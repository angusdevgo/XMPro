package teikengstudio.xmpro.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream
import java.security.SecureRandom
import java.util.Base64

/**
 * GeeTest v4 challenge in an isolated WebView.
 *
 * The document is served from a private `appassets` origin that only we can
 * answer, third-party cookies stay off, and any subresource outside the GeeTest
 * domains is blocked. The dialog never sees account credentials.
 */
@Composable
fun GeetestDialog(
    captchaId: String,
    onDismiss: () -> Unit,
    onSuccess: (Map<String, String>) -> Unit,
) {
    val nonce = remember { newNonce() }
    val documentUrl = "https://appassets.androidplatform.net/xmpro-captcha/$nonce/index.html"
    var finished by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            finished = true
            onDismiss()
        },
        title = { Text("安全验证") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                when {
                    error != null -> Text(
                        error ?: "",
                        color = MaterialTheme.colorScheme.error,
                    )

                    !ready -> Text("正在加载安全验证…")

                    else -> Text("请完成下方验证后返回登录。")
                }
                if (error == null) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(380.dp),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.setSupportMultipleWindows(false)
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mediaPlaybackRequiresUserGesture = true
                                settings.userAgentString = settings.userAgentString
                                setBackgroundColor(0)
                                @SuppressLint("AddJavascriptInterface")
                                addJavascriptInterface(
                                    Bridge { kind, payload ->
                                        if (finished) return@Bridge
                                        when (kind) {
                                            "ready" -> ready = true
                                            "success" -> {
                                                val fields = parseChallenge(payload, captchaId)
                                                if (fields == null) {
                                                    error = "安全验证结果无效，请关闭后重新验证。"
                                                } else {
                                                    finished = true
                                                    onSuccess(fields)
                                                }
                                            }

                                            "error" -> error = "安全验证未完成，请关闭后重试，或使用 Cookie 登录。"
                                            "close" -> {
                                                finished = true
                                                onDismiss()
                                            }
                                        }
                                    },
                                    "xmlyCaptcha",
                                )
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onConsoleMessage(
                                        message: android.webkit.ConsoleMessage,
                                    ): Boolean {
                                        android.util.Log.d("Geetest", message.message())
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean = request.url.toString() != documentUrl

                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): WebResourceResponse? {
                                        val url = request.url.toString()
                                        if (url == documentUrl) {
                                            ready = true
                                            return WebResourceResponse(
                                                "text/html",
                                                "utf-8",
                                                200,
                                                "OK",
                                                mapOf("Cache-Control" to "no-store"),
                                                ByteArrayInputStream(html(captchaId, nonce).toByteArray()),
                                            )
                                        }
                                        if (request.isForMainFrame || !allowed(url)) {
                                            return WebResourceResponse(
                                                "text/plain",
                                                "utf-8",
                                                403,
                                                "Forbidden",
                                                emptyMap(),
                                                ByteArrayInputStream(ByteArray(0)),
                                            )
                                        }
                                        return null
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: android.webkit.WebResourceError,
                                    ) {
                                        android.util.Log.d(
                                            "Geetest",
                                            "error ${error.errorCode} ${error.description} ${request.url} main=${request.isForMainFrame}",
                                        )
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        if (url != null && url != documentUrl) error = "安全验证不允许跳转。"
                                    }
                                }
                                loadUrl(documentUrl)
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                finished = true
                onDismiss()
            }) { Text("关闭") }
        },
    )
}

private class Bridge(private val handler: (String, String?) -> Unit) {
    @JavascriptInterface
    fun post(nonce: String, kind: String, payload: String?) = handler(kind, payload)
}

private val FIELDS = listOf("captcha_id", "lot_number", "pass_token", "gen_time", "captcha_output")

private fun parseChallenge(payload: String?, captchaId: String): Map<String, String>? {
    if (payload == null) return null
    val json = runCatching { org.json.JSONObject(payload) }.getOrNull() ?: return null
    if (json.length() != FIELDS.size) return null
    val fields = linkedMapOf<String, String>()
    for (key in FIELDS) {
        val value = json.opt(key) as? String ?: return null
        if (!Regex("^[\\x21-\\x7e]{1,8192}$").matches(value)) return null
        fields[key] = value
    }
    if (!fields.getValue("captcha_id").equals(captchaId, ignoreCase = true)) return null
    return fields
}

private fun newNonce(): String =
    Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

private fun allowed(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    // java.net.URI reports -1 when the URL omits the port, which is the normal
    // case for geetest.com; only an explicit non-443 port is rejected.
    if (uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return false
    val host = uri.host ?: return false
    return listOf("geetest.com", "geevisit.com", "gsensebot.com")
        .any { host == it || host.endsWith(".$it") }
}

private fun html(captchaId: String, nonce: String): String {
    val id = org.json.JSONObject.quote(captchaId)
    val token = org.json.JSONObject.quote(nonce)
    return """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://*.geetest.com https://*.geevisit.com https://*.gsensebot.com; connect-src https://*.geetest.com https://*.geevisit.com https://*.gsensebot.com; img-src data: blob: https://*.geetest.com https://*.geevisit.com https://*.gsensebot.com; style-src 'unsafe-inline' https://*.geetest.com; font-src data: https://*.geetest.com; frame-src https://*.geetest.com https://*.geevisit.com https://*.gsensebot.com; object-src 'none'; base-uri 'none'; form-action 'none'">
<style>body{font:15px sans-serif;text-align:center;padding:16px;color:#333}</style>
</head><body><p id="status">正在加载安全验证…</p>
<script>
(function(){
  var nonce=$token;
  function emit(kind,payload){ if(window.xmlyCaptcha) window.xmlyCaptcha.post(nonce,kind,payload?JSON.stringify(payload):null); }
  function fail(){ document.getElementById('status').textContent='安全验证加载失败，请关闭后重试'; emit('error'); }
  function start(){
    if(typeof initGeetest4!=='function'){ fail(); return; }
    initGeetest4({captchaId:$id, product:'bind', language:'zho', protocol:'https://', onError:fail, offlineCb:fail}, function(captcha){
      captcha.onReady(function(){
        document.getElementById('status').textContent='请完成安全验证';
        emit('ready');
        captcha.showCaptcha();
      }).onSuccess(function(){
        var v=captcha.getValidate();
        if(!v){ fail(); return; }
        var out={captcha_id:$id};
        ['lot_number','pass_token','gen_time','captcha_output'].forEach(function(k){ out[k]=v[k]; });
        emit('success',out);
      }).onError(fail).onClose(function(){ emit('close'); });
    });
  }
  var script=document.createElement('script');
  script.src='https://static.geetest.com/v4/gt4.js';
  script.onload=start; script.onerror=fail;
  document.head.appendChild(script);
})();
</script></body></html>
    """.trimIndent()
}
