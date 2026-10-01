package com.apnipathshala.app;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private WebView webView;
    private SoundPool soundPool;
    private final Map<String,Integer> sounds = new HashMap<>();
    private final Map<Integer,Boolean> loaded = new HashMap<>();
    private String pendingSound = null;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(attrs)
                .build();

        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            if (status == 0) {
                loaded.put(sampleId, true);
                if (pendingSound != null) {
                    String name = pendingSound;
                    pendingSound = null;
                    playSound(name);
                }
            }
        });

        preloadSounds();

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        webView.addJavascriptInterface(new AudioBridge(), "AndroidAudio");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void preloadSounds() {
        for (char c = 'A'; c <= 'Z'; c++) {
            loadAsset("abc_" + c + ".wav");
        }
        for (int i = 0; i <= 48; i++) {
            loadAsset("hi_" + i + ".wav");
        }
    }

    private void loadAsset(String fileName) {
        try {
            int id = soundPool.load(getAssets().openFd("audio/" + fileName), 1);
            sounds.put(fileName, id);
            loaded.put(id, false);
        } catch (IOException ignored) {
        }
    }

    private void playSound(String fileName) {
        if (soundPool == null) return;
        Integer id = sounds.get(fileName);
        if (id == null) return;
        if (!Boolean.TRUE.equals(loaded.get(id))) {
            pendingSound = fileName;
            return;
        }
        soundPool.play(id, 1f, 1f, 1, 0, 1f);
    }

    private class AudioBridge {
        @JavascriptInterface
        public void play(String fileName) {
            if (fileName == null || fileName.trim().isEmpty()) return;
            runOnUiThread(() -> playSound(fileName));
        }
    }

    @Override
    protected void onDestroy() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidAudio");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
