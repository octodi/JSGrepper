import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.http.ProxyResponseHandler;
import burp.api.montoya.proxy.http.InterceptedResponse;
import burp.api.montoya.proxy.http.ProxyResponseReceivedAction;
import burp.api.montoya.proxy.http.ProxyResponseToBeSentAction;
import burp.api.montoya.http.message.HttpHeader;

import io.beautifier.javascript.JavaScriptBeautifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Hooks into Burp's Proxy HTTP history stream.
 * Detects JavaScript responses, beautifies them, and writes them to disk.
 */
public class JsProxyHandler implements ProxyResponseHandler {

    private final MontoyaApi api;
    private final JsSaverTab tab;
    private final JsVsCodeSender vsCodeSender;

    /** Dedup: host + path combinations already persisted in this session. */
    private final Set<String> savedKeys = Collections.synchronizedSet(new HashSet<>());

    /**
     * The host of the top-level page the user most recently navigated to.
     * Detected via Sec-Fetch-Mode: navigate on any proxied request.
     * All JS files captured while this is set are filed under this host,
     * so third-party scripts (CDNs, iframes, reCAPTCHA, etc.) land in the
     * same folder as the page that triggered them.
     */
    private volatile String currentNavHost = null;

    public JsProxyHandler(MontoyaApi api, JsSaverTab tab) {
        this.api          = api;
        this.tab          = tab;
        this.vsCodeSender = new JsVsCodeSender(tab);
    }

    // ── ProxyResponseHandler ──────────────────────────────────────────────────

    @Override
    public ProxyResponseReceivedAction handleResponseReceived(InterceptedResponse interceptedResponse) {
        try {
            // Track the current page context from every proxied response
            // (must happen before the early-return checks)
            updateNavContext(interceptedResponse);

            if (!tab.isEnabled()) return ProxyResponseReceivedAction.continueWith(interceptedResponse);
            if (!isJavaScript(interceptedResponse)) return ProxyResponseReceivedAction.continueWith(interceptedResponse);

            String host = interceptedResponse.initiatingRequest().httpService().host();
            String rawPath = interceptedResponse.initiatingRequest().path();

            // Strip query string for dedup key and filename
            String path = stripQuery(rawPath);
            String dedupKey = host + "|" + path;

            if (savedKeys.contains(dedupKey)) return ProxyResponseReceivedAction.continueWith(interceptedResponse);
            savedKeys.add(dedupKey);

            String saveDir = tab.getSaveDir();
            if (saveDir == null || saveDir.isBlank()) {
                tab.log("[WARN] No save directory set – skipping.");
                return ProxyResponseReceivedAction.continueWith(interceptedResponse);
            }

            // Primary: use the top-level page the user navigated to.
            // Fallback: Referer > Origin > serving host (handles direct script loads
            // before any navigation is detected).
            String loadedByHost = (currentNavHost != null)
                ? currentNavHost
                : determineOriginatingHost(interceptedResponse);

            // Build save path: <saveDir>/<loadedByHost>/<filename>.js
            String filename = toSafeFilename(path);
            Path dir  = Paths.get(saveDir, sanitizeHost(loadedByHost));
            Files.createDirectories(dir);
            Path file = dir.resolve(filename);

            // Avoid clobbering a previously saved file with the same sanitised name
            file = uniquePath(file);

            // Get body as string, beautify, then prepend a source comment
            String protocol   = interceptedResponse.initiatingRequest().httpService().secure() ? "https" : "http";
            String sourceUrl  = protocol + "://" + host + rawPath;
            String capturedAt = java.time.Instant.now().toString();

            String body      = bodyAsString(interceptedResponse);
            String beautified = new JavaScriptBeautifier(body).beautify();

            String header = "// ==================================================\n" +
                            "// Source  : " + sourceUrl + "\n" +
                            "// Page    : " + loadedByHost + "\n" +
                            "// Captured: " + capturedAt + "\n" +
                            "// ==================================================\n\n";

            String output = header + beautified;

            Files.writeString(file, output, StandardCharsets.UTF_8);
            tab.incrementSaved();
            tab.log("[SAVED] " + host + rawPath + "\n        → " + file);

            // Stream to VS Code if enabled (fire-and-forget, won't block proxy)
            vsCodeSender.send(loadedByHost, path, protocol + "://" + host + rawPath, output);

        } catch (Exception e) {
            tab.log("[ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        return ProxyResponseReceivedAction.continueWith(interceptedResponse);
    }

    @Override
    public ProxyResponseToBeSentAction handleResponseToBeSent(InterceptedResponse interceptedResponse) {
        return ProxyResponseToBeSentAction.continueWith(interceptedResponse);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private boolean isJavaScript(InterceptedResponse resp) {
        for (HttpHeader h : resp.headers()) {
            if ("Content-Type".equalsIgnoreCase(h.name()) &&
                h.value().toLowerCase().contains("javascript")) {
                return true;
            }
        }
        // Fallback: URL ends in .js (ignore query string)
        String path = stripQuery(resp.initiatingRequest().path()).toLowerCase();
        return path.endsWith(".js");
    }

    private static String stripQuery(String path) {
        int q = path.indexOf('?');
        return q >= 0 ? path.substring(0, q) : path;
    }

    private static String toSafeFilename(String path) {
        // Extract last path segment
        int slash = path.lastIndexOf('/');
        String name = (slash >= 0 && slash < path.length() - 1)
            ? path.substring(slash + 1)
            : "index";
        if (name.isBlank()) name = "index";
        // Sanitise characters
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        // Ensure .js suffix
        if (!name.endsWith(".js")) name = name + ".js";
        return name;
    }

    private static String sanitizeHost(String host) {
        return host.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Detects top-level page navigations via the Sec-Fetch-Mode: navigate header
     * and updates currentNavHost accordingly.
     *
     * Browsers send Sec-Fetch-Mode: navigate exclusively for real user-initiated
     * navigations (address bar, link click, history back/forward). It is NOT sent
     * by subresource loads (scripts, iframes, fetch, XHR), so it reliably
     * pinpoints the page the user actually browsed to.
     */
    private void updateNavContext(InterceptedResponse resp) {
        burp.api.montoya.http.message.requests.HttpRequest req = resp.initiatingRequest();
        if (req.hasHeader("Sec-Fetch-Mode")
                && "navigate".equalsIgnoreCase(req.headerValue("Sec-Fetch-Mode"))) {
            String host = req.httpService().host();
            if (host != null && !host.isBlank() && !host.equals(currentNavHost)) {
                currentNavHost = host;
                tab.log("[NAV] Active page context → " + currentNavHost);
            }
        }
    }

    /**
     * Fallback: determines originating host from Origin or Referer headers.
     * Used when no navigation context has been established yet.
     */
    private static String determineOriginatingHost(InterceptedResponse resp) {
        burp.api.montoya.http.message.requests.HttpRequest req = resp.initiatingRequest();
        
        // Try Origin header first
        if (req.hasHeader("Origin")) {
            String origin = req.headerValue("Origin");
            if (origin != null && !origin.isBlank()) return extractHostFromUrl(origin);
        }
        
        // Fallback to Referer
        if (req.hasHeader("Referer")) {
            String referer = req.headerValue("Referer");
            if (referer != null && !referer.isBlank()) return extractHostFromUrl(referer);
        }
        
        // Fallback: the site itself
        return req.httpService().host();
    }

    private static String extractHostFromUrl(String url) {
        // e.g. "https://mail.google.com/mail/u/0/" -> "mail.google.com"
        try {
            int start = url.indexOf("://");
            if (start != -1) url = url.substring(start + 3);
            int end = url.indexOf('/');
            if (end != -1) url = url.substring(0, end);
            int colon = url.indexOf(':');
            if (colon != -1) url = url.substring(0, colon); // strip port
            return url;
        } catch (Exception e) {
            return url; // fallback
        }
    }

    /** If <path> already exists, append _1, _2, … until a free name is found. */
    private static Path uniquePath(Path p) {
        if (!Files.exists(p)) return p;
        String name = p.getFileName().toString();
        String base = name.endsWith(".js") ? name.substring(0, name.length() - 3) : name;
        Path parent = p.getParent();
        int n = 1;
        Path candidate;
        do {
            candidate = parent.resolve(base + "_" + n + ".js");
            n++;
        } while (Files.exists(candidate));
        return candidate;
    }

    private static String bodyAsString(InterceptedResponse resp) {
        try {
            return resp.bodyToString();
        } catch (Exception e) {
            // Fallback using raw bytes
            return new String(resp.body().getBytes(), StandardCharsets.UTF_8);
        }
    }
}
