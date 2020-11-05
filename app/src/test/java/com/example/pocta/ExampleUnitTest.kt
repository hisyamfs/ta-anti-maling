package com.example.pocta

import android.os.Build.VERSION_CODES
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [VERSION_CODES.LOLLIPOP])
class ExampleUnitTest {
    companion object {
        fun getAddress(immobilizer: Immobilizer?): String = immobilizer?.address ?: "0"
        fun getName(immobilizer: Immobilizer?): String = immobilizer?.name ?: "0"
    }

    @Test
    fun testDBFunctions() {
    }
}
