# JS Grepper & JS Saver

A real-time pipeline to capture, beautify, and analyze JavaScript files proxied through Burp Suite directly inside VS Code.

This repository contains two cooperative tools:
1. **JS Saver (Burp Suite Extension)**: A Java-based Burp Suite extension that intercepts HTTP responses, extracts JavaScript, strips query parameters, auto-beautifies the content, saves files locally, and streams them in real-time via HTTP POST.
2. **JS Grepper (VS Code Extension)**: A lightweight VS Code companion extension that starts an internal HTTP listener to receive beautified scripts, maps them to a responsive sidebar tree view categorized by host, and automatically mounts them into the active VS Code workspace—enabling instant global searches, diffing, and analysis.

---

## How It Works (The Pipeline)

```mermaid
sequenceDiagram
    autonumber
    actor User as Browser / Burp Browser
    participant Burp as Burp Suite (JS Saver)
    participant Disk as Local Disk Storage
    participant VSCode as VS Code (JS Grepper)

    User->>Burp: 1. Requests Web Page (with JS scripts)
    Note over Burp: Strips If-None-Match & If-Modified-Since<br/>forces 200 OK (no 304s!)
    Burp->>User: 2. Proxied JS Response received
    Note over Burp: Intercepts body, beautifies content,<br/>and categorizes by parent navigation host
    Burp->>Disk: 3. Saves beautified <host>/<file>.js to disk
    Burp->>VSCode: 4. Streams payload via HTTP POST (JSON)
    Note over VSCode: Writes to session folder,<br/>adds directory to active workspace
    VSCode->>User: 5. Populates Sidebar tree-view in real-time
```

---

## Features

- **Real-time Pipeline**: JavaScript files arrive in VS Code within milliseconds of being loaded in your browser.
- **Automatic Beautification**: Bundles a fully local, high-performance JS beautifier to unpack minified or obfuscated sources instantly.
- **Smart Context Mapping**: Third-party scripts (e.g., CDNs, widgets, auth providers) are grouped under the host directory of the top-level page that loaded them, keeping your workspace neat and highly contextual.
- **No More 304s (Cache Busting)**: Automatically strips conditional request headers (`If-None-Match`, `If-Modified-Since`) from JS loads, ensuring the server always responds with a `200 OK` and a full body instead of an empty `304 Not Modified`.
- **Ultra-light VS Code Extension**: Pure JavaScript with zero heavy external dependencies. It automatically mounts directories inside your VS Code workspace to facilitate instantaneous searching and interaction.

---

## Build & Installation

### Prerequisites
* **Java Development Kit (JDK) 11** or newer (installed and on your system `PATH`)
* **VS Code** (or VSCodium)
* **Burp Suite** (Community or Professional)

### 1. Build the Extension Binaries
Run the included build script to compile the Java project, bundle external dependencies into a fat JAR, and package the VS Code extension into a `.vsix` file:

```bash
chmod +x build.sh
./build.sh
```

Upon successful completion, you will have:
- `JsSaver.jar` — The Burp Suite Extension fat JAR.
- `vscode-extension/js-grepper-0.2.0.vsix` — The VS Code Extension installer.

---

### 2. Install the Burp Suite Extension
1. Open Burp Suite.
2. Go to **Extensions** -> **Installed** -> **Add**.
3. Set Extension Type to **Java**.
4. Select the built `JsSaver.jar` file from the project root.
5. Click **Next** to load it. You will see a new **JS Saver** tab appear in the Burp header.

---

### 3. Install the VS Code Extension
Simply drag and drop the built `vscode-extension/js-grepper-0.2.0.vsix` file into your VS Code window to install it.

Alternatively, you can open the Extensions sidebar (`Cmd+Shift+X` / `Ctrl+Shift+X`), click the **three dots** (`...`) in the upper-right corner, select **Install from VSIX...**, and choose the `.vsix` file from your filesystem.

---

## Usage Guide

1. **Configure VS Code**:
   - In VS Code, verify the **JS Grepper** icon is visible in your Activity Bar.
   - By default, it starts a local HTTP listener on port `7777`. If you need to change this, go to Settings and search for `jsGrepper.port`.
   - Ensure you define your desired session folder via the `jsGrepper.sessionDir` setting (defaults to `~/js-grepper`).

2. **Configure Burp**:
   - Go to the **JS Saver** tab in Burp.
   - Choose your save directory (make sure it matches or points to the directory specified in VS Code).
   - Toggle the **Enable** button to start intercepting JavaScript.
   - Toggle the **Send to VS Code** button to enable real-time streaming, and ensure the port is matching (e.g., `7777`).

3. **Start Grepping**:
   - Configure your browser to proxy through Burp Suite (or use Burp's built-in browser).
   - Navigate to any web application.
   - Watch the captured, beautified scripts populate your VS Code workspace and sidebar tree-view in real-time!
   - Perform a global search (`Cmd+Shift+F`) inside VS Code to quickly grep for API routes, tokens, endpoints, or hidden logic across all collected files.

---

## License
This project is open-source and available under the **MIT License**.
