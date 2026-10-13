package app.offenefahrten;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.net.URLEncoder;

/**
 * Zugfinder im App-Fenster. Die Anmeldung bleibt über den CookieManager erhalten,
 * der Pro-Zugang muss also nur einmal eingegeben werden. Die abgelesene
 * Verspätung wird unten eingetragen und an die Fahrt zurückgegeben.
 */
public class ZugfinderActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "fahrtId";
    public static final String EXTRA_TRAIN = "zug";
    public static final String EXTRA_DATE = "datum";
    public static final String EXTRA_MINUTES = "minuten";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        final String fahrtId = getIntent().getStringExtra(EXTRA_ID);
        String zug = getIntent().getStringExtra(EXTRA_TRAIN);
        String datum = getIntent().getStringExtra(EXTRA_DATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView hint = new TextView(this);
        hint.setPadding(32, 24, 32, 16);
        hint.setTextSize(13f);
        hint.setText(getString(R.string.zugfinder_hint,
                zug == null || zug.isEmpty() ? "-" : zug,
                datum == null || datum.isEmpty() ? "-" : datum));
        root.addView(hint);

        WebView web = new WebView(this);
        web.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient());
        root.addView(web);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(24, 16, 24, 24);

        final EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setHint(R.string.zugfinder_minutes_hint);
        field.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(field);

        Button ok = new Button(this);
        ok.setText(R.string.zugfinder_apply);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int min;
                try {
                    min = Integer.parseInt(field.getText().toString().trim());
                } catch (NumberFormatException e) {
                    Toast.makeText(ZugfinderActivity.this,
                            R.string.zugfinder_invalid, Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent out = new Intent();
                out.putExtra(EXTRA_ID, fahrtId);
                out.putExtra(EXTRA_MINUTES, min);
                setResult(Activity.RESULT_OK, out);
                finish();
            }
        });
        bar.addView(ok);
        root.addView(bar);

        setContentView(root);
        web.loadUrl(buildUrl(zug));
    }

    private static String buildUrl(String zug) {
        String base = "https://www.zugfinder.net/de/";
        if (zug == null || zug.trim().isEmpty()) return base;
        String first = zug.split(",")[0].replaceAll("\\s+", "");
        try {
            return base + "zugsuche?q=" + URLEncoder.encode(first, "UTF-8");
        } catch (Exception e) {
            return base;
        }
    }
}
