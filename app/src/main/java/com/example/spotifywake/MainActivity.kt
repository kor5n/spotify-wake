package com.example.spotifywake

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        private val CLIENT_ID = BuildConfig.SPOTIFY_CLIENT_ID
        private val CLIENT_SECRET = BuildConfig.SPOTIFY_CLIENT_SECRET
        private const val REDIRECT_URI = "spotifywake://callback"
        private const val PLAYLIST_URI = "spotify:playlist:5l37TRiqGM4zBHTd9moGp4"
        private const val TAG = "MainActivity"
    }

    private var accessToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startAuthFlow()
    }

    private fun startAuthFlow() {
        val uri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", "user-modify-playback-state user-read-playback-state")
            .build()

        CustomTabsIntent.Builder().build().launchUrl(this, uri)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            if (uri.toString().startsWith("spotifywake://callback")) {
                val code = uri.getQueryParameter("code")
                val error = uri.getQueryParameter("error")
                when {
                    code != null -> {
                        Log.d(TAG, "Got auth code, exchanging for token...")
                        exchangeCodeForToken(code)
                    }
                    error != null -> Log.e(TAG, "Auth error: $error")
                    else -> Log.e(TAG, "Unknown redirect: $uri")
                }
            }
        }
    }

    private fun exchangeCodeForToken(code: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://accounts.spotify.com/api/token")
                val body = "grant_type=authorization_code&code=$code&redirect_uri=${Uri.encode(REDIRECT_URI)}"
                val credentials = android.util.Base64.encodeToString(
                    "$CLIENT_ID:$CLIENT_SECRET".toByteArray(),
                    android.util.Base64.NO_WRAP
                )

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Basic $credentials")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    doOutput = true
                    outputStream.write(body.toByteArray())
                }

                val response = connection.inputStream.bufferedReader().readText()
                val token = JSONObject(response).getString("access_token")
                Log.d(TAG, "Got access token, starting playback...")

                withContext(Dispatchers.Main) {
                    accessToken = token
                    startPlayback(token)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token exchange failed: ${e.message}", e)
            }
        }
    }

    private fun startPlayback(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {

                //start spotify app
                withContext(Dispatchers.Main) {
                    val intent = packageManager.getLaunchIntentForPackage("com.spotify.music")
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                }

                delay(3000)

                val devicesConn = (URL("https://api.spotify.com/v1/me/player/devices")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                }

                val devicesCode = devicesConn.responseCode
                val devicesBody = if (devicesCode in 200..299) {
                    devicesConn.inputStream.bufferedReader().readText()
                } else {
                    devicesConn.errorStream?.bufferedReader()?.readText() ?: "no error body"
                }

                Log.d(TAG, "Devices response $devicesCode: $devicesBody")

                if (devicesCode !in 200..299) {
                    Log.e(TAG, "Devices call failed with $devicesCode")
                    return@launch
                }

                val devices = JSONObject(devicesBody).getJSONArray("devices")

                if (devices.length() == 0) {
                    Log.e(TAG, "No active Spotify devices — open Spotify on your phone first")
                    return@launch
                }

                val deviceId = devices.getJSONObject(0).getString("id")
                Log.d(TAG, "Using device: $deviceId")

                val playConn = (URL("https://api.spotify.com/v1/me/player/play?device_id=$deviceId")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    outputStream.write("""{"context_uri":"$PLAYLIST_URI"}""".toByteArray())
                }

                val playCode = playConn.responseCode
                val playBody = if (playCode in 200..299) "success"
                else playConn.errorStream?.bufferedReader()?.readText() ?: "no error body"

                Log.d(TAG, "Play response $playCode: $playBody")

            } catch (e: Exception) {
                Log.e(TAG, "Playback failed: ${e.message}", e)
            }
        }
    }
}