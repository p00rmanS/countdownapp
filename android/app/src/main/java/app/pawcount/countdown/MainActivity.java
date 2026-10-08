package app.pawcount.countdown;

import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.BridgeActivity;

/**
 * Android 15 forces apps to draw edge-to-edge, and a WebView doesn't report the system bars
 * through env(safe-area-inset-*) the way iOS does. So we measure the bars here and hand them to the
 * page (window.PawInsets.insets(), read by js/native.js), which sets the --safe-* CSS variables.
 * The web content then draws under the bars in its own theme colour instead of a hard-coded native one.
 */
public class MainActivity extends BridgeActivity {
    // top,right,bottom,left in CSS pixels
    private volatile String insetsCss = "0,0,0,0";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getBridge().getWebView().addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String insets() {
                return insetsCss;
            }
        }, "PawInsets");

        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            float d = getResources().getDisplayMetrics().density;
            insetsCss = Math.round(bars.top / d) + "," + Math.round(bars.right / d) + ","
                    + Math.round(bars.bottom / d) + "," + Math.round(bars.left / d);
            // Don't pad the WebView; the page applies the insets itself.
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
