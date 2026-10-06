package com.stanisryz.logica.puzzle.core.daily

actual typealias DailyDate = java.time.LocalDate

actual fun dailyDateOfEpochDay(epochDay: Long): DailyDate = java.time.LocalDate.ofEpochDay(epochDay)
