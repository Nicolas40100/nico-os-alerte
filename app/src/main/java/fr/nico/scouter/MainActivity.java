package fr.nico.scouter;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int CAMERA_REQUEST = 1001;
    private static final int SPEECH_REQUEST = 1002;
    private WebView webView;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new TextToSpeech(this, this);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " NicoScouter/0.1");
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) request.grant(request.getResources());
                    else { request.deny(); requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST); }
                });
            }
        });
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        loadApp();
    }

    private void loadApp() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("index.html"), StandardCharsets.UTF_8));
            StringBuilder html = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) html.append(line).append('\n');
            reader.close();
            webView.loadDataWithBaseURL("https://localhost/", html.toString(), "text/html", "UTF-8", null);
        } catch (Exception e) { Toast.makeText(this, "Impossible de charger l'application : " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) { tts.setLanguage(Locale.FRENCH); tts.setSpeechRate(1.05f); }
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String text = results.get(0);
                String safe = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
                webView.evaluateJavascript("window.setSearchTerm('" + safe + "')", null);
            }
        }
    }

    public class AndroidBridge {
        @JavascriptInterface public void startVoice() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
                    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Que veux-tu chercher ?");
                    startActivityForResult(intent, SPEECH_REQUEST);
                } catch (Exception e) { Toast.makeText(MainActivity.this, "Reconnaissance vocale indisponible", Toast.LENGTH_SHORT).show(); }
            });
        }
        @JavascriptInterface public void speak(String text) {
            runOnUiThread(() -> { if (tts != null && text != null && !text.trim().isEmpty()) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nico_scouter"); });
        }
        @JavascriptInterface public void requestCamera() {
            runOnUiThread(() -> { if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST); });
        }
    }
}
