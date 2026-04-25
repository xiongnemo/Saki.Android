package com.anzupop.saki.android.presentation

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

sealed interface UiText {
    fun asString(context: Context): String

    data class Dynamic(val value: String) : UiText {
        override fun asString(context: Context): String = value
    }

    data class Resource(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText {
        override fun asString(context: Context): String {
            if (args.isEmpty()) return context.getString(id)

            val resolvedArgs = args.map { arg ->
                if (arg is UiText) arg.asString(context) else arg
            }.toTypedArray()
            return context.getString(id, *resolvedArgs)
        }
    }

    data class Plural(
        @PluralsRes
        val id: Int,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : UiText {
        override fun asString(context: Context): String {
            if (args.isEmpty()) return context.resources.getQuantityString(id, quantity)

            val resolvedArgs = args.map { arg ->
                if (arg is UiText) arg.asString(context) else arg
            }.toTypedArray()
            return context.resources.getQuantityString(id, quantity, *resolvedArgs)
        }
    }

    companion object {
        fun resource(@StringRes id: Int, vararg args: Any): UiText = Resource(id, args.toList())

        fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any): UiText = Plural(id, quantity, args.toList())

        fun dynamic(value: String): UiText = Dynamic(value)
    }
}

@Composable
fun UiText.asString(): String = asString(LocalContext.current)

fun Throwable.localizedOr(@StringRes fallback: Int): UiText {
    val detail = message?.takeIf { it.isNotBlank() }
    return if (detail != null) UiText.Dynamic(detail) else UiText.Resource(fallback)
}
