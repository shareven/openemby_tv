package com.xxxx.emby_tv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor
import com.xxxx.emby_tv.util.ErrorHandler
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class LocalServer private constructor(
    port: Int,
    private val mode: Mode,
    private val onLoginConfigReceived: ((String, String, String, String, String) -> Unit)? = null,
    private val onSearchReceived: ((String) -> Unit)? = null,
    private val onProxyConfigReceived: ((Boolean, String, String, Int, String, String) -> Unit)? = null
) : NanoHTTPD(port) {

    enum class Mode {
        LOGIN, SEARCH, PROXY
    }

    companion object {
        private var themePrimary: String = "#448AFF"
        private var themeSecondary: String = "#E040FB"

        fun startServer(
            themePrimaryDark: Color,
            themeSecondaryLight: Color,
            onConfigReceived: (String, String, String, String, String) -> Unit
        ): LocalServer? {
            updateThemeColors(themePrimaryDark, themeSecondaryLight)
            return startInternal(Mode.LOGIN, onLoginConfigReceived = onConfigReceived)
        }

        fun startSearchServer(
            themePrimaryDark: Color,
            themeSecondaryLight: Color,
            onSearchReceived: (String) -> Unit
        ): LocalServer? {
            updateThemeColors(themePrimaryDark, themeSecondaryLight)
            return startInternal(Mode.SEARCH, onSearchReceived = onSearchReceived)
        }

        fun startProxyServer(
            themePrimaryDark: Color,
            themeSecondaryLight: Color,
            onProxyConfigReceived: (Boolean, String, String, Int, String, String) -> Unit
        ): LocalServer? {
            updateThemeColors(themePrimaryDark, themeSecondaryLight)
            return startInternal(Mode.PROXY, onProxyConfigReceived = onProxyConfigReceived)
        }

        private fun updateThemeColors(primary: Color, secondary: Color) {
            val primaryArgb = primary.toArgb()
            val primaryRgb = AndroidColor.rgb(
                AndroidColor.red(primaryArgb),
                AndroidColor.green(primaryArgb),
                AndroidColor.blue(primaryArgb)
            )
            val secondaryArgb = secondary.toArgb()
            val secondaryRgb = AndroidColor.rgb(
                AndroidColor.red(secondaryArgb),
                AndroidColor.green(secondaryArgb),
                AndroidColor.blue(secondaryArgb)
            )
            themePrimary = String.format("#%06X", 0xFFFFFF and primaryRgb)
            themeSecondary = String.format("#%06X", 0xFFFFFF and secondaryRgb)
        }

        private fun startInternal(
            mode: Mode,
            onLoginConfigReceived: ((String, String, String, String, String) -> Unit)? = null,
            onSearchReceived: ((String) -> Unit)? = null,
            onProxyConfigReceived: ((Boolean, String, String, Int, String, String) -> Unit)? = null
        ): LocalServer? {
            var port = 4000
            while (port < 4010) {
                try {
                    val server = LocalServer(port, mode, onLoginConfigReceived, onSearchReceived, onProxyConfigReceived)
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    return server
                } catch (e: IOException) {
                    port++
                }
            }
            return null
        }
    }

    override fun serve(session: IHTTPSession): Response {
        return when (mode) {
            Mode.LOGIN -> serveLogin(session)
            Mode.SEARCH -> serveSearch(session)
            Mode.PROXY -> serveProxy(session)
        }
    }

    private fun serveSearch(session: IHTTPSession): Response {
        if (session.method == Method.POST) {
            try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val params = session.parameters
                val query = params["query"]?.firstOrNull() ?: ""

                if (query.isNotEmpty()) {
                    onSearchReceived?.invoke(query)
                    return serveSuccessPage("Search Sent!", "The search query has been sent to your TV.", "搜索已发送", "搜索词已发送到电视。")
                }
            } catch (e: Exception) {
                ErrorHandler.logError("LocalServer", "Error processing search", e)
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error parsing request")
            }
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Emby TV Search</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta charset="UTF-8">
                <style>
                    :root { --theme-primary: $themePrimary; --theme-secondary: $themeSecondary; }
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        padding: 0; margin: 0;
                        background: linear-gradient(135deg, var(--theme-primary), var(--theme-secondary));
                        color: white;
                        display: flex; justify-content: center; align-items: center;
                        min-height: 100vh;
                    }
                    .container {
                        background: rgba(0, 0, 0, 0.2);
                        padding: 40px;
                        border-radius: 16px;
                        backdrop-filter: blur(10px);
                        box-shadow: 0 4px 30px rgba(0, 0, 0, 0.3);
                        width: 90%; max-width: 400px;
                        border: 1px solid rgba(255, 255, 255, 0.1);
                    }
                    h2 { text-align: center; margin-bottom: 30px; font-weight: 300; }
                    input {
                        width: 100%; padding: 14px; margin-bottom: 20px;
                        box-sizing: border-box;
                        background: rgba(0, 0, 0, 0.3);
                        border: 1px solid rgba(255, 255, 255, 0.2);
                        border-radius: 8px; color: white; font-size: 16px;
                    }
                    input::placeholder {
                        color: rgba(255, 255, 255, 0.5);
                    }
                    input:focus {
                        border-color: var(--theme-primary);
                        outline: none;
                    }
                    button {
                        width: 100%; padding: 14px;
                        background: white; color: black;
                        border: none; border-radius: 8px;
                        font-size: 16px; font-weight: bold; cursor: pointer;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2 id="title">Emby TV Search</h2>
                    <form method="POST">
                        <input type="text" name="query" id="input-query" placeholder="Enter keywords..." required autofocus>
                        <button type="submit" id="btn-submit">Search on TV</button>
                    </form>
                </div>
                <script>
                    if (navigator.language.startsWith('zh')) {
                        document.getElementById('title').innerText = 'Emby TV 搜索';
                        document.getElementById('input-query').placeholder = '输入搜索关键词...';
                        document.getElementById('btn-submit').innerText = '在电视上搜索';
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(html)
    }

    private fun serveLogin(session: IHTTPSession): Response {
        if (session.method == Method.POST) {
            try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val params = session.parameters

                val protocol = params["protocol"]?.firstOrNull() ?: "http"
                val host = params["host"]?.firstOrNull() ?: ""
                val port = params["port"]?.firstOrNull() ?: "8096"
                val username = params["username"]?.firstOrNull() ?: ""
                val password = params["password"]?.firstOrNull() ?: ""

                if (host.isNotEmpty()) {
                    onLoginConfigReceived?.invoke(protocol, host, port, username, password)
                    return serveSuccessPage("Configuration Sent!", "You can verify the login on your TV.", "配置已发送！", "请在电视上验证登录。")
                }
            } catch (e: Exception) {
                ErrorHandler.logError("LocalServer", "服务器错误", e)
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error parsing request")
            }
        }
        
        // Serve the form
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Emby TV Login</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta charset="UTF-8">
                <style>
                    :root {
                        --theme-primary: $themePrimary;
                        --theme-secondary: $themeSecondary;
                    }
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        padding: 0;
                        margin: 0;
                        background: linear-gradient(135deg, var(--theme-primary), var(--theme-secondary));
                        color: white;
                        display: flex;
                        flex-wrap: wrap;
                        justify-content: center;
                        align-items: flex-start;
                        min-height: 100vh;
                    }
                    .container {
                        background: transparent;
                        padding: 40px 20px;
                        width: 90%;
                        max-width: 400px;
                        margin-top: 20px;
                        margin-bottom: 20px;
                    }
                    h2 {
                        text-align: center;
                        margin-bottom: 30px;
                        font-weight: 300;
                        letter-spacing: 1px;
                    }
                    label {
                        display: block;
                        margin-bottom: 8px;
                        font-size: 14px;
                        color: #ccc;
                    }
                    .input-row {
                        display: flex;
                        gap: 8px;
                        margin-bottom: 20px;
                        align-items: center;
                        justify-content: center;
                    }
                    .input-row input {
                        width: 100%;
                        padding: 12px;
                        box-sizing: border-box;
                        background: rgba(0, 0, 0, 0.3);
                        border: 1px solid rgba(255, 255, 255, 0.2);
                        border-radius: 8px;
                        color: white;
                        font-size: 16px;
                    }
                    .input-row input:focus {
                        border-color: #448AFF;
                        outline: none;
                    }
                    .protocol-btn {
                        padding: 12px 20px;
                        background: rgba(255, 255, 255, 0.1);
                        border: 1px solid rgba(255, 255, 255, 0.2);
                        border-radius: 8px;
                        color: white;
                        font-size: 16px;
                        cursor: pointer;
                        transition: all 0.2s;
                        min-width: 80px;
                        margin-bottom: 20px;
                    }
                    .protocol-btn.active {
                        background: rgba(255, 255, 255, 0.2);
                    }
                    .protocol { flex: 0 0 80px; }
                    .host { flex: 1; }
                    .port { flex: 0 0 80px; }
                    input {
                        width: 100%;
                        padding: 12px;
                        margin-bottom: 20px;
                        box-sizing: border-box;
                        background: rgba(0, 0, 0, 0.3);
                        border: 1px solid rgba(255, 255, 255, 0.2);
                        border-radius: 8px;
                        color: white;
                        font-size: 16px;
                        transition: border-color 0.3s;
                    }
                    input::placeholder {
                        color: rgba(255, 255, 255, 0.5);
                    }
                    input:focus {
                        border-color: var(--theme-primary);
                        outline: none;
                    }
                    button {
                        width: 100%;
                        padding: 14px;
                        background: white;
                        color: black;
                        border: none;
                        border-radius: 8px;
                        font-size: 16px;
                        font-weight: bold;
                        cursor: pointer;
                        transition: transform 0.2s, opacity 0.2s;
                    }
                    button:active {
                        transform: scale(0.98);
                        opacity: 0.9;
                    }
                    .success {
                        text-align: center;
                        color: #4CAF50;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2 id="form-title">Emby TV Login</h2>
                    <form method="POST" id="login-form">
                        <label id="label-url">Server URL</label>
                        <div class="input-row">
                            <input type="hidden" name="protocol" id="protocol-input" value="http">
                            <button type="button" class="protocol protocol-btn active" id="btn-protocol" onclick="toggleProtocol()">HTTP</button>
                            <input type="text" name="host" class="host" placeholder="192.168.1.x" required>
                            <input type="text" name="port" class="port" placeholder="Port" value="8096">
                        </div>
                        <label id="label-user">Username</label>
                        <input type="text" name="username" id="input-username" placeholder="Username" required>
                        <label id="label-pass">Password</label>
                        <input type="password" name="password" id="input-password" placeholder="Password">
                        <button type="submit" id="btn-submit">Send to TV</button>
                    </form>
                </div>
                <script>
                    function toggleProtocol() {
                        var btn = document.getElementById('btn-protocol');
                        var input = document.getElementById('protocol-input');
                        if (input.value === 'http') {
                            input.value = 'https';
                            btn.innerText = 'HTTPS';
                        } else {
                            input.value = 'http';
                            btn.innerText = 'HTTP';
                        }
                    }
                    if (navigator.language.startsWith('zh')) {
                        document.getElementById('form-title').innerText = 'Emby TV 登录';
                        document.getElementById('label-url').innerText = '服务器地址';
                        document.getElementById('label-user').innerText = '用户名';
                        document.getElementById('input-username').placeholder = '用户名';
                        document.getElementById('label-pass').innerText = '密码';
                        document.getElementById('input-password').placeholder = '密码';
                        document.getElementById('btn-submit').innerText = '发送到电视';
                    }
                    var lastScrollY = 0;
                    if (window.visualViewport) {
                        window.visualViewport.addEventListener('resize', function() {
                            var container = document.querySelector('.container');
                            if (window.visualViewport.height < window.innerHeight) {
                                var diff = window.innerHeight - window.visualViewport.height;
                                var containerBottom = container.getBoundingClientRect().bottom;
                                if (containerBottom > window.visualViewport.height - 50) {
                                    var scrollAmount = containerBottom - (window.visualViewport.height - 100);
                                    window.scrollTo({ top: scrollAmount, behavior: 'smooth' });
                                }
                            } else {
                                if (lastScrollY > 0) {
                                    window.scrollTo({ top: 0, behavior: 'smooth' });
                                }
                            }
                        });
                    }
                    var inputs = document.querySelectorAll('input');
                    inputs.forEach(function(input) {
                        input.addEventListener('focus', function() {
                            setTimeout(function() {
                                var btn = document.getElementById('btn-submit');
                                btn.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            }, 300);
                        });
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
        
        return newFixedLengthResponse(html)
    }

    private fun serveProxy(session: IHTTPSession): Response {
        if (session.method == Method.POST) {
            try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val params = session.parameters

                val enabled = params["enabled"]?.firstOrNull() == "on"
                val type = params["type"]?.firstOrNull() ?: "http"
                val host = params["host"]?.firstOrNull() ?: ""
                val portStr = params["port"]?.firstOrNull() ?: "1080"
                val port = portStr.toIntOrNull() ?: 1080
                val username = params["username"]?.firstOrNull() ?: ""
                val password = params["password"]?.firstOrNull() ?: ""

                onProxyConfigReceived?.invoke(enabled, type, host, port, username, password)
                return serveSuccessPage("Configuration Sent!", "Proxy settings have been sent to your TV.", "配置已发送！", "代理设置已发送到电视。")
            } catch (e: Exception) {
                ErrorHandler.logError("LocalServer", "Error processing proxy config", e)
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error parsing request")
            }
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Emby TV Proxy Settings</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta charset="UTF-8">
                <style>
                    :root { --theme-primary: $themePrimary; --theme-secondary: $themeSecondary; }
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        padding: 0; margin: 0;
                        background: linear-gradient(135deg, var(--theme-primary), var(--theme-secondary));
                        color: white;
                        display: flex; justify-content: center; align-items: center;
                        min-height: 100vh;
                    }
                    .container {
                        background: rgba(0, 0, 0, 0.2);
                        padding: 40px;
                        border-radius: 16px;
                        backdrop-filter: blur(10px);
                        box-shadow: 0 4px 30px rgba(0, 0, 0, 0.3);
                        width: 90%; max-width: 420px;
                        border: 1px solid rgba(255, 255, 255, 0.1);
                    }
                    h2 { text-align: center; margin-bottom: 30px; font-weight: 300; }
                    .toggle-row {
                        display: flex; align-items: center; justify-content: space-between;
                        margin-bottom: 24px; padding: 12px 16px;
                        background: rgba(0, 0, 0, 0.2); border-radius: 10px;
                    }
                    .toggle-row label { font-size: 16px; }
                    .switch { position: relative; width: 52px; height: 28px; }
                    .switch input { opacity: 0; width: 0; height: 0; }
                    .slider {
                        position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0;
                        background-color: rgba(255,255,255,0.2); border-radius: 28px; transition: .3s;
                    }
                    .slider:before {
                        position: absolute; content: ""; height: 22px; width: 22px;
                        left: 3px; bottom: 3px; background: white; border-radius: 50%; transition: .3s;
                    }
                    input:checked + .slider { background-color: var(--theme-primary); }
                    input:checked + .slider:before { transform: translateX(24px); }
                    label { display: block; margin-bottom: 8px; font-size: 14px; color: #ccc; }
                    .type-row {
                        display: flex; gap: 8px; margin-bottom: 20px;
                    }
                    .type-btn {
                        flex: 1; padding: 10px; text-align: center;
                        background: rgba(0, 0, 0, 0.2); border: 1px solid rgba(255,255,255,0.15);
                        border-radius: 8px; color: white; font-size: 15px; cursor: pointer; transition: .2s;
                    }
                    .type-btn.active { background: rgba(255,255,255,0.2); border-color: rgba(255,255,255,0.4); }
                    .input-row {
                        display: flex; gap: 8px; margin-bottom: 16px;
                    }
                    .input-row input {
                        width: 100%; padding: 12px; box-sizing: border-box;
                        background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.2);
                        border-radius: 8px; color: white; font-size: 16px;
                    }
                    .input-row input:focus { border-color: var(--theme-primary); outline: none; }
                    .host { flex: 1; }
                    .port { flex: 0 0 90px; }
                    input {
                        width: 100%; padding: 12px; margin-bottom: 16px; box-sizing: border-box;
                        background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.2);
                        border-radius: 8px; color: white; font-size: 16px;
                    }
                    input::placeholder { color: rgba(255,255,255,0.4); }
                    input:focus { border-color: var(--theme-primary); outline: none; }
                    .scope-text { 
                        text-align: center; color: rgba(255,255,255,0.5); 
                        font-size: 13px; margin-bottom: 24px; 
                    }
                    button {
                        width: 100%; padding: 14px;
                        background: white; color: black;
                        border: none; border-radius: 8px;
                        font-size: 16px; font-weight: bold; cursor: pointer;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2 id="form-title">Proxy Settings</h2>
                    <form method="POST" id="proxy-form">
                        <input type="hidden" name="enabled" id="input-enabled" value="off">
                        <input type="hidden" name="type" id="input-type" value="http">
                        <div class="toggle-row">
                            <label id="label-enable">Enable Proxy</label>
                            <label class="switch">
                                <input type="checkbox" id="toggle-enable" onchange="document.getElementById('input-enabled').value=this.checked?'on':'off'">
                                <span class="slider"></span>
                            </label>
                        </div>
                        <label id="label-type">Proxy Type</label>
                        <div class="type-row">
                            <div class="type-btn active" id="btn-http" onclick="setType('http')">HTTP</div>
                            <div class="type-btn" id="btn-socks5" onclick="setType('socks5')">SOCKS5</div>
                        </div>
                        <label id="label-host">Proxy Host</label>
                        <div class="input-row">
                            <input type="text" name="host" class="host" id="input-host" placeholder="192.168.1.x">
                            <input type="text" name="port" class="port" id="input-port" placeholder="Port" value="1080">
                        </div>
                        <label id="label-user">Username (optional)</label>
                        <input type="text" name="username" id="input-username" placeholder="">
                        <label id="label-pass">Password (optional)</label>
                        <input type="password" name="password" id="input-password" placeholder="">
                        <div class="scope-text" id="scope-text">Proxy for Emby service only</div>
                        <button type="submit" id="btn-submit">Send to TV</button>
                    </form>
                </div>
                <script>
                    function setType(t) {
                        document.getElementById('input-type').value = t;
                        document.getElementById('btn-http').className = 'type-btn' + (t==='http'?' active':'');
                        document.getElementById('btn-socks5').className = 'type-btn' + (t==='socks5'?' active':'');
                    }
                    if (navigator.language.startsWith('zh')) {
                        document.getElementById('form-title').innerText = '代理设置';
                        document.getElementById('label-enable').innerText = '启用代理';
                        document.getElementById('label-type').innerText = '代理类型';
                        document.getElementById('label-host').innerText = '代理地址';
                        document.getElementById('input-host').placeholder = '代理服务器地址';
                        document.getElementById('input-port').placeholder = '端口';
                        document.getElementById('label-user').innerText = '用户名（可选）';
                        document.getElementById('label-pass').innerText = '密码（可选）';
                        document.getElementById('scope-text').innerText = '仅代理Emby服务';
                        document.getElementById('btn-submit').innerText = '发送到电视';
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(html)
    }

    private fun serveSuccessPage(enTitle: String, enDesc: String, zhTitle: String, zhDesc: String): Response {
        val successHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Success</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta charset="UTF-8">
                <style>
                    :root { --theme-primary: $themePrimary; --theme-secondary: $themeSecondary; }
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        padding: 0; margin: 0;
                        background: linear-gradient(135deg, var(--theme-primary), var(--theme-secondary));
                        color: white;
                        display: flex; justify-content: center; align-items: center;
                        min-height: 100vh;
                    }
                    .container {
                        background: rgba(255, 255, 255, 0.1);
                        padding: 40px; border-radius: 16px;
                        backdrop-filter: blur(10px);
                        box-shadow: 0 4px 30px rgba(0, 0, 0, 0.5);
                        width: 90%; max-width: 400px; text-align: center;
                    }
                    h2 { margin-bottom: 20px; font-weight: 300; }
                    p { color: #ccc; margin-bottom: 30px; }
                    .icon { font-size: 64px; margin-bottom: 20px; color: var(--theme-primary); }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon">✓</div>
                    <h2 id="success-title">$enTitle</h2>
                    <p id="success-desc">$enDesc</p>
                </div>
                <script>
                    if (navigator.language.startsWith('zh')) {
                        document.getElementById('success-title').innerText = '$zhTitle';
                        document.getElementById('success-desc').innerText = '$zhDesc';
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(successHtml)
    }
}
