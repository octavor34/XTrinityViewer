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
    val icon: String, // Nombre del recurso (ej: "ic_pig") o nombre de icono material
    val color: String // Hex (ej: "#FF0000")
)

data class UiMessage(
    val title: String,
    val subtitle: String,
    val iconRes: Int?, // Para imágenes PNG/XML personalizadas
    val vectorIcon: ImageVector?, // Para iconos nativos de Android
    val color: Color
)

object MessageManager {

    fun getRandomMessage(context: Context): UiMessage {
        try {
            // 1. Leer el JSON desde assets
            val inputStream = context.assets.open("frases_error.json")
            val reader = InputStreamReader(inputStream)
            val listType = object : TypeToken<List<RandomMessageDto>>() {}.type
            val messages: List<RandomMessageDto> = Gson().fromJson(reader, listType)
            reader.close()

            if (messages.isEmpty()) return getFallbackMessage()

            // 2. Elegir uno al azar
            val selected = messages.random()

            // 3. Resolver el Icono (String -> Recurso)
            // Primero intentamos buscarlo en la carpeta 'drawable'
            val drawableId = context.resources.getIdentifier(selected.icon, "drawable", context.packageName)

            val finalIconRes = if (drawableId != 0) drawableId else null

            // Si no hay drawable, intentamos mapear a iconos nativos por nombre (Opcional)
            val vectorIcon = if (drawableId == 0) getVectorIconByName(selected.icon) else null

            // Si fallan los dos, usamos un icono por defecto
            val safeVector = if (finalIconRes == null && vectorIcon == null) Icons.Default.Error else vectorIcon

            // 4. Resolver Color
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
            "ic_pig" -> Icons.Default.Pets // Si no tienes icono de cerdo, usa huella
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