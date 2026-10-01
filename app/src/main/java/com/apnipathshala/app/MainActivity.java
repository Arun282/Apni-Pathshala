package com.apnipathshala.app;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {
    private WebView webView;
    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
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

    private void requestMusicFocus() {
        try {
            if (audioManager == null) return;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attrs)
                        .setAcceptsDelayedFocusGain(false)
                        .build();
                audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
        } catch (Exception ignored) {}
    }

    private void abandonMusicFocus() {
        try {
            if (audioManager == null) return;
            if (android.os.Build.VERSION.SDK_INT >= 26 && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(null);
            }
        } catch (Exception ignored) {}
    }

    private void playAsset(String fileName) {
        runOnUiThread(() -> {
            try {
                if (player != null) {
                    try { player.stop(); } catch (Exception ignored) {}
                    player.release();
                    player = null;
                }
                requestMusicFocus();
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                android.content.res.AssetFileDescriptor afd =
                        getAssets().openFd("audio/" + fileName);
                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                player.setVolume(1.0f, 1.0f);
                player.setOnCompletionListener(mp -> {
                    mp.release();
                    if (player == mp) player = null;
                    abandonMusicFocus();
                });
                player.setOnErrorListener((mp, what, extra) -> {
                    try { mp.reset(); } catch (Exception ignored) {}
                    mp.release();
                    if (player == mp) player = null;
                    abandonMusicFocus();
                    return true;
                });
                player.prepare();
                player.start();
            } catch (Exception ignored) {
                if (player != null) {
                    try { player.release(); } catch (Exception ignored2) {}
                    player = null;
                }
                abandonMusicFocus();
            }
        });
    }

    private class AudioBridge {
        @JavascriptInterface public void play(String fileName) {
            if (fileName == null || fileName.trim().isEmpty()) return;
            playAsset(fileName);
        }
    }

    @Override protected void onDestroy() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        abandonMusicFocus();
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidAudio");
            webView.destroy();
        }
        super.onDestroy();
    }
}
