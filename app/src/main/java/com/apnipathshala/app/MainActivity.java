package com.apnipathshala.app;

import android.app.Activity;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingText = null;
    private String pendingLang = "hi-IN";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        tts = new TextToSpeech(this, this);

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new TtsBridge(), "AndroidTTS");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false;
            return;
        }

        ttsReady = true;
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());

        // Prefer an installed network voice for natural speech when available.
        preferNetworkVoice("hi-IN");
        preferNetworkVoice("en-IN");

        if (pendingText != null) {
            String text = pendingText;
            String lang = pendingLang;
            pendingText = null;
            speakNow(text, lang);
        }
    }

    private void preferNetworkVoice(String langTag) {
        try {
            Locale locale = Locale.forLanguageTag(langTag);
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return;

            Voice best = null;
            for (Voice v : voices) {
                if (!v.getLocale().getLanguage().equals(locale.getLanguage())) continue;
                if (v.getLocale().getCountry().equalsIgnoreCase(locale.getCountry())
                        && v.isNetworkConnectionRequired()) {
                    best = v;
                    break;
                }
                if (best == null && v.isNetworkConnectionRequired()) {
                    best = v;
                }
            }
            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) {}
    }

    private void speakNow(String text, String langTag) {
        if (!ttsReady || tts == null) return;

        String lang = (langTag == null || langTag.trim().isEmpty()) ? "hi-IN" : langTag;
        Locale locale = Locale.forLanguageTag(lang);

        try {
            int result = tts.setLanguage(locale);
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                locale = lang.toLowerCase(Locale.US).startsWith("en")
                        ? Locale.US : new Locale("hi", "IN");
                tts.setLanguage(locale);
            }

            // Re-select a matching network voice after changing language.
            preferNetworkVoice(lang);

            tts.stop();
            tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    new HashMap<String, String>(),
                    "apni_pathshala_" + System.currentTimeMillis()
            );
        } catch (Exception ignored) {}
    }

    private class TtsBridge {
        @JavascriptInterface
        public void speak(String text, String lang) {
            if (text == null || text.trim().isEmpty()) return;
            runOnUiThread(() -> {
                if (!ttsReady) {
                    pendingText = text;
                    pendingLang = lang == null ? "hi-IN" : lang;
                } else {
                    speakNow(text, lang);
                }
            });
        }

        @JavascriptInterface
        public void speakSequence(String first, String firstLang, String second, String secondLang) {
            if (first == null || first.trim().isEmpty()) return;
            runOnUiThread(() -> {
                if (!ttsReady) {
                    pendingText = first + (second == null || second.trim().isEmpty() ? "" : " " + second);
                    pendingLang = firstLang == null ? "hi-IN" : firstLang;
                    return;
                }

                tts.stop();
                setVoiceFor(firstLang);
                tts.speak(first, TextToSpeech.QUEUE_FLUSH, new HashMap<String, String>(),
                        "apni_first_" + System.currentTimeMillis());

                if (second != null && !second.trim().isEmpty()) {
                    setVoiceFor(secondLang);
                    tts.speak(second, TextToSpeech.QUEUE_ADD, new HashMap<String, String>(),
                            "apni_second_" + System.currentTimeMillis());
                }
            });
        }
    }

    private void setVoiceFor(String langTag) {
        try {
            Locale locale = Locale.forLanguageTag(
                    (langTag == null || langTag.trim().isEmpty()) ? "hi-IN" : langTag);
            tts.setLanguage(locale);
            preferNetworkVoice(locale.toLanguageTag());
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
            try { tts.shutdown(); } catch (Exception ignored) {}
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
