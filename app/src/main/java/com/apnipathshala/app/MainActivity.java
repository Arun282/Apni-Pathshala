package com.apnipathshala.app;

import android.app.Activity;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.util.ArrayDeque;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextToSpeech tts;
    private WebView webView;
    private boolean ttsReady = false;
    private final ArrayDeque<String[]> pending = new ArrayDeque<>();

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
            if (status == TextToSpeech.SUCCESS && tts != null) {
                ttsReady = true;
                tts.setSpeechRate(0.82f);
                tts.setPitch(1.05f);
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());

                while (!pending.isEmpty()) {
                    String[] item = pending.poll();
                    speakNow(item[0], item[1]);
                }
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void speakNow(String text, String language) {
        if (tts == null || !ttsReady || text == null || text.trim().isEmpty()) return;

        Locale requested = (language != null &&
                language.toLowerCase(Locale.ROOT).startsWith("hi"))
                ? new Locale("hi", "IN") : Locale.US;

        int available = tts.isLanguageAvailable(requested);
        Locale useLocale = requested;

        if (available == TextToSpeech.LANG_NOT_SUPPORTED ||
                available == TextToSpeech.LANG_MISSING_DATA) {
            useLocale = Locale.US;
        }

        int setResult = tts.setLanguage(useLocale);
        if (setResult == TextToSpeech.LANG_NOT_SUPPORTED ||
                setResult == TextToSpeech.LANG_MISSING_DATA) {
            tts.setLanguage(Locale.US);
        }

        Bundle params = new Bundle();
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params,
                "apni_pathshala_" + System.nanoTime());
    }

    private class TTSBridge {
        @JavascriptInterface
        public void speak(String text, String language) {
            if (text == null || text.trim().isEmpty()) return;

            if (!ttsReady) {
                pending.clear();
                pending.add(new String[]{text, language});
                return;
            }

            runOnUiThread(() -> speakNow(text, language));
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidTTS");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}