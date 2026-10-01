package com.apnipathshala.app;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private WebView webView;
    private MediaPlayer mediaPlayer;
    private final Map<String, String> audioFiles = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) am.setMode(AudioManager.MODE_NORMAL);

        prepareAudioFiles();

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new AudioBridge(), "AndroidAudio");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void prepareAudioFiles() {
        File dir = new File(getCacheDir(), "apni_audio");
        if (!dir.exists()) dir.mkdirs();

        for (char c = 'A'; c <= 'Z'; c++) copyAudio("abc_" + c + ".wav", dir);
        for (int i = 0; i <= 48; i++) copyAudio("hi_" + i + ".wav", dir);
    }

    private void copyAudio(String name, File dir) {
        try {
            File out = new File(dir, name);
            if (!out.exists() || out.length() < 1000) {
                try (InputStream in = getAssets().open("audio/" + name);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                }
            }
            if (out.exists() && out.length() >= 1000) {
                audioFiles.put(name, out.getAbsolutePath());
            }
        } catch (Exception ignored) {}
    }

    private void playSound(String fileName) {
        String path = audioFiles.get(fileName);
        if (path == null) return;

        try {
            releasePlayer();

            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(path);
            mp.setOnPreparedListener(player -> {
                mediaPlayer = player;
                player.setVolume(1.0f, 1.0f);
                player.start();
            });
            mp.setOnCompletionListener(player -> {
                player.release();
                if (mediaPlayer == player) mediaPlayer = null;
            });
            mp.setOnErrorListener((player, what, extra) -> {
                try { player.reset(); } catch (Exception ignored) {}
                player.release();
                if (mediaPlayer == player) mediaPlayer = null;
                return true;
            });
            mp.prepareAsync();
        } catch (Exception ignored) {
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
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
        releasePlayer();
        audioFiles.clear();
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidAudio");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
