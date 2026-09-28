package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PaymentProductSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class WebPaidPriceTest {
    private fun product(
        price: String?,
        priceValue: String?,
        code: String?,
    ) = PaymentProductSnapshot("gems_small", null, null, price, priceValue, code, null)

    @Test
    fun catalogPriceAlreadyNamesItsCurrencyAndIsShownAsIs() {
        assertEquals("20 YAN", paidPriceText(product("20 YAN", "20", "YAN")))
    }

    @Test
    fun missingPriceTextFallsBackToValueAndCode() {
        assertEquals("20 YAN", paidPriceText(product(null, "20", "YAN")))
        assertEquals("—", paidPriceText(product(null, null, null)))
    }
}
