package com.apnipathshala.app;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioManager;
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
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());

                while (!pending.isEmpty()) {
                    String[] item = pending.poll();
                    speakNow(item[0], item[1], TextToSpeech.QUEUE_ADD);
                }
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private Locale pickLocale(String language) {
        boolean hindi = language != null && language.toLowerCase(Locale.ROOT).startsWith("hi");
        Locale[] choices = hindi
                ? new Locale[]{new Locale("hi", "IN"), new Locale("hi"), Locale.getDefault()}
                : new Locale[]{new Locale("en", "IN"), Locale.US, Locale.getDefault()};

        for (Locale candidate : choices) {
            int available = tts.isLanguageAvailable(candidate);
            if (available == TextToSpeech.LANG_AVAILABLE ||
                    available == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    available == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
                return candidate;
            }
        }
        return Locale.getDefault();
    }

    private void speakNow(String text, String language, int queueMode) {
        if (tts == null || !ttsReady || text == null || text.trim().isEmpty()) return;

        Locale useLocale = pickLocale(language);
        int result = tts.setLanguage(useLocale);

        if (result == TextToSpeech.LANG_NOT_SUPPORTED ||
                result == TextToSpeech.LANG_MISSING_DATA) {
            result = tts.setLanguage(Locale.US);
        }
        if (result == TextToSpeech.LANG_NOT_SUPPORTED ||
                result == TextToSpeech.LANG_MISSING_DATA) return;

        Bundle params = new Bundle();
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        tts.speak(text, queueMode, params, "apni_pathshala_" + System.nanoTime());
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

            runOnUiThread(() -> speakNow(text, language, TextToSpeech.QUEUE_FLUSH));
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
