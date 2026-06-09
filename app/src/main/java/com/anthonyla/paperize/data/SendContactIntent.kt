package com.anthonyla.paperize.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity

/**
 * Opens an email intent to contact the developer.
 */
fun SendContactIntent(context: Context) {
    try {
        val authorEmail = "anthonyyla.dev@gmail.com"
        val cc = ""
        val subject = "[Support] AutoWallpaperChangerPro"
        val bodyText = "This is regarding the AutoWallpaperChangerPro app for Android:\n"
        val mailto = "mailto:" + Uri.encode(authorEmail) +
                "?cc=" + Uri.encode(cc) +
                "&subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(bodyText)

        val emailIntent = Intent(Intent.ACTION_SENDTO)
        emailIntent.setData(Uri.parse(mailto))
        startActivity(context, emailIntent, null)
    } catch (e: ActivityNotFoundException) {
        Log.e("SendContactIntent", "No email app found", e)
        Toast.makeText(context, "No email app found. Please install an email app.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("SendContactIntent", "Error opening email intent", e)
        Toast.makeText(context, "Unable to open email app.", Toast.LENGTH_SHORT).show()
    }
}