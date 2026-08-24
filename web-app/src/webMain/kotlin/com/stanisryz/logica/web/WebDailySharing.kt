package com.stanisryz.logica.web

/**
 * The small browser share adapter for the spoiler-free Daily text. Sharing is user-initiated only:
 * the shared Daily completion card invokes this once per explicit tap. Native Web Share is
 * preferred; the clipboard is the fallback when it is unavailable or fails recoverably. Sharing
 * has no gameplay or persistence side effects, so failures stay silent and non-fatal.
 */
internal object WebDailyTextSharer {
    fun share(text: String) {
        if (isWebNativeShareAvailable()) {
            nativeWebShare(text)
        } else {
            copyWebTextToClipboard(text)
        }
    }
}

private fun isWebNativeShareAvailable(): Boolean =
    js(
        "typeof globalThis.navigator !== 'undefined' && " +
            "typeof globalThis.navigator.share === 'function'",
    )

// A user-cancelled share sheet must not fall back to the clipboard; every other failure may.
private fun nativeWebShare(text: String): Unit =
    js(
        "globalThis.navigator.share({ text: text }).then(" +
            "() => {}, " +
            "(error) => { if (error && error.name !== 'AbortError') { " +
            "try { globalThis.navigator.clipboard.writeText(text); } catch (ignored) {} } })",
    )

private fun copyWebTextToClipboard(text: String): Unit =
    js(
        "try { globalThis.navigator.clipboard.writeText(text); } catch (ignored) {}",
    )

