import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class JsSaverExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("JS Saver");

        JsSaverTab tab = new JsSaverTab();
        api.userInterface().registerSuiteTab("JS Saver", tab);

        // Strip If-None-Match / If-Modified-Since from JS requests so the browser
        // never gets a 304 (body-less) response — we need the full response body.
        api.proxy().registerRequestHandler(new JsRequestCacheBuster(tab));

        // Intercept responses, detect JS, beautify and save to disk.
        api.proxy().registerResponseHandler(new JsProxyHandler(api, tab));

        api.logging().logToOutput("JS Saver loaded — cache buster + response handler registered.");
    }
}
