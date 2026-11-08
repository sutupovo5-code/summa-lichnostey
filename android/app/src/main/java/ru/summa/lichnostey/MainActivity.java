package ru.summa.lichnostey;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Приложение «Сумма Личностей»: открывает сайт команды в WebView.
 * Сайт живой — все правки на GitHub Pages сразу видны в приложении без переустановки.
 */
public class MainActivity extends Activity {

    static final String SITE = "https://sutupovo5-code.github.io/summa-lichnostey/";
    static final String HOST = "sutupovo5-code.github.io";
    static final int REQ_FILE = 41;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xFF1A0E30);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);           // localStorage: баланс Σ, магазин, настройки
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setTextZoom(100);                     // крупный шрифт в настройках телефона не ломает вёрстку
        s.setUserAgentString(s.getUserAgentString() + " SummaApp/" + BuildConfigLite.version(this));
        // без интернета — показать последнюю сохранённую версию сайта
        s.setCacheMode(isOnline() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "SummaApp");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, ua, disposition, mime, length) -> download(url, disposition, mime));

        if (state != null) {
            web.restoreState(state);
        } else {
            Uri data = getIntent() != null ? getIntent().getData() : null;
            web.loadUrl(data != null && HOST.equals(data.getHost()) ? data.toString() : SITE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data != null && HOST.equals(data.getHost())) web.loadUrl(data.toString());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    /** «Назад»: сначала закрываются окна сайта (они пишут шаг в историю), потом выход. */
    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private boolean isOnline() {
        try {
            ConnectivityManager c = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = c.getNetworkCapabilities(c.getActiveNetwork());
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String scheme = u.getScheme() == null ? "" : u.getScheme();
            boolean ours = HOST.equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/summa-lichnostey");
            if (ours) return false;
            if (scheme.equals("https") || scheme.equals("http") || scheme.equals("tel") || scheme.equals("mailto")
                    || scheme.equals("tg") || scheme.equals("intent") || scheme.equals("whatsapp")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "Нет приложения, чтобы открыть ссылку", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            view.getSettings().setCacheMode(isOnline() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            if (!req.isForMainFrame()) return;
            String html = "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>"
                    + "<body style='margin:0;min-height:100vh;display:grid;place-items:center;background:#1a0e30;color:#fff;font-family:sans-serif;text-align:center'>"
                    + "<div style='padding:24px'><div style='font-size:64px'>📡</div>"
                    + "<h2 style='margin:12px 0 6px'>Нет интернета</h2>"
                    + "<p style='opacity:.75;line-height:1.5'>Сайт «Сумма Личностей» не загрузился.<br>Проверьте связь и попробуйте ещё раз.</p>"
                    + "<a href='" + SITE + "' style='display:inline-block;margin-top:14px;padding:14px 26px;border-radius:16px;"
                    + "background:linear-gradient(90deg,#ec6f9b,#f2a061);color:#1d0d1e;font-weight:800;text-decoration:none'>Повторить</a></div>";
            view.loadDataWithBaseURL(SITE, html, "text/html", "utf-8", SITE);
        }
    }

    private class Chrome extends WebChromeClient {
        /** Кнопки «Прикрепить фото/видео» на сайте открывают галерею телефона. */
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = cb;
            Intent i;
            try {
                i = params.createIntent();
            } catch (Exception e) {
                i = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            }
            if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try {
                startActivityForResult(Intent.createChooser(i, "Выберите файл"), REQ_FILE);
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, "Не нашлось галереи", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != REQ_FILE || fileCallback == null) return;
        Uri[] res = null;
        if (result == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                res = new Uri[n];
                for (int k = 0; k < n; k++) res[k] = data.getClipData().getItemAt(k).getUri();
            } else if (data.getData() != null) {
                res = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(res);
        fileCallback = null;
    }

    /** Скачивание: картинки с кодом восстановления, QR и т.п. сохраняются в «Загрузки/Сумма». */
    private void download(String url, String disposition, String mime) {
        String name = URLUtil.guessFileName(url, disposition, mime);
        if (url.startsWith("data:")) {
            saveDataUrl(url, name);
        } else if (url.startsWith("blob:")) {
            String js = "(async()=>{try{const b=await (await fetch('" + url + "')).blob();const r=new FileReader();"
                    + "r.onload=()=>SummaApp.save(r.result," + quote(name) + ");r.readAsDataURL(b)}catch(e){SummaApp.toast('Не удалось сохранить файл')}})()";
            web.evaluateJavascript(js, null);
        } else {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
            }
        }
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private void saveDataUrl(String dataUrl, String name) {
        try {
            int comma = dataUrl.indexOf(',');
            String meta = dataUrl.substring(5, comma);
            String mime = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
            byte[] bytes = meta.endsWith(";base64")
                    ? Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                    : Uri.decode(dataUrl.substring(comma + 1)).getBytes("UTF-8");
            if (name == null || name.isEmpty() || name.equals("downloadfile.bin")) name = "summa-" + System.currentTimeMillis();
            if (!name.contains(".")) name += mime.equals("image/png") ? ".png" : mime.equals("image/jpeg") ? ".jpg" : "";
            String where;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime.isEmpty() ? "application/octet-stream" : mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Сумма");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    os.write(bytes);
                }
                where = "Загрузки/Сумма";
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                File f = new File(dir, name);
                try (FileOutputStream os = new FileOutputStream(f)) {
                    os.write(bytes);
                }
                where = f.getParent();
            }
            toastMain("Сохранено: " + where + "/" + name);
        } catch (Exception e) {
            toastMain("Не удалось сохранить файл");
        }
    }

    private void toastMain(String t) {
        runOnUiThread(() -> Toast.makeText(this, t, Toast.LENGTH_LONG).show());
    }

    /** Мост для сайта: window.SummaApp.save(dataUrl, name), SummaApp.toast(text), SummaApp.version(). */
    private class Bridge {
        @JavascriptInterface
        public void save(String dataUrl, String name) {
            saveDataUrl(dataUrl, name);
        }

        @JavascriptInterface
        public void toast(String text) {
            toastMain(text);
        }

        @JavascriptInterface
        public String version() {
            return BuildConfigLite.version(MainActivity.this);
        }
    }

    /** Номер версии приложения без генерации BuildConfig. */
    static final class BuildConfigLite {
        static String version(Context c) {
            try {
                return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "1";
            }
        }
    }
}
