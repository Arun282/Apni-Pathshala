package com.apnipathshala.app;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private WebView webView;
    private MediaPlayer mediaPlayer;
    private final Map<String, byte[]> audioCache = new HashMap<>();
    private final Map<String, android.content.res.AssetFileDescriptor> descriptors = new HashMap<>();

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        preloadAudio();

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new AudioBridge(), "AndroidAudio");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    /*
     * MediaPlayer is used instead of SoundPool. This is more reliable for the
     * bundled WAV files and avoids SoundPool load/play timing problems.
     */
    private void preloadAudio() {
        for (char c = 'A'; c <= 'Z'; c++) {
            cacheAsset("abc_" + c + ".wav");
        }
        for (int i = 0; i <= 48; i++) {
            cacheAsset("hi_" + i + ".wav");
        }
    }

    private void cacheAsset(String fileName) {
        try {
            android.content.res.AssetFileDescriptor afd =
                    getAssets().openFd("audio/" + fileName);
            descriptors.put(fileName, afd);
        } catch (IOException ignored) {
        }
    }

    private void playSound(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return;
        android.content.res.AssetFileDescriptor afd = descriptors.get(fileName);
        if (afd == null) return;

        try {
            if (mediaPlayer != null) {
                try { mediaPlayer.stop(); } catch (Exception ignored) {}
                mediaPlayer.release();
                mediaPlayer = null;
            }

            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());

            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
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
                player.release();
                if (mediaPlayer == player) mediaPlayer = null;
                return true;
            });
            mp.prepareAsync();
        } catch (Exception ignored) {
            try { mpRelease(); } catch (Exception ignored2) {}
        }
    }

    private void mpRelease() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
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
        mpRelease();
        for (android.content.res.AssetFileDescriptor afd : descriptors.values()) {
            try { afd.close(); } catch (Exception ignored) {}
        }
        descriptors.clear();
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidAudio");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
