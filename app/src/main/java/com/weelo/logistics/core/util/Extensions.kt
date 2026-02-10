package com.weelo.logistics.core.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import timber.log.Timber

/**
 * Extension functions for common operations
 */

// Context Extensions
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.showLongToast(message: String) {
    showToast(message, Toast.LENGTH_LONG)
}

// View Extensions
fun View.visible() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.isVisible(): Boolean = visibility == View.VISIBLE

fun View.enable() {
    isEnabled = true
}

fun View.disable() {
    isEnabled = false
}

// LiveData Extensions
fun <T> LiveData<T>.observeOnce(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
    observe(lifecycleOwner, object : Observer<T> {
        override fun onChanged(value: T) {
            observer.onChanged(value)
            removeObserver(this)
        }
    })
}

// String Extensions
fun String.isValidEmail(): Boolean {
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$".toRegex()
    return this.matches(emailRegex)
}

fun String.isValidPhone(): Boolean {
    val phoneRegex = "^[6-9]\\d{9}$".toRegex()
    return this.matches(phoneRegex)
}

fun String.sanitize(): String {
    return this.trim()
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("&", "&amp;")
}

// Logging Extensions
fun Any.logDebug(message: String) {
    Timber.tag(this::class.java.simpleName).d(message)
}

fun Any.logError(message: String, throwable: Throwable? = null) {
    Timber.tag(this::class.java.simpleName).e(throwable, message)
}

fun Any.logInfo(message: String) {
    Timber.tag(this::class.java.simpleName).i(message)
}

// Number Extensions
fun Double.format(digits: Int = 2): String = "%.${digits}f".format(this)

fun Int.toCurrency(): String = "₹$this"

fun Double.toCurrency(): String = "₹${this.format(0)}"

// Intent Parcelable extension for API compatibility
inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }
}

// Intent Parcelable Array extension for API compatibility
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Parcelable> Intent.getParcelableArrayExtraCompat(key: String): Array<T>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        val parcelables = getParcelableArrayExtra(key) ?: return null
        parcelables.filterIsInstance<T>().toTypedArray()
    }
}

// Intent ArrayList Parcelable extension for API compatibility
inline fun <reified T : Parcelable> Intent.getParcelableArrayListExtraCompat(key: String): ArrayList<T>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(key)
    }
}

// Bundle Parcelable extension for API compatibility
inline fun <reified T : Parcelable> android.os.Bundle.getParcelableCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key) as? T
    }
}

// Bundle ArrayList Parcelable extension for API compatibility
inline fun <reified T : Parcelable> android.os.Bundle.getParcelableArrayListCompat(key: String): ArrayList<T>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayList(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayList(key)
    }
}
