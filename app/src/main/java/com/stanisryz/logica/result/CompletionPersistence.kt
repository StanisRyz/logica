package com.stanisryz.logica.result

internal sealed interface CompletionPersistence {
    data object NotRequired : CompletionPersistence

    data object Saving : CompletionPersistence

    data object Saved : CompletionPersistence

    data object Error : CompletionPersistence
}
