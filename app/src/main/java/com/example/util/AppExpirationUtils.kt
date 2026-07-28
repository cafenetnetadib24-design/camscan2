package com.example.util

import java.util.Calendar

object AppExpirationUtils {
    /**
     * Expiration limit: March 28, 2027 (23:59:59)
     */
    val EXPIRATION_TIMESTAMP: Long by lazy {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, 2027)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 28)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    fun isAppExpired(): Boolean {
        return System.currentTimeMillis() > EXPIRATION_TIMESTAMP
    }
}
