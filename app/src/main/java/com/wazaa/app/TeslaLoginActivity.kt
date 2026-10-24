package com.wazaa.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Connexion Tesla par **OAuth** (à la demande, une seule fois).
 *
 * Deux flux sont possibles :
 *  - sans `client_id` configuré : client public historique `ownerapi` + URI
 *    `tesla://auth/callback` (vérifié fonctionnel le 2026-10-19) ;
 *  - avec une application Fleet API enregistrée sur developer.tesla.com (`client_id` + URI
 *    déclarée).
 * La saisie manuelle d'un token (procédure TeslaMate) reste disponible en secours.
 */
class TeslaLoginActivity : Activity() {

    companion object {
        const val EXTRA_AUTH_BASE = "auth_base"
        const val EXTRA_CLIENT_ID = "client_id"
        const val EXTRA_REDIRECT_URI = "redirect_uri"
        const val EXTRA_SCOPE = "scope"
        private const val TAG = "Wazaa"
    }

    private lateinit var web: WebView
    private lateinit var authBase: String
    private lateinit var clientId: String
    private lateinit var redirectUri: String
    private lateinit var scope: String
    private var verifier = ""
    private var state = ""
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        authBase = i?.getStringExtra(EXTRA_AUTH_BASE) ?: TeslaAuth.DEFAULT_AUTH_BASE
        clientId = i?.getStringExtra(EXTRA_CLIENT_ID).orEmpty()
        redirectUri = i?.getStringExtra(EXTRA_REDIRECT_URI).orEmpty()
        scope = i?.getStringExtra(EXTRA_SCOPE) ?: TeslaAuth.SCOPE_FLEET

        if (clientId.isBlank() || redirectUri.isBlank()) {
            finishWithError(
                "Aucun client_id Fleet API configuré. Enregistrez une application sur " +
                        "developer.tesla.com, ou utilisez « Coller un token » (procédure TeslaMate)."
            )
            return
        }

        verifier = TeslaAuth.newVerifier()
        state = TeslaAuth.newState()

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString + " Wazaa/1.0"
        }
        CookieManager.getInstance().setAcceptCookie(true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                intercept(request.url.toString())

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = intercept(url)

            override fun onPageFinished(view: WebView, url: String) {
                // Tesla renvoie une page d'erreur (200) quand la configuration OAuth est refusée :
                // on la détecte pour afficher un message utile au lieu d'une page muette.
                if (url.startsWith(authBase) && !url.contains("code=")) {
                    view.evaluateJavascript("(document.body && document.body.innerText) || ''") { text ->
                        val t = text?.replace("\\n", " ") ?: ""
                        if (t.contains("redirect_uri", ignoreCase = true) ||
                            t.contains("client_id", ignoreCase = true)
                        ) {
                            finishWithError(
                                "Configurations OAuth refusée par Tesla " +
                                        "(redirect_uri / client_id). Vérifiez l'application " +
                                        "enregistrée sur developer.tesla.com, ou collez un token."
                            )
                        }
                    }
                }
            }
        }

        setContentView(FrameLayout(this).apply { addView(web) })
        web.loadUrl(
            TeslaAuth.authorizeUrl(authBase, clientId, redirectUri, scope, state, verifier)
        )
        Log.d(TAG, "auth: page OAuth ouverte (client=$clientId redirect=$redirectUri)")
    }

    private fun intercept(url: String): Boolean {
        if (finished || !url.startsWith(redirectUri)) return false
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        if (error != null) {
            finishWithError(uri.getQueryParameter("error_description") ?: error)
            return true
        }
        if (code.isNullOrBlank()) {
            finishWithError("code absent du retour OAuth")
            return true
        }
        val returnedState = uri.getQueryParameter("state")
        if (!returnedState.isNullOrBlank() && returnedState != state) {
            finishWithError("state OAuth invalide")
            return true
        }

        Log.d(TAG, "auth: code reçu, échange en cours")
        Thread {
            val token = TeslaAuth.exchangeCode(authBase, clientId, redirectUri, code, verifier)
            runOnUiThread {
                if (token == null) {
                    finishWithError("échange du code refusé par Tesla")
                } else {
                    TeslaTokenStore(applicationContext).save(token)
                    Log.d(TAG, "auth: jeton enregistré (expire dans ${token.expiresInMinutes()} min)")
                    setResult(RESULT_OK)
                    finished = true
                    finish()
                }
            }
        }.start()
        return true
    }

    private fun finishWithError(message: String) {
        Log.w(TAG, "auth: échec — $message")
        setResult(RESULT_CANCELED, Intent().putExtra("error", message))
        finished = true
        finish()
    }

    @Deprecated("nécessaire pour annuler proprement")
    override fun onBackPressed() {
        if (!finished) {
            setResult(RESULT_CANCELED)
            finished = true
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
