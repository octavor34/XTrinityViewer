package com.xtrinityviewer.data

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xtrinityviewer.R
import java.io.InputStreamReader

data class RandomMessageDto(
    val message: String,
    val subMessage: String,
    val icon: String,
    val color: String
)

data class UiMessage(
    val title: String,
    val subtitle: String,
    val iconRes: Int?,
    val vectorIcon: ImageVector?,
    val color: Color
)

object MessageManager {

    fun getRandomMessage(context: Context): UiMessage {
        try {
            val inputStream = context.assets.open("frases_error.json")
            val reader = InputStreamReader(inputStream)
            val listType = object : TypeToken<List<RandomMessageDto>>() {}.type
            val messages: List<RandomMessageDto> = Gson().fromJson(reader, listType)
            reader.close()

            if (messages.isEmpty()) return getFallbackMessage()
            val selected = messages.random()
            val drawableId = context.resources.getIdentifier(selected.icon, "drawable", context.packageName)
            val finalIconRes = if (drawableId != 0) drawableId else null
            val vectorIcon = if (drawableId == 0) getVectorIconByName(selected.icon) else null
            val safeVector = if (finalIconRes == null && vectorIcon == null) Icons.Default.Error else vectorIcon
            val color = try {
                Color(android.graphics.Color.parseColor(selected.color))
            } catch (e: Exception) {
                Color.Gray
            }

            return UiMessage(selected.message, selected.subMessage, finalIconRes, safeVector, color)

        } catch (e: Exception) {
            e.printStackTrace()
            return getFallbackMessage()
        }
    }

    private fun getVectorIconByName(name: String): ImageVector {
        return when(name) {
            "ic_flash_off" -> Icons.Default.FlashOff
            "ic_security" -> Icons.Default.Security
            "ic_broken_image" -> Icons.Default.BrokenImage
            "ic_face" -> Icons.Default.Face
            "ic_pig" -> Icons.Default.Pets
            else -> Icons.Default.Warning
        }
    }

    private fun getFallbackMessage(): UiMessage {
        return UiMessage(
            "No se encontró nada",
            "Intenta buscar otra cosa.",
            null,
            Icons.Default.SearchOff,
            Color.Gray
        )
    }
}