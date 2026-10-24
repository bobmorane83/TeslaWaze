package com.wazaa.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Jeton Tesla (OAuth2) : `access_token` + `refresh_token` + **client_id utilisé**.
 *
 * On conserve le `client_id` car le rafraîchissement l'exige (`grant_type=refresh_token`) :
 *  - jeton **collé** (procédure TeslaMate / tesla_auth) → `ownerapi` (API historique) ;
 *  - jeton obtenu par **OAuth Fleet API** → le `client_id` de l'application enregistrée.
 */
data class TeslaToken(
    val access: String,
    val refresh: String?,
    val expiresAtMs: Long,
    val tokenType: String = "Bearer",
    /** Client OAuth qui a émis le jeton — indispensable au rafraîchissement. */
    val clientId: String = TeslaAuth.LEGACY_CLIENT_ID
) {
    fun expiresInMinutes(now: Long = System.currentTimeMillis()): Long =
        (expiresAtMs - now) / 60_000

    fun isExpiring(now: Long = System.currentTimeMillis()): Boolean =
        now > expiresAtMs - 120_000
}

/**
 * OAuth2 Tesla.
 *
 * **Constaté le 2026-10-19** :
 *  - le flux `ownerapi` + `redirect_uri=https://auth.tesla.com/void/callback` est refusé
 *    (« redirect_uri not registered for this client_id ») ;
 *  - le même client `ownerapi` avec `redirect_uri=tesla://auth/callback` fonctionne (correctif
 *    de tesla_auth 0.13+).
 *
 * Trois voies sont donc possibles dans l'app :
 *  1. **OAuth sans configuration** : client public `ownerapi` + `tesla://auth/callback`
 *     (utilisé quand aucun `client_id` n'est renseigné) ;
 *  2. **OAuth Fleet API** avec *votre* `client_id` enregistré sur developer.tesla.com et
 *     l'URI de redirection déclarée pour cette application ;
 *  3. **Coller un token** (comme TeslaMate) — toujours disponible en secours.
 */
object TeslaAuth {

    /** Client public historique (tesla_auth / TeslaMate). Sert au refresh d'un token collé. */
    const val LEGACY_CLIENT_ID = "ownerapi"

    /**
     * URI de retour du client `ownerapi` — **vérifié accepté le 2026-10-19** :
     * l'ancien `https://auth.tesla.com/void/callback` est refusé
     * (« redirect_uri not registered for this client_id »), `tesla://auth/callback` fonctionne
     * (c'est le correctif de tesla_auth 0.13+).
     */
    const val LEGACY_REDIRECT_URI = "tesla://auth/callback"

    /**
     * Scopes demandés au client `ownerapi`. Vérifié le 2026-10-19 : la page de login les
     * accepte, et un jeton **sans** `vehicle_device_data` / `vehicle_location` ne permet plus
     * de lire les données véhicule (erreur « Endpoint is only available on fleetapi »).
     */
    const val SCOPE_LEGACY = "openid vehicle_device_data vehicle_location offline_access"
    const val SCOPE_FLEET = "openid vehicle_device_data vehicle_location offline_access"

    const val DEFAULT_AUTH_BASE = "https://auth.tesla.com/oauth2/v3"

    fun newState(): String = randomUrlSafe(24)

    fun newVerifier(): String = randomUrlSafe(64)

    /** `BASE64URL(SHA256(verifier))` sans padding — PKCE S256. */
    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    fun authorizeUrl(
        authBase: String,
        clientId: String,
        redirectUri: String,
        scope: String,
        state: String,
        verifier: String
    ): String {
        val q = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to scope,
            "state" to state,
            "code_challenge" to challenge(verifier),
            "code_challenge_method" to "S256",
            "locale" to "fr-FR"
        ).joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
        return "$authBase/authorize?$q"
    }

    fun exchangeCode(
        authBase: String,
        clientId: String,
        redirectUri: String,
        code: String,
        verifier: String
    ): TeslaToken? {
        val (status, body) = Http.postForm(
            "$authBase/token",
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri,
                "client_id" to clientId,
                "code_verifier" to verifier
            )
        )
        return if (status == 200) parse(body, null, clientId) else null
    }

    /** Rafraîchissement (TeslaMate `TeslaApi.Auth.Refresh`) avec le `client_id` d'origine. */
    fun refresh(authBase: String, clientId: String, refreshToken: String): TeslaToken? {
        val (status, body) = Http.postForm(
            "$authBase/token",
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to clientId,
                "scope" to if (clientId == LEGACY_CLIENT_ID) SCOPE_LEGACY else SCOPE_FLEET
            )
        )
        return if (status == 200) parse(body, refreshToken, clientId) else null
    }

    /**
     * Expiration lue dans le JWT d'accès (les jetons Tesla sont des JWT) ; `null` si opaque.
     * Utile pour un jeton collé à la main, dont on ne reçoit pas `expires_in`.
     */
    fun jwtExpiryMs(access: String): Long? = jwtPayload(access)?.optLong("exp", 0L)
        ?.takeIf { it > 0L }?.times(1000L)

    /**
     * Hôte **Fleet API** porté par le jeton (`aud` du JWT), ex.
     * `https://fleet-api.prd.eu.vn.cloud.tesla.com`.
     *
     * Nécessaire car l'API historique répond désormais :
     * `Endpoint is only available on fleetapi`.
     */
    fun fleetBaseFromJwt(access: String): String? {
        val payload = jwtPayload(access)
        if (payload == null) {
            android.util.Log.d("Wazaa", "jwt: jeton non JWT (pas de payload)")
            return null
        }
        val aud = payload.opt("aud")
        val iss = payload.optString("iss")
        val audList = when (aud) {
            is JSONArray -> (0 until aud.length()).map { aud.optString(it) }
            is String -> listOf(aud)
            else -> emptyList()
        }
        // Trace utile au diagnostic (aucune donnée sensible : URL d'audience / émetteur).
        android.util.Log.d("Wazaa", "jwt: aud=$audList iss=$iss")
        return (audList + iss).firstOrNull { it.contains("fleet-api", ignoreCase = true) }
    }

    private fun jwtPayload(access: String): JSONObject? {
        val parts = access.split(".")
        if (parts.size < 2) return null
        return try {
            JSONObject(
                String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(body: String, previousRefresh: String?, clientId: String): TeslaToken? = try {
        val o = JSONObject(body)
        val access = o.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        val expiresIn = o.optLong("expires_in", 3600L)
        TeslaToken(
            access = access,
            // RFC 6749 §6 : sans nouveau refresh_token, l'ancien reste valable.
            refresh = o.optString("refresh_token").takeIf { it.isNotBlank() } ?: previousRefresh,
            expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L,
            tokenType = o.optString("token_type", "Bearer"),
            clientId = clientId
        )
    } catch (e: Exception) {
        null
    }

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }
}

/**
 * Stockage chiffré du jeton (AES/GCM, clé non exportable dans AndroidKeyStore).
 * La connexion n'est donc à refaire qu'en cas de révocation / expiration du refresh.
 */
class TeslaTokenStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tesla_auth", Context.MODE_PRIVATE)

    fun load(): TeslaToken? {
        val enc = prefs.getString(KEY_TOKEN, null) ?: return null
        val json = Crypto.decrypt(enc) ?: return null
        return try {
            val o = JSONObject(json)
            TeslaToken(
                access = o.getString("access"),
                refresh = o.optString("refresh").takeIf { it.isNotBlank() },
                expiresAtMs = o.getLong("expiresAt"),
                tokenType = o.optString("type", "Bearer"),
                clientId = o.optString("clientId", TeslaAuth.LEGACY_CLIENT_ID)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun save(token: TeslaToken) {
        val json = JSONObject()
            .put("access", token.access)
            .put("refresh", token.refresh ?: "")
            .put("expiresAt", token.expiresAtMs)
            .put("type", token.tokenType)
            .put("clientId", token.clientId)
            .toString()
        prefs.edit().putString(KEY_TOKEN, Crypto.encrypt(json)).apply()
    }

    fun clear() = prefs.edit().remove(KEY_TOKEN).apply()

    private companion object {
        const val KEY_TOKEN = "token"
    }
}

/** Chiffrement symétrique lié à l'appareil (clé AES-256-GCM dans AndroidKeyStore). */
internal object Crypto {

    private const val ALIAS = "wazaa_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(1 + iv.size + data.size)
        out[0] = iv.size.toByte()
        iv.copyInto(out, 1)
        data.copyInto(out, 1 + iv.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = try {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        val ivSize = raw[0].toInt()
        val iv = raw.copyOfRange(1, 1 + ivSize)
        val data = raw.copyOfRange(1 + ivSize, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
