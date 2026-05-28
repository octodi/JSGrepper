// @ts-check
'use strict';

const vscode = require('vscode');
const http   = require('http');
const fs     = require('fs');
const path   = require('path');
const os     = require('os');

// ── In-memory index (metadata only – content lives on disk) ──────────────────
/** @type {Map<string, {host:string, relPath:string, url:string, timestamp:number}>} */
const capturedFiles = new Map();   // absolute file path → metadata
/** @type {Map<string, Set<string>>} */
const hostFiles     = new Map();   // host → Set of absolute file paths

let httpServer    = /** @type {http.Server|null} */ (null);
let statusBarItem = /** @type {vscode.StatusBarItem|null} */ (null);
let treeProvider  = /** @type {JSGrepperTreeProvider|null} */ (null);
let serverPort    = 7777;

// ── Tree data provider ────────────────────────────────────────────────────────
class JSGrepperTreeProvider {
    constructor() {
        this._emitter = new vscode.EventEmitter();
        /** @type {vscode.Event<undefined>} */
        this.onDidChangeTreeData = this._emitter.event;
    }

    refresh() { this._emitter.fire(undefined); }

    /** @param {vscode.TreeItem|undefined} element */
    getTreeItem(element) { return element; }

    /** @param {vscode.TreeItem|undefined} element */
    getChildren(element) {
        if (!element) {
            // Root – host folders sorted by most-recently-captured
            const hosts = [...hostFiles.keys()].sort((a, b) => {
                const tA = Math.max(...[...hostFiles.get(a)].map(p => (capturedFiles.get(p) || {}).timestamp || 0));
                const tB = Math.max(...[...hostFiles.get(b)].map(p => (capturedFiles.get(p) || {}).timestamp || 0));
                return tB - tA;
            });

            return hosts.map(host => {
                const count = hostFiles.get(host).size;
                const item  = new vscode.TreeItem(host, vscode.TreeItemCollapsibleState.Expanded);
                item.id           = 'host:' + host;
                item.iconPath     = new vscode.ThemeIcon('globe');
                item.description  = `${count} file${count !== 1 ? 's' : ''}`;
                item.contextValue = 'host';
                return item;
            });
        }

        if (element.contextValue === 'host') {
            const host   = /** @type {string} */ (element.label);
            const paths  = [...(hostFiles.get(host) || new Set())].sort((a, b) => {
                return ((capturedFiles.get(b) || {}).timestamp || 0) - ((capturedFiles.get(a) || {}).timestamp || 0);
            });

            return paths.map(filePath => {
                const meta     = capturedFiles.get(filePath) || {};
                const filename = path.basename(filePath);
                const stat     = safeStatSync(filePath);
                const kb       = stat ? Math.max(1, Math.round(stat.size / 1024)) : '?';
                const time     = meta.timestamp ? new Date(meta.timestamp).toLocaleTimeString() : '';

                const item = new vscode.TreeItem(filename, vscode.TreeItemCollapsibleState.None);
                item.id           = filePath;
                item.iconPath     = new vscode.ThemeIcon('file-code');
                item.description  = `${kb}KB · ${time}`;
                item.tooltip      = new vscode.MarkdownString(
                    `**${filename}**\n\nURL: \`${meta.url || filePath}\`\n\nCaptured: ${time}\n\nPath: \`${filePath}\``
                );
                item.command = {
                    command:   'jsGrepper.openFile',
                    title:     'Open',
                    arguments: [vscode.Uri.file(filePath)]
                };
                item.contextValue = 'jsfile';
                item.resourceUri  = vscode.Uri.file(filePath);  // enables file-context decorations
                return item;
            });
        }

        return [];
    }
}

// ── Extension lifecycle ───────────────────────────────────────────────────────
/** @param {vscode.ExtensionContext} context */
function activate(context) {
    // Tree view
    treeProvider = new JSGrepperTreeProvider();
    const treeView = vscode.window.createTreeView('jsGrepper.fileTree', {
        treeDataProvider: treeProvider,
        showCollapseAll: true
    });
    context.subscriptions.push(treeView);

    // Status bar
    statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
    statusBarItem.command = 'jsGrepper.toggleServer';
    updateStatusBar(false);
    statusBarItem.show();
    context.subscriptions.push(statusBarItem);

    // Commands
    context.subscriptions.push(
        vscode.commands.registerCommand('jsGrepper.startServer',  startServer),
        vscode.commands.registerCommand('jsGrepper.stopServer',   stopServer),
        vscode.commands.registerCommand('jsGrepper.toggleServer', toggleServer),
        vscode.commands.registerCommand('jsGrepper.clearFiles',   clearFiles),
        vscode.commands.registerCommand('jsGrepper.openFile',     openFile),
        vscode.commands.registerCommand('jsGrepper.openFolder',   openSessionFolder)
    );

    // Ensure session folder is in workspace on activation (if it exists from a previous session)
    const sessionDir = getSessionDir();
    if (fs.existsSync(sessionDir)) {
        ensureWorkspaceFolder(sessionDir);
    }

    startServer();
}

function deactivate() {
    if (httpServer) httpServer.close();
}

// ── Server ────────────────────────────────────────────────────────────────────
function startServer() {
    if (httpServer) return;

    const cfg  = vscode.workspace.getConfiguration('jsGrepper');
    serverPort = cfg.get('port', 7777);

    httpServer = http.createServer((req, res) => {
        if (req.method === 'POST' && req.url === '/js') {
            let body = '';
            req.on('data', chunk => { body += chunk.toString(); });
            req.on('end', () => {
                try {
                    const data = JSON.parse(body);
                    addFile(data);
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ ok: true, total: capturedFiles.size }));
                } catch (e) {
                    res.writeHead(400);
                    res.end('Bad JSON');
                }
            });
        } else if (req.method === 'GET' && req.url === '/ping') {
            res.writeHead(200);
            res.end('pong');
        } else {
            res.writeHead(404);
            res.end();
        }
    });

    httpServer.listen(serverPort, '127.0.0.1', () => {
        updateStatusBar(true);
        vscode.window.setStatusBarMessage(`⚡ JS Grepper: Listening on port ${serverPort}`, 4000);
    });

    httpServer.on('error', (/** @type {NodeJS.ErrnoException} */ err) => {
        const msg = err.code === 'EADDRINUSE'
            ? `Port ${serverPort} already in use. Change jsGrepper.port in Settings.`
            : err.message;
        vscode.window.showErrorMessage(`JS Grepper: ${msg}`);
        httpServer = null;
        updateStatusBar(false);
    });
}

function stopServer() {
    if (!httpServer) return;
    httpServer.close();
    httpServer = null;
    updateStatusBar(false);
    vscode.window.setStatusBarMessage('JS Grepper: Listener stopped', 3000);
}

function toggleServer() {
    if (httpServer) stopServer();
    else startServer();
}

// ── File handling ─────────────────────────────────────────────────────────────
function addFile(/** @type {any} */ data) {
    const { host, path: filePath, url, content, timestamp } = data;
    if (!host || !filePath || !content) return;

    // Normalise path: strip query/hash, ensure .js suffix
    let safePath = (filePath || '/script.js').replace(/[?#].*$/, '');
    if (!safePath.toLowerCase().endsWith('.js')) safePath += '.js';

    // Build the real file path on disk: <sessionDir>/<host>/<filename>
    const sessionDir = getSessionDir();
    const hostDir    = path.join(sessionDir, sanitizeName(host));
    const filename   = sanitizeName(path.basename(safePath) || 'script.js');
    const fullPath   = path.join(hostDir, filename);

    // Write to disk
    try {
        fs.mkdirSync(hostDir, { recursive: true });
        fs.writeFileSync(fullPath, content, 'utf8');
    } catch (e) {
        vscode.window.showErrorMessage(`JS Grepper: Cannot write file — ${e.message}`);
        return;
    }

    // Update in-memory index
    capturedFiles.set(fullPath, { host, relPath: safePath, url: url || '', timestamp: timestamp || Date.now() });
    if (!hostFiles.has(host)) hostFiles.set(host, new Set());
    hostFiles.get(host).add(fullPath);

    // Make the session folder a workspace folder so search / AI agents can see it
    ensureWorkspaceFolder(sessionDir);

    // Refresh tree
    treeProvider && treeProvider.refresh();
    updateStatusBar(!!httpServer);

    // Auto-open if configured
    const cfg = vscode.workspace.getConfiguration('jsGrepper');
    if (cfg.get('autoOpen', false)) {
        openFile(vscode.Uri.file(fullPath));
    }

    // Subtle status bar flash
    vscode.window.setStatusBarMessage(`⚡ ${filename} from ${host}`, 5000);
}

/** @param {vscode.Uri} uri */
function openFile(uri) {
    vscode.workspace.openTextDocument(uri)
        .then(doc => vscode.window.showTextDocument(doc, { preview: true, preserveFocus: false }))
        .then(undefined, err => {
            vscode.window.showErrorMessage(`JS Grepper: Cannot open file — ${err.message}`);
        });
}

function openSessionFolder() {
    const dir = getSessionDir();
    if (fs.existsSync(dir)) {
        vscode.env.openExternal(vscode.Uri.file(dir));
    } else {
        vscode.window.showInformationMessage('JS Grepper: No session folder yet — capture some JS first.');
    }
}

function clearFiles() {
    capturedFiles.clear();
    hostFiles.clear();
    treeProvider && treeProvider.refresh();
    updateStatusBar(!!httpServer);
    vscode.window.setStatusBarMessage('JS Grepper: Cleared file index (files remain on disk)', 4000);
}

// ── Workspace integration ─────────────────────────────────────────────────────
function ensureWorkspaceFolder(/** @type {string} */ dir) {
    const folders = vscode.workspace.workspaceFolders || [];
    const already = folders.some(f => f.uri.fsPath === dir);
    if (!already) {
        vscode.workspace.updateWorkspaceFolders(folders.length, 0, {
            uri:  vscode.Uri.file(dir),
            name: '⚡ JS Grepper'
        });
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function getSessionDir() {
    const cfg  = vscode.workspace.getConfiguration('jsGrepper');
    const base = /** @type {string} */ (cfg.get('sessionDir', '~/js-grepper'));
    // Node.js does NOT expand ~ automatically — do it manually
    return base.replace(/^~($|\/|\\)/, os.homedir() + '$1');
}

/** @param {string} name */
function sanitizeName(name) {
    return name.replace(/[^a-zA-Z0-9._\-]/g, '_');
}

/** @param {string} p */
function safeStatSync(p) {
    try { return fs.statSync(p); } catch { return null; }
}

// ── Status bar ────────────────────────────────────────────────────────────────
function updateStatusBar(/** @type {boolean} */ running) {
    if (!statusBarItem) return;
    const count = capturedFiles.size;
    if (running) {
        statusBarItem.text            = `⚡ JS Grepper $(circle-filled) ${count}`;
        statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
        statusBarItem.tooltip         = `Listening on :${serverPort} · ${count} file${count !== 1 ? 's' : ''} captured. Click to stop.`;
    } else {
        statusBarItem.text            = `⚡ JS Grepper $(circle-outline) offline`;
        statusBarItem.backgroundColor = undefined;
        statusBarItem.tooltip         = 'JS Grepper listener stopped. Click to start.';
    }
}

module.exports = { activate, deactivate };
