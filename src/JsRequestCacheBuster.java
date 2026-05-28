import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;
import burp.api.montoya.http.message.requests.HttpRequest;

/**
 * Strips browser cache-validation headers from JavaScript requests before
 * they leave Burp, so the server always responds with a full 200 + body
 * instead of a 304 Not Modified (which has no body and would be invisible
 * to the response handler).
 *
 * Headers removed / overridden:
 *   If-None-Match        – ETag validator → server would send 304
 *   If-Modified-Since    – date validator → server would send 304
 *   Cache-Control        – replaced with "no-cache" (revalidate always)
 *   Pragma               – replaced with "no-cache" (HTTP/1.0 compat)
 *
 * Only applied to requests that are clearly JS loads
 * (Sec-Fetch-Dest: script, or path ending in .js).
 */
public class JsRequestCacheBuster implements ProxyRequestHandler {

    private final JsSaverTab tab;

    public JsRequestCacheBuster(JsSaverTab tab) {
        this.tab = tab;
    }

    // ── ProxyRequestHandler ───────────────────────────────────────────────────

    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
        // We only need to act just before the request is forwarded to the server
        return ProxyRequestReceivedAction.continueWith(interceptedRequest);
    }

    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest) {
        if (!tab.isEnabled() || !isJsRequest(interceptedRequest)) {
            return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
        }

        // Remove conditional validators so the server cannot respond with 304
        HttpRequest modified = interceptedRequest
            .withRemovedHeader("If-None-Match")
            .withRemovedHeader("If-Modified-Since")
            .withRemovedHeader("Cache-Control")
            .withAddedHeader("Cache-Control", "no-cache")
            .withRemovedHeader("Pragma")
            .withAddedHeader("Pragma", "no-cache");

        return ProxyRequestToBeSentAction.continueWith(modified);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true for requests that are loading a JavaScript resource.
     * Sec-Fetch-Dest: script is set exclusively by the browser for <script>
     * tag loads and dynamic import(). The .js path check handles edge cases
     * where that header is absent.
     */
    private static boolean isJsRequest(InterceptedRequest req) {
        if (req.hasHeader("Sec-Fetch-Dest")
                && "script".equalsIgnoreCase(req.headerValue("Sec-Fetch-Dest"))) {
            return true;
        }
        String path = req.path();
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        return path.toLowerCase().endsWith(".js");
    }
}
