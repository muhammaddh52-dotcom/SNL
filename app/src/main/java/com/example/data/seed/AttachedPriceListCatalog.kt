package com.example.data.seed

import com.example.data.model.Category
import com.example.data.model.ExtractedPriceRow
import com.example.data.model.PriceGroup

/**
 * Exact, verbatim product and pricing data extracted from the user's attached
 * SAIFI National Lubricants Lube Price List ("For : (Sole Distributor PAKISTAN) 03-11-26").
 *
 * - Total rows in attached image: 59 (S. No. 1 through 59)
 * - Rows 1..33 and 37..59 have clear numerical Sole Distributor PKR prices.
 * - Rows 34, 35, 36 (Liquix L-7000+ SP 5w30 in 1ltr x 12, 3ltr x 4, 4 ltr x 4)
 *   have "-" in the price column of the attached image and are explicitly flagged
 *   as uncertain/missing price (`isUncertainOrMissingPrice = true`) per OCR safety rules,
 *   never fabricated or guessed.
 */
object AttachedPriceListCatalog {

    const val SOURCE_HEADER_TITLE = "SAIFI National Lubricants — Lube Price List"
    const val SOURCE_HEADER_SUBTITLE = "For : (Sole Distributor PAKISTAN) | Effective Date: 03-11-26"
    const val SOURCE_DETECTED_PRICE_COLUMN = "Sole Distributor (PKR)"
    const val COMPANY_PHONE = "+92 310 100 52 53"
    const val COMPANY_LOCATION = "KARACHI, PAKISTAN"

    val initialPriceGroups: List<PriceGroup> = listOf(
        PriceGroup(
            id = "DISTRIBUTOR",
            groupCode = "DISTRIBUTOR",
            groupName = "Sole Distributor / Distributor",
            description = "Official Sole Distributor Pakistan price tier from attached SNL price list (03-11-26)",
            defaultDiscountPercent = 0.0,
            minOrderQty = 1L,
            isActive = true
        ),
        PriceGroup(
            id = "STANDARD_DEALER",
            groupCode = "STANDARD_DEALER",
            groupName = "Standard Dealer",
            description = "Authorized Standard Dealer price group",
            defaultDiscountPercent = 0.0,
            minOrderQty = 1L,
            isActive = true
        ),
        PriceGroup(
            id = "PREMIUM_DEALER",
            groupCode = "PREMIUM_DEALER",
            groupName = "Premium Dealer",
            description = "High-volume Premium Dealer price group",
            defaultDiscountPercent = 0.0,
            minOrderQty = 1L,
            isActive = true
        ),
        PriceGroup(
            id = "SPECIAL_DEALER",
            groupCode = "SPECIAL_DEALER",
            groupName = "Special Dealer",
            description = "Special negotiated Dealer price group",
            defaultDiscountPercent = 0.0,
            minOrderQty = 1L,
            isActive = true
        )
    )

    val initialCategories: List<Category> = listOf(
        Category(
            id = "MIX_BRANDS",
            categoryCode = "MIX_BRANDS",
            categoryName = "MIX Brands",
            nameUrdu = "مکس برانڈز",
            sortOrder = 1L,
            isActive = true
        ),
        Category(
            id = "M_CYCLE",
            categoryCode = "M_CYCLE",
            categoryName = "M. Cycle",
            nameUrdu = "موٹر سائیکل آئل",
            sortOrder = 2L,
            isActive = true
        ),
        Category(
            id = "GASOLINE_CNG",
            categoryCode = "GASOLINE_CNG",
            categoryName = "GASOLINE / CNG",
            nameUrdu = "پیٹرول اور سی این جی",
            sortOrder = 3L,
            isActive = true
        ),
        Category(
            id = "DIESEL",
            categoryCode = "DIESEL",
            categoryName = "DIESEL",
            nameUrdu = "ڈیزل انجن آئل",
            sortOrder = 4L,
            isActive = true
        ),
        Category(
            id = "FLUIDS",
            categoryCode = "FLUIDS",
            categoryName = "FLUIDS",
            nameUrdu = "بریک فلوئڈ اور کولنٹ",
            sortOrder = 5L,
            isActive = true
        ),
        Category(
            id = "HYD_OIL",
            categoryCode = "HYD_OIL",
            categoryName = "HYD. OIL",
            nameUrdu = "ہائیڈرولک آئل",
            sortOrder = 6L,
            isActive = true
        ),
        Category(
            id = "GEAR_OIL",
            categoryCode = "GEAR_OIL",
            categoryName = "GEAR OIL",
            nameUrdu = "گیئر آئل",
            sortOrder = 7L,
            isActive = true
        )
    )

    private fun row(
        sNo: Int,
        catCode: String,
        catName: String,
        item: String,
        packing: String,
        grade: String,
        rawPrice: String,
        price: Double?
    ): ExtractedPriceRow {
        val sku = "SNL-%03d".format(sNo)
        val lowerPack = packing.lowercase()
        val unit = when {
            lowerPack.contains("bucket") -> "Bucket"
            lowerPack.contains("can") -> "Can"
            lowerPack.contains("x") -> "Carton"
            else -> "Bucket/Can"
        }
        val cartonQty = if (lowerPack.contains("x")) {
            lowerPack.substringAfter("x").trim().toIntOrNull() ?: 1
        } else 1

        val isUncertain = price == null
        val note = if (isUncertain) {
            "Price shown as '-' in attached price list image (S. No. $sNo). Requires Admin entry/verification."
        } else {
            "Verified from attached SNL Sole Distributor Pakistan price list (03-11-26)."
        }

        return ExtractedPriceRow(
            serialNo = sNo,
            sku = sku,
            productName = item,
            categoryCode = catCode,
            categoryName = catName,
            packagingSize = packing,
            packagingUnit = unit,
            cartonQty = cartonQty,
            grade = grade,
            rawPriceText = rawPrice,
            distributorPricePkr = price,
            ocrConfidence = if (isUncertain) 50 else 99,
            isUncertainOrMissingPrice = isUncertain,
            uncertaintyNote = note
        )
    }

    val attachedImageExtractedRows: List<ExtractedPriceRow> = listOf(
        // MIX Brands (1 - 19)
        row(1, "MIX_BRANDS", "MIX Brands", "Reborn", "700ml x 12", "SC 20w50", "5,105", 5105.0),
        row(2, "MIX_BRANDS", "MIX Brands", "Byco BRC", "700ml x 12", "SC 20w50", "4,907", 4907.0),
        row(3, "MIX_BRANDS", "MIX Brands", "Action BRC", "700ml x 12", "SC 20w50", "4,929", 4929.0),
        row(4, "MIX_BRANDS", "MIX Brands", "Action", "1ltr x 12", "SC 20w50", "7,075", 7075.0),
        row(5, "MIX_BRANDS", "MIX Brands", "Option 1 BRC", "700ml x 12", "SC 20w50", "4,973", 4973.0),
        row(6, "MIX_BRANDS", "MIX Brands", "Option 1", "1ltr x 12", "SC 20w50", "7,049", 7049.0),
        row(7, "MIX_BRANDS", "MIX Brands", "Option 1 White", "700ml x 12", "SC 20w50", "4,973", 4973.0),
        row(8, "MIX_BRANDS", "MIX Brands", "Power Action Tin", "700ml x 12", "SC 20w50", "5,013", 5013.0),
        row(9, "MIX_BRANDS", "MIX Brands", "Quest Round", "700ml x 12", "30 / 40", "4,735", 4735.0),
        row(10, "MIX_BRANDS", "MIX Brands", "Shandar Round", "700ml x 12", "30 / 40", "4,735", 4735.0),
        row(11, "MIX_BRANDS", "MIX Brands", "Motor Oil 40/40", "700ml x 12", "SC 20w50", "5,013", 5013.0),
        row(12, "MIX_BRANDS", "MIX Brands", "Mermaid", "700ml x 12", "SC 20w50", "4,907", 4907.0),
        row(13, "MIX_BRANDS", "MIX Brands", "ECO M5", "700ml x 12", "SC 20w50", "5,105", 5105.0),
        row(14, "MIX_BRANDS", "MIX Brands", "Oxygen Yellow", "700ml x 12", "SC 20w50", "5,105", 5105.0),
        row(15, "MIX_BRANDS", "MIX Brands", "Oxygen Black", "700ml x 12", "SC 20w50", "4,907", 4907.0),
        row(16, "MIX_BRANDS", "MIX Brands", "Sky ATF", "1ltr x 12", "Type A", "6,468", 6468.0),
        row(17, "MIX_BRANDS", "MIX Brands", "Action 2-Stroke Side Oil", "1ltr x 12", "T-2", "6,494", 6494.0),
        row(18, "MIX_BRANDS", "MIX Brands", "Sky HD50 Can", "10ltr", "HD50", "5,170", 5170.0),
        row(19, "MIX_BRANDS", "MIX Brands", "Sky HD50 Can", "20ltr", "HD50", "10,120", 10120.0),

        // M. Cycle (20 - 24)
        row(20, "M_CYCLE", "M. Cycle", "Liquix M-2000", "700ml x 12", "SL 20w50", "6,567", 6567.0),
        row(21, "M_CYCLE", "M. Cycle", "Liquix M-2000", "800ml x 12", "SL 20w50", "7,458", 7458.0),
        row(22, "M_CYCLE", "M. Cycle", "Liquix M-2000", "1ltr x 12", "SL 20w50", "9,207", 9207.0),
        row(23, "M_CYCLE", "M. Cycle", "Liquix M-2000 LOADER", "1.3 ltr x 9", "SL 20w50", "9,149", 9149.0),
        row(24, "M_CYCLE", "M. Cycle", "Liquix M-3000", "1ltr x 12", "SN 10w40", "12,243", 12243.0),

        // GASOLINE / CNG (25 - 36)
        row(25, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-1000+", "1 ltr x 12", "SG/CD 20w50", "8,165", 8165.0),
        row(26, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-1000+", "3ltr x 4", "SG/CD 20w50", "7,703", 7703.0),
        row(27, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-1000+", "4ltr x 4", "SG/CD 20w50", "9,951", 9951.0),
        row(28, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-3000+", "1ltr x 12", "SM/CF 20w50", "9,834", 9834.0),
        row(29, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-3000+", "3ltr x 4", "SM/CF 20w50", "9,570", 9570.0),
        row(30, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-3000+", "4ltr x 4", "SM/CF 20w50", "12,441", 12441.0),
        row(31, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-5000+", "1ltr x 12", "SN/CF 10w40", "12,375", 12375.0),
        row(32, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-5000+", "3ltr x 4", "SN/CF 10w40", "12,133", 12133.0),
        row(33, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-5000+", "4ltr x 4", "SN/CF 10w40", "16,313", 16313.0),
        row(34, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-7000+", "1ltr x 12", "SP 5w30", "-", null),
        row(35, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-7000+", "3ltr x 4", "SP 5w30", "-", null),
        row(36, "GASOLINE_CNG", "GASOLINE / CNG", "Liquix L-7000+", "4 ltr x 4", "SP 5w30", "-", null),

        // DIESEL (37 - 46)
        row(37, "DIESEL", "DIESEL", "Liquix D-8000+", "1 ltr x 12", "CI-4/SL 15w40", "13,101", 13101.0),
        row(38, "DIESEL", "DIESEL", "Liquix D-8000+", "4 ltr x 4", "CI-4/SL 15w40", "16,973", 16973.0),
        row(39, "DIESEL", "DIESEL", "Liquix D-8000+", "10 ltr Bucket", "CI-4/SL 15w40", "10,824", 10824.0),
        row(40, "DIESEL", "DIESEL", "Liquix D-6000+", "1 ltr x 12", "CF-4/SJ 20w50", "10,395", 10395.0),
        row(41, "DIESEL", "DIESEL", "Liquix D-6000+", "4 ltr x 4", "CF-4/SJ 20w50", "13,365", 13365.0),
        row(42, "DIESEL", "DIESEL", "Liquix D-6000+", "10 ltr Bucket", "CF-4/SJ 20w50", "8,602", 8602.0),
        row(43, "DIESEL", "DIESEL", "Liquix D-4000+", "1 ltr x 12", "CD 50", "7,907", 7907.0),
        row(44, "DIESEL", "DIESEL", "Liquix D-4000+", "4 ltr x 4", "CD 50", "10,092", 10092.0),
        row(45, "DIESEL", "DIESEL", "Liquix D-4000+", "10 ltr Bucket", "CD 50", "6,556", 6556.0),
        row(46, "DIESEL", "DIESEL", "Liquix HD50 AGRI", "5 ltr x 4", "CD 50", "12,156", 12156.0),

        // FLUIDS (47 - 50)
        row(47, "FLUIDS", "FLUIDS", "Brake Fluid DOT-3", "350 ml x 24", "DOT-3", "6,457", 6457.0),
        row(48, "FLUIDS", "FLUIDS", "Brake Fluid DOT-3", "200 ml x 24", "DOT-3", "4,597", 4597.0),
        row(49, "FLUIDS", "FLUIDS", "Radiator Coolant GREEN", "1ltr x 12", "Anti-Freeze", "5,702", 5702.0),
        row(50, "FLUIDS", "FLUIDS", "Radiator Coolant RED", "1ltr x 12", "Anti-Freeze", "5,702", 5702.0),

        // HYD. OIL (51 - 53)
        row(51, "HYD_OIL", "HYD. OIL", "Hydraulic 68# Bucket", "8ltr", "AW68", "5,951", 5951.0),
        row(52, "HYD_OIL", "HYD. OIL", "Hydraulic 68# Bucket", "10ltr", "AW68", "7,315", 7315.0),
        row(53, "HYD_OIL", "HYD. OIL", "Hydraulic 68# Bucket", "20ltr", "AW68", "13,992", 13992.0),

        // GEAR OIL (54 - 59)
        row(54, "GEAR_OIL", "GEAR OIL", "MAK Gear Oil", "1ltr x 12", "GL4 SAE 140", "7,649", 7649.0),
        row(55, "GEAR_OIL", "GEAR OIL", "MAK Gear Oil", "4ltr x 4", "GL4 SAE 140", "9,845", 9845.0),
        row(56, "GEAR_OIL", "GEAR OIL", "Gear Oil 75W-85", "1ltr x 12", "75W85", "7,722", 7722.0),
        row(57, "GEAR_OIL", "GEAR OIL", "Gear Oil 75W-85", "5ltr x 4", "75W85", "11,803", 11803.0),
        row(58, "GEAR_OIL", "GEAR OIL", "Gear Oil 75W-85", "10 Ltr", "75W85", "5,665", 5665.0),
        row(59, "GEAR_OIL", "GEAR OIL", "Gear Oil 75W-85", "20 Ltr", "75W85", "11,000", 11000.0)
    )
}
