package com.salati.app

import com.salati.app.domain.Prayer
import com.salati.app.domain.QadaaMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QadaaMathTest {
    @Test fun yearlyEstimateUses365Days() {
        assertEquals(365, QadaaMath.estimateDays(1.0))
        assertEquals(3650, QadaaMath.estimateDays(10.0))
        assertEquals(0, QadaaMath.estimateDays(0.0))
    }

    @Test fun fractionalYearsRoundHalfUp() {
        assertEquals(913, QadaaMath.estimateDays(2.5))   // 912.5
        assertEquals(183, QadaaMath.estimateDays(0.5))   // 182.5
        assertEquals(91, QadaaMath.estimateDays(0.25))   // 91.25
        assertEquals(37, QadaaMath.estimateDays(0.1))    // 36.5
    }

    @Test fun estimateCoversFiveObligatoryPrayersOnly() {
        val b = QadaaMath.estimateBalances(2.0)
        assertEquals(5, b.size)
        assertEquals(setOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA), b.keys)
        b.values.forEach { assertEquals(730, it) }
    }

    @Test fun parsesWesternAndArabicDigits() {
        assertEquals(2.5, QadaaMath.parseYears("2.5")!!, 0.0)
        assertEquals(2.5, QadaaMath.parseYears("2,5")!!, 0.0)
        assertEquals(2.5, QadaaMath.parseYears("٢٫٥")!!, 0.0)
        assertEquals(3.0, QadaaMath.parseYears(" ٣ ")!!, 0.0)
    }

    @Test fun rejectsInvalidYears() {
        assertNull(QadaaMath.parseYears(""))
        assertNull(QadaaMath.parseYears("-1"))
        assertNull(QadaaMath.parseYears("abc"))
        assertNull(QadaaMath.parseYears("101"))
        assertNull(QadaaMath.parseYears("1e2"))
        assertNull(QadaaMath.parseYears("NaN"))
    }
}
