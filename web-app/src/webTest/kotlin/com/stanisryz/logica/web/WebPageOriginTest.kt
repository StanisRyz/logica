package com.stanisryz.logica.web

import kotlin.test.Test
import kotlin.test.assertEquals

class WebPageOriginTest {
    @Test
    fun anArchiveOpenedFromTheHubGoesBackDayToListToHub() {
        assertEquals(WebArchiveBack.DAY_LIST, webArchiveBack(WebPageOrigin.GAME_HUB, selectedDay = 20_729L))
        assertEquals(WebArchiveBack.GAME_HUB, webArchiveBack(WebPageOrigin.GAME_HUB, selectedDay = null))
    }

    @Test
    fun anArchiveDayOpenedFromTheProfileCalendarGoesBackToTheCalendar() {
        assertEquals(WebArchiveBack.PROFILE_CALENDAR, webArchiveBack(WebPageOrigin.PROFILE, selectedDay = 20_729L))
        assertEquals(WebArchiveBack.PROFILE_CALENDAR, webArchiveBack(WebPageOrigin.PROFILE, selectedDay = null))
    }
}
