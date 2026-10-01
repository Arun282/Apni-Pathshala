package com.apnipathshala.app;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView webView;
    private MediaPlayer mediaPlayer;
    private final List<String> queue = new ArrayList<>();
    private int queueIndex = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new OnlineAudioBridge(), "OnlineAudio");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private String ttsUrl(String text, String lang) throws Exception {
        String q = URLEncoder.encode(text, "UTF-8");
        String tl = (lang == null || lang.trim().isEmpty()) ? "hi-IN" : lang;
        return "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl="
                + URLEncoder.encode(tl, "UTF-8") + "&q=" + q;
    }

    private void playOnlineSequence(String[] texts, String[] langs) {
        releasePlayer();
        queue.clear();
        queueIndex = 0;

        if (texts == null || texts.length == 0) return;
        for (int i = 0; i < texts.length; i++) {
            try {
                String lang = (langs != null && i < langs.length) ? langs[i] : "hi-IN";
                queue.add(ttsUrl(texts[i], lang));
            } catch (Exception ignored) {}
        }
        playNextOnline();
    }

    private void playNextOnline() {
        if (queueIndex >= queue.size()) {
            queue.clear();
            return;
        }

        final String url = queue.get(queueIndex++);
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setOnPreparedListener(player -> {
                mediaPlayer = player;
                player.setVolume(1.0f, 1.0f);
                player.start();
            });
            mp.setOnCompletionListener(player -> {
                player.release();
                if (mediaPlayer == player) mediaPlayer = null;
                playNextOnline();
            });
            mp.setOnErrorListener((player, what, extra) -> {
                try { player.reset(); } catch (Exception ignored) {}
                try { player.release(); } catch (Exception ignored) {}
                if (mediaPlayer == player) mediaPlayer = null;
                playNextOnline();
                return true;
            });
            mp.setDataSource(url);
            mp.prepareAsync();
        } catch (Exception ignored) {
            playNextOnline();
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private class OnlineAudioBridge {
        @JavascriptInterface
        public void speak(String text, String lang) {
            if (text == null || text.trim().isEmpty()) return;
            runOnUiThread(() -> playOnlineSequence(
                    new String[]{text},
                    new String[]{lang}
            ));
        }

        @JavascriptInterface
        public void speakSequence(String[] texts, String[] langs) {
            if (texts == null || texts.length == 0) return;
            runOnUiThread(() -> playOnlineSequence(texts, langs));
        }
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        queue.clear();
        if (webView != null) {
            webView.removeJavascriptInterface("OnlineAudio");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
