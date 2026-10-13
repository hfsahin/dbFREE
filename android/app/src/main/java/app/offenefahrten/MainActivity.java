package app.offenefahrten;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hülle um die Weboberfläche. Die eigentliche App liegt unter assets/www.
 * Alle Daten bleiben in der IndexedDB der WebView, also auf dem Gerät.
 *
 * Die Hülle übernimmt drei Dinge, die der Oberfläche allein nicht möglich sind:
 * Abrufe bei bahn.de ohne Herkunftssperre, das Hochdrehen der Displayhelligkeit
 * für den Code am Zugang, und den Empfang geteilter Links aus der DB-App.
 */
public class MainActivity extends AppCompatActivity {

    private static final String START_URL = "file:///android_asset/www/index.html";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private ActivityResultLauncher<Intent> filePicker;
    private ActivityResultLauncher<Intent> zugfinder;
    private final ExecutorService netz = Executors.newFixedThreadPool(2);
    private boolean seiteFertig = false;
    private String wartenderLink = null;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        filePicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onFilesPicked);
        zugfinder = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onZugfinderResult);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setTextZoom(100);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                seiteFertig = true;
                if (wartenderLink != null) {
                    String t = wartenderLink;
                    wartenderLink = null;
                    ruf("window.__linkEmpfangen(" + jsString(t) + ");");
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    filePicker.launch(params.createIntent());
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (state != null) {
            web.restoreState(state);
        } else {
            web.loadUrl(START_URL);
        }
        linkAusIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        linkAusIntent(intent);
    }

    /** Aus der DB-App geteilte Verbindung landet direkt im Linkfenster. */
    private void linkAusIntent(Intent intent) {
        if (intent == null) return;
        String text = null;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            text = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            text = intent.getData().toString();
        }
        if (text == null || text.trim().isEmpty()) return;
        if (seiteFertig) {
            ruf("window.__linkEmpfangen(" + jsString(text) + ");");
        } else {
            wartenderLink = text;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        netz.shutdownNow();
        super.onDestroy();
    }

    private void ruf(final String js) {
        runOnUiThread(() -> {
            try {
                web.evaluateJavascript(js, null);
            } catch (Exception ignored) {
            }
        });
    }

    private void onFilesPicked(ActivityResult result) {
        if (fileCallback == null) return;
        Uri[] uris = null;
        if (result.getResultCode() == Activity.RESULT_OK) {
            uris = WebChromeClient.FileChooserParams.parseResult(
                    result.getResultCode(), result.getData());
        }
        fileCallback.onReceiveValue(uris);
        fileCallback = null;
    }

    private void onZugfinderResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        String id = result.getData().getStringExtra(ZugfinderActivity.EXTRA_ID);
        int min = result.getData().getIntExtra(ZugfinderActivity.EXTRA_MINUTES, -1);
        if (id == null || min < 0) return;
        ruf("window.__zugfinderResult(" + jsString(id) + "," + min + ");");
    }

    /** Als JavaScript-Zeichenkette einbetten, ohne Ausbruchmöglichkeit. */
    private static String jsString(String raw) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n");  break;
                case '\r': b.append("\\r");  break;
                case '\t': b.append("\\t");  break;
                case ' ': b.append("\\u2028"); break;
                case ' ': b.append("\\u2029"); break;
                default:
                    if (c < 0x20 || c > 0x7e) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }

    /** Wird aus der Weboberfläche heraus aufgerufen. */
    public class Bridge {

        @JavascriptInterface
        public void openZugfinder(String fahrtId, String zugnummern, String reisedatum) {
            Intent i = new Intent(MainActivity.this, ZugfinderActivity.class);
            i.putExtra(ZugfinderActivity.EXTRA_ID, fahrtId);
            i.putExtra(ZugfinderActivity.EXTRA_TRAIN, zugnummern);
            i.putExtra(ZugfinderActivity.EXTRA_DATE, reisedatum);
            runOnUiThread(() -> zugfinder.launch(i));
        }

        /** Displayhelligkeit für den Code am Zugang, danach wieder Systemwert. */
        @JavascriptInterface
        public void setBrightness(final boolean voll) {
            runOnUiThread(() -> {
                WindowManager.LayoutParams p = getWindow().getAttributes();
                p.screenBrightness = voll ? 1.0f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                getWindow().setAttributes(p);
            });
        }

        /**
         * HTTP-Abruf ohne Herkunftssperre. Die Antwort geht über
         * window.__netResult(id, status, text) zurück an die Oberfläche.
         */
        @JavascriptInterface
        public void httpRequest(final String id, final String methode,
                                final String url, final String koerper) {
            netz.execute(() -> {
                int status = 0;
                String text = "";
                HttpURLConnection c = null;
                try {
                    URL u = new URL(url);
                    if (!"https".equalsIgnoreCase(u.getProtocol())) {
                        throw new IllegalArgumentException("Nur https erlaubt.");
                    }
                    c = (HttpURLConnection) u.openConnection();
                    c.setRequestMethod(methode == null || methode.isEmpty() ? "GET" : methode);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("Accept", "application/json");
                    c.setRequestProperty("Accept-Language", "de");
                    c.setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                                    + "Chrome/126.0.0.0 Mobile Safari/537.36");
                    String host = u.getHost();
                    if (host != null && host.endsWith("bahn.de")) {
                        c.setRequestProperty("Origin", "https://" + host);
                        c.setRequestProperty("Referer", "https://" + host + "/buchung/fahrplan/suche");
                    }
                    if (koerper != null && !koerper.isEmpty()) {
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                        byte[] daten = koerper.getBytes(StandardCharsets.UTF_8);
                        c.setFixedLengthStreamingMode(daten.length);
                        OutputStream os = c.getOutputStream();
                        os.write(daten);
                        os.flush();
                        os.close();
                    }
                    status = c.getResponseCode();
                    InputStream in = (status >= 200 && status < 400)
                            ? c.getInputStream() : c.getErrorStream();
                    if (in != null) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] puffer = new byte[8192];
                        int n;
                        int gesamt = 0;
                        while ((n = in.read(puffer)) > 0) {
                            gesamt += n;
                            if (gesamt > 4 * 1024 * 1024) break; // Schutz gegen Übergröße
                            bo.write(puffer, 0, n);
                        }
                        in.close();
                        text = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                    }
                } catch (Exception e) {
                    if (status == 0) status = -1;
                    text = String.valueOf(e.getMessage());
                } finally {
                    if (c != null) c.disconnect();
                }
                ruf("window.__netResult(" + jsString(id) + "," + status + "," + jsString(text) + ");");
            });
        }
    }
}
