package io.nekohasekai.sagernet.ui

/**
 * Profile ids whose cards must be rebound when the selected profile changes
 * from [oldSelectedId] to [newSelectedId]: both the previously selected card
 * (to clear its blue border) and the newly selected card (to draw it).
 * 0 means "nothing selected" and needs no refresh.
 */
fun selectionRefreshIds(oldSelectedId: Long, newSelectedId: Long): List<Long> =
    listOf(oldSelectedId, newSelectedId).filter { it != 0L }.distinct()
