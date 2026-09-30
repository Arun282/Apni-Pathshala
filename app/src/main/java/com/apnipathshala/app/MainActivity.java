package com.apnipathshala.app;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextToSpeech tts;
    private WebView webView;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        webView.addJavascriptInterface(new TTSBridge(), "AndroidTTS");
        setContentView(webView);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setSpeechRate(0.82f);
                tts.setPitch(1.05f);
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private class TTSBridge {
        @JavascriptInterface
        public void speak(String text, String language) {
            if (tts == null) return;
            Locale locale = language != null && language.startsWith("hi")
                    ? new Locale("hi", "IN") : Locale.US;
            int result = tts.setLanguage(locale);
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                locale = Locale.US;
                tts.setLanguage(locale);
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "apni_pathshala");
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}