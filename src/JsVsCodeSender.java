import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sends a captured JS file to the VS Code JS Grepper extension via HTTP POST.
 * Uses Java 11's built-in HttpClient — no extra dependencies.
 * All sends are fire-and-forget (async) so they never block the proxy thread.
 */
public class JsVsCodeSender {

    private final JsSaverTab tab;
    private final HttpClient client;

    public JsVsCodeSender(JsSaverTab tab) {
        this.tab = tab;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    }

    /**
     * Asynchronously POST the JS file to VS Code.
     *
     * @param host      Host the JS was served from (e.g. "id.xsolla.com")
     * @param path      URL path stripped of query string (e.g. "/chunk-abc.js")
     * @param url       Full original URL (for tooltip in the tree view)
     * @param content   Beautified JS source
     */
    public void send(String host, String path, String url, String content) {
        if (!tab.isVsCodeEnabled()) return;

        int    port     = tab.getVsCodePort();
        String endpoint = "http://127.0.0.1:" + port + "/js";

        // Build JSON payload using org.json (already on classpath via js-beautify dep)
        String json;
        try {
            JSONObject obj = new JSONObject();
            obj.put("host",      host);
            obj.put("path",      path);
            obj.put("url",       url);
            obj.put("timestamp", System.currentTimeMillis());
            obj.put("content",   content);
            json = obj.toString();
        } catch (Exception e) {
            tab.log("[VSCODE] JSON build failed: " + e.getMessage());
            return;
        }

        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        } catch (Exception e) {
            tab.log("[VSCODE] Bad URI — is the port correct? port=" + port);
            return;
        }

        // Fire and forget; log on failure (first time) but don't spam
        client.sendAsync(req, HttpResponse.BodyHandlers.discarding())
              .thenAccept(resp -> {
                  if (resp.statusCode() != 200) {
                      tab.log("[VSCODE] Unexpected status " + resp.statusCode() + " from VS Code listener");
                  }
              })
              .exceptionally(ex -> {
                  tab.log("[VSCODE] Cannot reach VS Code listener on port " + port +
                          " — is the extension running? (" + ex.getMessage() + ")");
                  return null;
              });
    }
}
