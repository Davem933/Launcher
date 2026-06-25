package com.example.carlauncher.data.model

data class WidgetSlot(
    val appWidgetId: Int,
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int
)

fun WidgetSlot.serialize(): String = "$appWidgetId:$col:$row:$colSpan:$rowSpan"

fun String.toWidgetSlot(): WidgetSlot? = runCatching {
    val p = split(":")
    require(p.size == 5)
    WidgetSlot(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt())
}.getOrNull()

fun List<WidgetSlot>.serializeAll(): String = joinToString("|") { it.serialize() }

fun String.toWidgetSlots(): List<WidgetSlot> =
    if (isBlank()) emptyList()
    else split("|").mapNotNull { it.toWidgetSlot() }
