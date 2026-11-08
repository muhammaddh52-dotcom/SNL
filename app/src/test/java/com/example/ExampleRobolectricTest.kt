package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.OrderLineItem
import com.example.data.seed.AttachedPriceListCatalog
import com.example.util.ExcelAndSheetsProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appName_andAttached59ItemsCatalog_andExcelParser_workCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("SNL Lubricants", context.getString(R.string.app_name))

        // Verify all 59 items from the attached SAIFI National Lubricants price list (03-11-26)
        val rows = AttachedPriceListCatalog.attachedImageExtractedRows
        assertEquals(59, rows.size)

        // Row 1: Reborn 700ml x 12 SC 20w50 = PKR 5,105
        val row1 = rows.first { it.serialNo == 1 }
        assertEquals("Reborn", row1.productName)
        assertEquals(5105.0, row1.distributorPricePkr!!, 0.01)

        // Rows 34, 35, 36: Liquix L-7000+ SP 5w30 have '-' in the attached image and are flagged
        val row34 = rows.first { it.serialNo == 34 }
        assertNull(row34.distributorPricePkr)
        assertTrue(row34.isUncertainOrMissingPrice)

        // Row 59: Gear Oil 75W-85 20 Ltr = PKR 11,000
        val row59 = rows.first { it.serialNo == 59 }
        assertEquals(11000.0, row59.distributorPricePkr!!, 0.01)

        // Verify immutable OrderLineItem price snapshot encoding & decoding
        val snapshot = OrderLineItem.encodeList(
            listOf(
                OrderLineItem(
                    sku = "SNL-001",
                    productName = "Reborn",
                    packagingSize = "700ml x 12",
                    grade = "SC 20w50",
                    quantity = 5,
                    unitPricePkr = 5105.0,
                    lineTotalPkr = 25525.0
                )
            )
        )
        val decoded = OrderLineItem.decodeList(snapshot)
        assertEquals(1, decoded.size)
        assertEquals("SNL-001", decoded.first().sku)
        assertEquals(25525.0, decoded.first().lineTotalPkr, 0.01)

        // Verify 6-sheet Excel template generation
        val excelRes = ExcelAndSheetsProcessor.generateAndShareOfficialExcelTemplate(context)
        assertTrue(excelRes.isSuccess)
        val file = excelRes.getOrNull()
        assertNotNull(file)
        assertTrue(file!!.readText().contains("Worksheet ss:Name=\"Price Matrix\""))

        // Verify Signed APK metadata & side-loading distribution helper
        val apkMeta = com.example.util.ApkDistributionHelper.getInstalledApkMetadata(context)
        assertEquals("com.aistudio.snllubricants.pkb2b", apkMeta.applicationId)
        assertTrue(apkMeta.sha1Fingerprint.isNotBlank())
        assertTrue(
            com.example.util.ApkDistributionHelper.buildSideLoadInstructionsMessage()
                .contains("SNL_Lubricants_B2B_v1.0_Signed.apk")
        )
    }
}

