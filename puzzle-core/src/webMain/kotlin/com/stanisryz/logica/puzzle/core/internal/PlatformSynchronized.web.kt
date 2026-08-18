package com.stanisryz.logica.puzzle.core.internal

/** Current browser runtimes access core state on one thread, so synchronization is a no-op. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal actual annotation class PlatformSynchronized
