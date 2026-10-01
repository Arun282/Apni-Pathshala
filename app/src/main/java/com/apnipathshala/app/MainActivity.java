package com.apnipathshala.app;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {
    private WebView webView;
    private MediaPlayer player;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
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

    private void playAsset(String fileName) {
        runOnUiThread(() -> {
            try {
                if (player != null) { try { player.stop(); } catch (Exception ignored) {} player.release(); player = null; }
                player = new MediaPlayer();
                android.content.res.AssetFileDescriptor afd = getAssets().openFd("audio/" + fileName);
                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                player.setOnCompletionListener(mp -> { mp.release(); if (player == mp) player = null; });
                player.setOnErrorListener((mp, what, extra) -> { mp.release(); if (player == mp) player = null; return true; });
                player.prepare();
                player.start();
            } catch (Exception ignored) {
                if (player != null) { try { player.release(); } catch (Exception ignored2) {} player = null; }
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
        if (player != null) { try { player.stop(); } catch (Exception ignored) {} try { player.release(); } catch (Exception ignored) {} player = null; }
        if (webView != null) { webView.removeJavascriptInterface("AndroidAudio"); webView.destroy(); }
        super.onDestroy();
    }
}
