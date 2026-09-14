package com.travelbenefits.app.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

private val shortDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatEpochDay(epochDay: Long?): String? = epochDay?.let { LocalDate.ofEpochDay(it).format(shortDate) }

fun formatDateRange(start: Long?, end: Long?): String? {
    val s = formatEpochDay(start) ?: return null
    val e = formatEpochDay(end)
    return if (e == null || end == start) s else "$s – $e"
}

fun formatEpochMillisDate(epochMillis: Long?): String? =
    epochMillis?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(shortDate) }

/** "just now", "35 min ago", "3 h ago", "2 d ago", or the date for anything older. */
fun formatRelative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - epochMillis
    if (diff < 0) return formatEpochMillisDate(epochMillis).orEmpty()
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours h ago"
        days < 7 -> "$days d ago"
        else -> formatEpochMillisDate(epochMillis).orEmpty()
    }
}

fun formatPoints(points: Long?): String = points?.let { String.format("%,d", it) } ?: "—"

fun formatUsd(amount: Double): String = String.format("$%,.0f", amount)

fun formatMultiplier(multiplier: Double, asPercent: Boolean): String {
    val trimmed = if (multiplier == multiplier.toLong().toDouble()) multiplier.toLong().toString() else multiplier.toString()
    return if (asPercent) "$trimmed%" else "${trimmed}x"
}

/** Parses "2026-03-14" or "3/14/2026" into an epoch day; null when it isn't a date. */
fun parseUserDate(raw: String): Long? {
    val text = raw.trim()
    if (text.isBlank()) return null
    runCatching { LocalDate.parse(text) }.getOrNull()?.let { return it.toEpochDay() }
    val us = Regex("""(\d{1,2})/(\d{1,2})/(\d{2,4})""").matchEntire(text) ?: return null
    val (m, d, y) = us.destructured
    val year = y.toInt().let { if (it < 100) 2000 + it else it }
    return runCatching { LocalDate.of(year, m.toInt(), d.toInt()).toEpochDay() }.getOrNull()
}
