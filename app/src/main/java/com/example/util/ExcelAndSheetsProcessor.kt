package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.ExtractedPriceRow
import com.example.data.model.GroupPrice
import com.example.data.model.Product
import com.example.data.seed.AttachedPriceListCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class ColumnMappingProfile(
    val skuColumn: String = "SKU",
    val productNameColumn: String = "ProductName",
    val categoryColumn: String = "CategoryName",
    val gradeColumn: String = "GradeViscosity",
    val packSizeColumn: String = "PackSize",
    val distributorPriceColumn: String = "Distributor_Price_PKR",
    val standardDealerPriceColumn: String = "Standard_Dealer_Price_PKR",
    val premiumDealerPriceColumn: String = "Premium_Dealer_Price_PKR",
    val specialDealerPriceColumn: String = "Special_Dealer_Price_PKR",
    val mrpColumn: String = "MRP_PKR",
    val stockColumn: String = "StockQty",
    val priceGroupCodeColumn: String = "PriceGroupCode",
    val sellingPriceColumn: String = "SellingPricePKR"
)

data class PriceTierAlignmentIssue(
    val rowNumber: Int,
    val sku: String,
    val productName: String,
    val severity: String, // "ERROR" or "WARNING"
    val ruleCode: String,
    val description: String
)

data class PriceStructureValidationReport(
    val isStructureAligned: Boolean = true,
    val hasBlockingErrors: Boolean = false,
    val alignedRowsCount: Int = 0,
    val misalignedRowsCount: Int = 0,
    val detectedPriceColumns: List<String> = emptyList(),
    val missingPriceColumns: List<String> = emptyList(),
    val issues: List<PriceTierAlignmentIssue> = emptyList()
)

data class ExcelParseSummary(
    val formatMode: String, // WIDE_PRICE_MATRIX or LONG_GROUP_PRICES
    val totalRows: Int,
    val validRows: List<ExtractedPriceRow>,
    val existingProductsCount: Int,
    val newProductsCount: Int,
    val pricesToUpdateCount: Int,
    val unchangedCount: Int,
    val duplicateSkus: List<String>,
    val invalidRows: List<String>,
    val unknownPriceGroups: List<String>,
    val detectedColumns: List<String>,
    val appliedMapping: ColumnMappingProfile,
    val priceStructureValidation: PriceStructureValidationReport = PriceStructureValidationReport()
)

object ExcelAndSheetsProcessor {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Common column aliases per Section 9.B of the Master Prompt
    private val skuAliases = setOf("sku", "product code", "item code", "product id", "code", "s. no.", "s.no", "sno")
    private val nameAliases = setOf("productname", "product name", "item name", "product", "item", "description")
    private val dealerPriceAliases = setOf("standard_dealer_price_pkr", "dealer price", "dealer rate", "d-price", "standard dealer")
    private val premiumDealerAliases = setOf("premium_dealer_price_pkr", "premium dealer", "premium rate", "p-price")
    private val distributorPriceAliases = setOf("distributor_price_pkr", "distributor price", "distributor rate", "dist rate", "sole distributor", "ole distributo")
    private val specialDealerAliases = setOf("special_dealer_price_pkr", "special dealer", "special rate", "s-price")
    private val packingAliases = setOf("packsize", "packing", "pack size", "package size")
    private val gradeAliases = setOf("gradeviscosity", "grade", "viscosity", "grade / colour", "grade/colour")
    private val categoryAliases = setOf("categoryname", "category", "product category", "product type")
    private val mrpAliases = setOf("mrp_pkr", "mrp", "retail price")
    private val stockAliases = setOf("stockqty", "stock", "qty", "inventory")
    private val groupCodeAliases = setOf("pricegroupcode", "price group", "group code", "group")
    private val sellingPriceAliases = setOf("sellingpricepkr", "selling price", "price", "rate")

    fun autoDetectColumnMapping(headers: List<String>): ColumnMappingProfile {
        fun matchHeader(aliases: Set<String>, fallback: String): String {
            return headers.firstOrNull { h ->
                aliases.contains(h.trim().lowercase())
            } ?: fallback
        }
        return ColumnMappingProfile(
            skuColumn = matchHeader(skuAliases, "SKU"),
            productNameColumn = matchHeader(nameAliases, "ProductName"),
            categoryColumn = matchHeader(categoryAliases, "CategoryName"),
            gradeColumn = matchHeader(gradeAliases, "GradeViscosity"),
            packSizeColumn = matchHeader(packingAliases, "PackSize"),
            distributorPriceColumn = matchHeader(distributorPriceAliases, "Distributor_Price_PKR"),
            standardDealerPriceColumn = matchHeader(dealerPriceAliases, "Standard_Dealer_Price_PKR"),
            premiumDealerPriceColumn = matchHeader(premiumDealerAliases, "Premium_Dealer_Price_PKR"),
            specialDealerPriceColumn = matchHeader(specialDealerAliases, "Special_Dealer_Price_PKR"),
            mrpColumn = matchHeader(mrpAliases, "MRP_PKR"),
            stockColumn = matchHeader(stockAliases, "StockQty"),
            priceGroupCodeColumn = matchHeader(groupCodeAliases, "PriceGroupCode"),
            sellingPriceColumn = matchHeader(sellingPriceAliases, "SellingPricePKR")
        )
    }

    /**
     * Parses CSV, TSV, or Excel XML Workbook (`.xls`) text in either WIDE_PRICE_MATRIX or LONG_GROUP_PRICES format,
     * performing automatic SKU matching, duplicate detection, old-vs-new price comparison, and
     * price structure alignment validation across all distributor/dealer tiers.
     */
    fun parseDelimitedPriceList(
        rawContent: String,
        formatMode: String,
        existingProducts: List<Product>,
        existingPricesBySkuAndGroup: Map<Pair<String, String>, Double>,
        knownPriceGroups: Set<String>
    ): ExcelParseSummary {
        val normalizedContent = convertExcelXmlToCsvIfNeeded(rawContent, formatMode)
        val lines = normalizedContent.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.isEmpty()) {
            return ExcelParseSummary(
                formatMode = formatMode,
                totalRows = 0,
                validRows = emptyList(),
                existingProductsCount = 0,
                newProductsCount = 0,
                pricesToUpdateCount = 0,
                unchangedCount = 0,
                duplicateSkus = emptyList(),
                invalidRows = listOf("File is empty"),
                unknownPriceGroups = emptyList(),
                detectedColumns = emptyList(),
                appliedMapping = ColumnMappingProfile(),
                priceStructureValidation = PriceStructureValidationReport(
                    isStructureAligned = false,
                    hasBlockingErrors = true,
                    issues = listOf(
                        PriceTierAlignmentIssue(
                            rowNumber = 1,
                            sku = "N/A",
                            productName = "Header",
                            severity = "ERROR",
                            ruleCode = "EMPTY_FILE",
                            description = "Uploaded CSV/Excel file contains no data rows."
                        )
                    )
                )
            )
        }

        val delimiter = if (lines.first().contains("\t")) '\t' else ','
        val headers = splitCsvLine(lines.first(), delimiter)
        val mapping = autoDetectColumnMapping(headers)
        val headerIndex = headers.mapIndexed { idx, h -> h.trim().lowercase() to idx }.toMap()

        fun getCol(cols: List<String>, colName: String, aliases: Set<String>): String {
            val directIdx = headerIndex[colName.lowercase()]
                ?: headerIndex.entries.firstOrNull { aliases.contains(it.key) }?.value
            return if (directIdx != null && directIdx in cols.indices) cols[directIdx].trim() else ""
        }

        val existingBySku = existingProducts.associateBy { it.sku.uppercase() }
        val existingByComposite = existingProducts.associateBy {
            "${it.productName.lowercase()}|${it.grade.lowercase()}|${it.packagingSize.lowercase()}"
        }

        val seenSkus = mutableSetOf<String>()
        val seenSkuGroups = mutableSetOf<String>()
        val duplicateSkus = mutableListOf<String>()
        val invalidRows = mutableListOf<String>()
        val unknownPriceGroups = mutableListOf<String>()
        val parsedRows = mutableListOf<ExtractedPriceRow>()

        var existingFound = 0
        var newFound = 0
        var pricesToUpdate = 0
        var unchanged = 0

        for (i in 1 until lines.size) {
            val rowNum = i + 1
            val cols = splitCsvLine(lines[i], delimiter)
            if (cols.all { it.isBlank() }) continue

            val rawSku = getCol(cols, mapping.skuColumn, skuAliases)
            val rawName = getCol(cols, mapping.productNameColumn, nameAliases)
            val rawCat = getCol(cols, mapping.categoryColumn, categoryAliases).ifBlank { "MIX Brands" }
            val rawGrade = getCol(cols, mapping.gradeColumn, gradeAliases).ifBlank { "Standard" }
            val rawPack = getCol(cols, mapping.packSizeColumn, packingAliases).ifBlank { "1ltr x 12" }

            // Candidate matching if SKU is missing
            val matchedExisting = if (rawSku.isNotBlank()) {
                existingBySku[rawSku.uppercase()]
            } else {
                existingByComposite["${rawName.lowercase()}|${rawGrade.lowercase()}|${rawPack.lowercase()}"]
            }

            val finalSku = when {
                rawSku.isNotBlank() -> rawSku.uppercase()
                matchedExisting != null -> matchedExisting.sku
                else -> "SNL-NEW-%03d".format(rowNum)
            }

            val finalName = rawName.ifBlank { matchedExisting?.productName ?: "" }
            if (finalName.isBlank()) {
                invalidRows.add("Row $rowNum ($finalSku): Missing Product Name")
                continue
            }

            if (formatMode == "LONG_GROUP_PRICES") {
                val groupCode = getCol(cols, mapping.priceGroupCodeColumn, groupCodeAliases).uppercase().ifBlank { "DISTRIBUTOR" }
                val key = "$finalSku|$groupCode"
                if (!seenSkuGroups.add(key)) {
                    duplicateSkus.add("Row $rowNum: Duplicate SKU+Group ($key)")
                    continue
                }
                if (knownPriceGroups.isNotEmpty() && !knownPriceGroups.contains(groupCode)) {
                    unknownPriceGroups.add("Row $rowNum: Unknown PriceGroup '$groupCode'")
                }
                val priceStr = getCol(cols, mapping.sellingPriceColumn, sellingPriceAliases).replace(",", "")
                val priceVal = priceStr.toDoubleOrNull()
                if (priceStr.isNotBlank() && priceStr != "-" && (priceVal == null || priceVal < 0.0)) {
                    invalidRows.add("Row $rowNum ($finalSku): Invalid price '$priceStr'")
                    continue
                }
                val oldPrice = existingPricesBySkuAndGroup[finalSku to groupCode]
                if (matchedExisting != null) existingFound++ else newFound++
                if (priceVal != null && priceVal > 0 && priceVal != oldPrice) pricesToUpdate++ else unchanged++

                parsedRows.add(
                    ExtractedPriceRow(
                        serialNo = i,
                        sku = finalSku,
                        productName = finalName,
                        categoryCode = rawCat.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_'),
                        categoryName = rawCat,
                        packagingSize = rawPack,
                        packagingUnit = if (rawPack.lowercase().contains("bucket")) "Bucket" else "Carton",
                        cartonQty = rawPack.substringAfter("x", "1").trim().toIntOrNull() ?: 1,
                        grade = rawGrade,
                        rawPriceText = priceStr.ifBlank { "-" },
                        distributorPricePkr = if (groupCode == "DISTRIBUTOR") priceVal else null,
                        standardDealerPricePkr = if (groupCode == "STANDARD_DEALER") priceVal else null,
                        premiumDealerPricePkr = if (groupCode == "PREMIUM_DEALER") priceVal else null,
                        specialDealerPricePkr = if (groupCode == "SPECIAL_DEALER") priceVal else null,
                        isUncertainOrMissingPrice = priceVal == null || rawSku.isBlank(),
                        uncertaintyNote = if (rawSku.isBlank()) "SKU was missing; candidate matched by Name+Grade+Packing." else "",
                        isExistingProduct = matchedExisting != null,
                        oldPricePkr = oldPrice
                    )
                )
            } else {
                // WIDE_PRICE_MATRIX
                if (!seenSkus.add(finalSku)) {
                    duplicateSkus.add("Row $rowNum: Duplicate SKU '$finalSku'")
                    continue
                }

                fun parseOptionalPrice(raw: String, label: String): Double? {
                    val cleaned = raw.replace(",", "").trim()
                    if (cleaned.isBlank() || cleaned == "-") return null // Empty cells mean NO CHANGE
                    val v = cleaned.toDoubleOrNull()
                    if (v == null || v < 0.0) {
                        invalidRows.add("Row $rowNum ($finalSku): Invalid $label '$raw'")
                        return null
                    }
                    return v
                }

                val distRaw = getCol(cols, mapping.distributorPriceColumn, distributorPriceAliases)
                val stdRaw = getCol(cols, mapping.standardDealerPriceColumn, dealerPriceAliases)
                val premRaw = getCol(cols, mapping.premiumDealerPriceColumn, premiumDealerAliases)
                val specRaw = getCol(cols, mapping.specialDealerPriceColumn, specialDealerAliases)
                val mrpRaw = getCol(cols, mapping.mrpColumn, mrpAliases)
                val stockRaw = getCol(cols, mapping.stockColumn, stockAliases)

                val distPrice = parseOptionalPrice(distRaw, "Distributor Price")
                val stdPrice = parseOptionalPrice(stdRaw, "Standard Dealer Price")
                val premPrice = parseOptionalPrice(premRaw, "Premium Dealer Price")
                val specPrice = parseOptionalPrice(specRaw, "Special Dealer Price")
                val mrpPrice = parseOptionalPrice(mrpRaw, "MRP")
                val stockVal = stockRaw.replace(",", "").trim().toIntOrNull()

                val oldDistPrice = existingPricesBySkuAndGroup[finalSku to "DISTRIBUTOR"]
                if (matchedExisting != null) existingFound++ else newFound++
                val anyUpdated = (distPrice != null && distPrice != oldDistPrice) ||
                    stdPrice != null || premPrice != null || specPrice != null
                if (anyUpdated) pricesToUpdate++ else unchanged++

                parsedRows.add(
                    ExtractedPriceRow(
                        serialNo = i,
                        sku = finalSku,
                        productName = finalName,
                        categoryCode = rawCat.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_'),
                        categoryName = rawCat,
                        packagingSize = rawPack,
                        packagingUnit = if (rawPack.lowercase().contains("bucket")) "Bucket" else "Carton",
                        cartonQty = rawPack.substringAfter("x", "1").trim().toIntOrNull() ?: 1,
                        grade = rawGrade,
                        rawPriceText = distRaw.ifBlank { stdRaw.ifBlank { "-" } },
                        distributorPricePkr = distPrice,
                        standardDealerPricePkr = stdPrice,
                        premiumDealerPricePkr = premPrice,
                        specialDealerPricePkr = specPrice,
                        mrpPkr = mrpPrice,
                        stockQty = stockVal,
                        isUncertainOrMissingPrice = (distPrice == null && stdPrice == null && premPrice == null && specPrice == null) || rawSku.isBlank(),
                        uncertaintyNote = if (rawSku.isBlank()) "SKU missing; matched by Name+Grade+Packing" else if (distPrice == null && stdPrice == null) "No price value in row (NO CHANGE)" else "",
                        isExistingProduct = matchedExisting != null,
                        oldPricePkr = oldDistPrice
                    )
                )
            }
        }

        val validationReport = validatePriceStructureAlignment(
            formatMode = formatMode,
            headers = headers,
            parsedRows = parsedRows,
            unknownPriceGroups = unknownPriceGroups.distinct(),
            invalidRows = invalidRows
        )

        return ExcelParseSummary(
            formatMode = formatMode,
            totalRows = lines.size - 1,
            validRows = parsedRows,
            existingProductsCount = existingFound,
            newProductsCount = newFound,
            pricesToUpdateCount = pricesToUpdate,
            unchangedCount = unchanged,
            duplicateSkus = duplicateSkus,
            invalidRows = invalidRows,
            unknownPriceGroups = unknownPriceGroups.distinct(),
            detectedColumns = headers,
            appliedMapping = mapping,
            priceStructureValidation = validationReport
        )
    }

    /**
     * Validates that the imported CSV/Excel file aligns with SNL's B2B multi-tier pricing structure:
     * 1. Expected price tier columns are present (`Distributor_Price_PKR`, `Standard_Dealer_Price_PKR`, `Premium_Dealer_Price_PKR`, `Special_Dealer_Price_PKR`, `MRP_PKR`).
     * 2. Tier price hierarchy holds (`Distributor Price <= Premium Dealer Price <= Standard Dealer Price <= MRP`).
     * 3. No negative/zero invalid prices or unrecognized price group codes exist.
     * 4. Flags large single-import price swings (>50% variance vs existing price) for admin review.
     */
    fun validatePriceStructureAlignment(
        formatMode: String,
        headers: List<String>,
        parsedRows: List<ExtractedPriceRow>,
        unknownPriceGroups: List<String>,
        invalidRows: List<String>
    ): PriceStructureValidationReport {
        val lowerHeaders = headers.map { it.trim().lowercase() }.toSet()
        val issues = mutableListOf<PriceTierAlignmentIssue>()

        val expectedTierColumns = if (formatMode == "LONG_GROUP_PRICES") {
            listOf(
                "PriceGroupCode" to groupCodeAliases,
                "SellingPricePKR" to sellingPriceAliases
            )
        } else {
            listOf(
                "Distributor_Price_PKR" to distributorPriceAliases,
                "Standard_Dealer_Price_PKR" to dealerPriceAliases,
                "Premium_Dealer_Price_PKR" to premiumDealerAliases,
                "Special_Dealer_Price_PKR" to specialDealerAliases,
                "MRP_PKR" to mrpAliases
            )
        }

        val detectedPriceCols = mutableListOf<String>()
        val missingPriceCols = mutableListOf<String>()
        for ((canonicalName, aliases) in expectedTierColumns) {
            if (lowerHeaders.any { aliases.contains(it) }) {
                detectedPriceCols.add(canonicalName)
            } else {
                missingPriceCols.add(canonicalName)
            }
        }

        if (detectedPriceCols.isEmpty()) {
            issues.add(
                PriceTierAlignmentIssue(
                    rowNumber = 1,
                    sku = "HEADER",
                    productName = "Column Structure",
                    severity = "ERROR",
                    ruleCode = "MISSING_REQUIRED_PRICE_COLUMN",
                    description = "No recognized SNL price tier columns found in header (${headers.joinToString(", ")})."
                )
            )
        }

        unknownPriceGroups.forEach { unknownMsg ->
            issues.add(
                PriceTierAlignmentIssue(
                    rowNumber = 0,
                    sku = "GROUP",
                    productName = "Price Group Alignment",
                    severity = "ERROR",
                    ruleCode = "UNKNOWN_PRICE_GROUP",
                    description = unknownMsg
                )
            )
        }

        invalidRows.forEach { invalidMsg ->
            issues.add(
                PriceTierAlignmentIssue(
                    rowNumber = 0,
                    sku = "ROW",
                    productName = "Row Format Validation",
                    severity = "ERROR",
                    ruleCode = "INVALID_PRICE_FORMAT",
                    description = invalidMsg
                )
            )
        }

        var alignedCount = 0
        var misalignedCount = 0

        for (row in parsedRows) {
            var rowHasIssue = false
            val dist = row.distributorPricePkr
            val std = row.standardDealerPricePkr
            val prem = row.premiumDealerPricePkr
            val spec = row.specialDealerPricePkr
            val mrp = row.mrpPkr

            // Rule 1: Distributor price should not exceed Standard Dealer price
            if (dist != null && std != null && dist > std + 0.01) {
                rowHasIssue = true
                issues.add(
                    PriceTierAlignmentIssue(
                        rowNumber = row.serialNo,
                        sku = row.sku,
                        productName = row.productName,
                        severity = "ERROR",
                        ruleCode = "DISTRIBUTOR_EXCEEDS_STANDARD_DEALER",
                        description = "Distributor price (PKR ${dist.toLong()}) is higher than Standard Dealer price (PKR ${std.toLong()})."
                    )
                )
            }

            // Rule 2: Distributor price should not exceed Premium Dealer price
            if (dist != null && prem != null && dist > prem + 0.01) {
                rowHasIssue = true
                issues.add(
                    PriceTierAlignmentIssue(
                        rowNumber = row.serialNo,
                        sku = row.sku,
                        productName = row.productName,
                        severity = "WARNING",
                        ruleCode = "DISTRIBUTOR_EXCEEDS_PREMIUM_DEALER",
                        description = "Distributor price (PKR ${dist.toLong()}) exceeds Premium Dealer price (PKR ${prem.toLong()})."
                    )
                )
            }

            // Rule 3: Premium Dealer price should not exceed Standard Dealer price
            if (prem != null && std != null && prem > std + 0.01) {
                rowHasIssue = true
                issues.add(
                    PriceTierAlignmentIssue(
                        rowNumber = row.serialNo,
                        sku = row.sku,
                        productName = row.productName,
                        severity = "ERROR",
                        ruleCode = "PREMIUM_EXCEEDS_STANDARD_DEALER",
                        description = "Premium Dealer price (PKR ${prem.toLong()}) is higher than Standard Dealer price (PKR ${std.toLong()})."
                    )
                )
            }

            // Rule 4: No wholesale tier price should exceed MRP
            if (mrp != null && mrp > 0.0) {
                val maxWholesale = listOfNotNull(dist, std, prem, spec).maxOrNull() ?: 0.0
                if (maxWholesale > mrp + 0.01) {
                    rowHasIssue = true
                    issues.add(
                        PriceTierAlignmentIssue(
                            rowNumber = row.serialNo,
                            sku = row.sku,
                            productName = row.productName,
                            severity = "ERROR",
                            ruleCode = "TIER_EXCEEDS_MRP",
                            description = "Wholesale tier price (PKR ${maxWholesale.toLong()}) exceeds retail MRP (PKR ${mrp.toLong()})."
                        )
                    )
                }
            }

            // Rule 5: Large price swing warning (>50% change vs existing price)
            val oldPrice = row.oldPricePkr
            if (oldPrice != null && oldPrice > 0.0 && dist != null && dist > 0.0) {
                val changeRatio = kotlin.math.abs(dist - oldPrice) / oldPrice
                if (changeRatio > 0.50) {
                    rowHasIssue = true
                    val pct = (changeRatio * 100).toInt()
                    issues.add(
                        PriceTierAlignmentIssue(
                            rowNumber = row.serialNo,
                            sku = row.sku,
                            productName = row.productName,
                            severity = "WARNING",
                            ruleCode = "LARGE_PRICE_VARIANCE",
                            description = "Price changed by $pct% (PKR ${oldPrice.toLong()} → PKR ${dist.toLong()}). Verify before committing."
                        )
                    )
                }
            }

            if (rowHasIssue) {
                misalignedCount++
            } else {
                alignedCount++
            }
        }

        val hasBlocking = issues.any { it.severity == "ERROR" }
        return PriceStructureValidationReport(
            isStructureAligned = issues.isEmpty(),
            hasBlockingErrors = hasBlocking,
            alignedRowsCount = alignedCount,
            misalignedRowsCount = misalignedCount,
            detectedPriceColumns = detectedPriceCols,
            missingPriceColumns = missingPriceCols,
            issues = issues
        )
    }

    /**
     * Converts an Excel 2003 XML Workbook (`.xls` generated by SNL or exported from Google Drive)
     * into CSV text so it can be parsed and validated identically to `.csv` files.
     */
    fun convertExcelXmlToCsvIfNeeded(rawContent: String, formatMode: String): String {
        val trimmed = rawContent.trimStart()
        if (!trimmed.startsWith("<?xml", ignoreCase = true) && !trimmed.contains("<Workbook", ignoreCase = true)) {
            return rawContent
        }

        val worksheetRegex = Regex("""<Worksheet[^>]*ss:Name="([^"]+)"[^>]*>(.*?)</Worksheet>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val rowRegex = Regex("""<Row[^>]*>(.*?)</Row>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val dataRegex = Regex("""<Cell[^>]*>.*?<Data[^>]*>(.*?)</Data>.*?</Cell>|<Cell[^>]*/>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        val sheets = worksheetRegex.findAll(trimmed).associate { match ->
            match.groupValues[1].trim() to match.groupValues[2]
        }

        fun extractRowsFromSheet(sheetXml: String): List<List<String>> {
            return rowRegex.findAll(sheetXml).map { rowMatch ->
                dataRegex.findAll(rowMatch.groupValues[1]).map { cellMatch ->
                    unescapeXml(cellMatch.groupValues[1].trim())
                }.toList()
            }.filter { it.isNotEmpty() }.toList()
        }

        if (formatMode == "LONG_GROUP_PRICES" && sheets.containsKey("Group Prices")) {
            val groupRows = extractRowsFromSheet(sheets.getValue("Group Prices"))
            return groupRows.joinToString("\n") { row ->
                row.joinToString(",") { cell -> if (cell.contains(",")) "\"$cell\"" else cell }
            }
        }

        val productsSheet = sheets["Products"]
        val priceMatrixSheet = sheets["Price Matrix"]
        if (productsSheet != null && priceMatrixSheet != null) {
            val prodRows = extractRowsFromSheet(productsSheet)
            val matrixRows = extractRowsFromSheet(priceMatrixSheet)
            val prodBySku = prodRows.drop(1).associateBy { it.firstOrNull().orEmpty().uppercase() }

            return buildString {
                appendLine("SKU,ProductName,CategoryName,GradeViscosity,PackSize,Distributor_Price_PKR,Standard_Dealer_Price_PKR,Premium_Dealer_Price_PKR,Special_Dealer_Price_PKR,MRP_PKR,MinimumOrderQty,EffectiveFrom,IsActive")
                for (mRow in matrixRows.drop(1)) {
                    val sku = mRow.getOrNull(0).orEmpty()
                    if (sku.isBlank()) continue
                    val pRow = prodBySku[sku.uppercase()]
                    val pName = pRow?.getOrNull(1).orEmpty().ifBlank { sku }
                    val catName = pRow?.getOrNull(2).orEmpty().ifBlank { "MIX Brands" }
                    val grade = pRow?.getOrNull(3).orEmpty().ifBlank { "Standard" }
                    val pack = pRow?.getOrNull(4).orEmpty().ifBlank { "1ltr x 12" }
                    val stdPrice = mRow.getOrNull(1).orEmpty()
                    val premPrice = mRow.getOrNull(2).orEmpty()
                    val distPrice = mRow.getOrNull(3).orEmpty()
                    val specPrice = mRow.getOrNull(4).orEmpty()
                    val mrpPrice = mRow.getOrNull(5).orEmpty()
                    val minQty = mRow.getOrNull(6).orEmpty().ifBlank { "1" }
                    val effFrom = mRow.getOrNull(7).orEmpty().ifBlank { "2026-11-03" }
                    val isAct = mRow.getOrNull(9).orEmpty().ifBlank { "TRUE" }
                    appendLine("$sku,\"$pName\",\"$catName\",\"$grade\",\"$pack\",$distPrice,$stdPrice,$premPrice,$specPrice,$mrpPrice,$minQty,$effFrom,$isAct")
                }
            }
        }

        // Fallback: convert the first data worksheet found
        val firstSheetXml = sheets.entries.firstOrNull { !it.key.equals("START HERE", ignoreCase = true) }?.value
            ?: sheets.values.firstOrNull()
            ?: return rawContent
        return extractRowsFromSheet(firstSheetXml).joinToString("\n") { row ->
            row.joinToString(",") { cell -> if (cell.contains(",")) "\"$cell\"" else cell }
        }
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString().trim())
        return result
    }

    /**
     * Generates and shares the official SNL Excel Multi-Sheet Workbook Template
     * containing all 6 mandatory worksheets:
     * 1. START HERE
     * 2. Products
     * 3. Price Matrix (Wide Format)
     * 4. Group Prices (Long Format)
     * 5. Price Groups
     * 6. Inventory
     * Pre-populated with the actual 59 SNL products from the attached price list!
     */
    fun generateAndShareOfficialExcelTemplate(context: Context): Result<File> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            // Generate both a multi-sheet Excel XML Workbook (.xls/.xml) AND a ready-to-import Price Matrix CSV
            val workbookFile = File(exportDir, "SNL_Official_Price_List_Template_2026.xls")
            val rows = AttachedPriceListCatalog.attachedImageExtractedRows

            val xml = buildString {
                appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
                appendLine("""<?mso-application progid="Excel.Sheet"?>""")
                appendLine("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""")

                // Sheet 1: START HERE
                appendLine("""<Worksheet ss:Name="START HERE"><Table>""")
                appendLine("""<Row><Cell><Data ss:Type="String">SAIFI NATIONAL LUBRICANTS (SNL) — OFFICIAL EXCEL PRICE LIST &amp; INVENTORY WORKBOOK</Data></Cell></Row>""")
                appendLine("""<Row><Cell><Data ss:Type="String">Instructions: Empty price cells mean NO CHANGE. Blank prices never overwrite existing prices with zero. All prices in PKR.</Data></Cell></Row>""")
                appendLine("""<Row><Cell><Data ss:Type="String">Sheets included: 1. START HERE | 2. Products | 3. Price Matrix (Wide) | 4. Group Prices (Long) | 5. Price Groups | 6. Inventory</Data></Cell></Row>""")
                appendLine("""</Table></Worksheet>""")

                // Sheet 2: Products
                appendLine("""<Worksheet ss:Name="Products"><Table>""")
                appendLine("""<Row><Cell><Data ss:Type="String">SKU</Data></Cell><Cell><Data ss:Type="String">ProductName</Data></Cell><Cell><Data ss:Type="String">CategoryName</Data></Cell><Cell><Data ss:Type="String">GradeViscosity</Data></Cell><Cell><Data ss:Type="String">PackSize</Data></Cell><Cell><Data ss:Type="String">PackUnit</Data></Cell><Cell><Data ss:Type="String">Description</Data></Cell><Cell><Data ss:Type="String">ImageURL</Data></Cell><Cell><Data ss:Type="String">IsActive</Data></Cell></Row>""")
                for (r in rows) {
                    appendLine("""<Row><Cell><Data ss:Type="String">${r.sku}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(r.productName)}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(r.categoryName)}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(r.grade)}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(r.packagingSize)}</Data></Cell><Cell><Data ss:Type="String">${r.packagingUnit}</Data></Cell><Cell><Data ss:Type="String">SNL ${escapeXml(r.productName)} ${escapeXml(r.grade)}</Data></Cell><Cell><Data ss:Type="String"></Data></Cell><Cell><Data ss:Type="String">TRUE</Data></Cell></Row>""")
                }
                appendLine("""</Table></Worksheet>""")

                // Sheet 3: Price Matrix (Wide Format)
                appendLine("""<Worksheet ss:Name="Price Matrix"><Table>""")
                appendLine("""<Row><Cell><Data ss:Type="String">SKU</Data></Cell><Cell><Data ss:Type="String">Standard_Dealer_Price_PKR</Data></Cell><Cell><Data ss:Type="String">Premium_Dealer_Price_PKR</Data></Cell><Cell><Data ss:Type="String">Distributor_Price_PKR</Data></Cell><Cell><Data ss:Type="String">Special_Dealer_Price_PKR</Data></Cell><Cell><Data ss:Type="String">MRP_PKR</Data></Cell><Cell><Data ss:Type="String">MinimumOrderQty</Data></Cell><Cell><Data ss:Type="String">EffectiveFrom</Data></Cell><Cell><Data ss:Type="String">EffectiveUntil</Data></Cell><Cell><Data ss:Type="String">IsActive</Data></Cell></Row>""")
                for (r in rows) {
                    val distStr = r.distributorPricePkr?.toInt()?.toString() ?: ""
                    appendLine("""<Row><Cell><Data ss:Type="String">${r.sku}</Data></Cell><Cell><Data ss:Type="String"></Data></Cell><Cell><Data ss:Type="String"></Data></Cell><Cell><Data ss:Type="String">$distStr</Data></Cell><Cell><Data ss:Type="String"></Data></Cell><Cell><Data ss:Type="String"></Data></Cell><Cell><Data ss:Type="Number">1</Data></Cell><Cell><Data ss:Type="String">2026-11-03</Data></Cell><Cell><Data ss:Type="String"></Data></Cell><Cell><Data ss:Type="String">TRUE</Data></Cell></Row>""")
                }
                appendLine("""</Table></Worksheet>""")

                // Sheet 4: Group Prices (Long Format)
                appendLine("""<Worksheet ss:Name="Group Prices"><Table>""")
                appendLine("""<Row><Cell><Data ss:Type="String">SKU</Data></Cell><Cell><Data ss:Type="String">PriceGroupCode</Data></Cell><Cell><Data ss:Type="String">SellingPricePKR</Data></Cell><Cell><Data ss:Type="String">MRP_PKR</Data></Cell><Cell><Data ss:Type="String">MinimumOrderQty</Data></Cell><Cell><Data ss:Type="String">EffectiveFrom</Data></Cell><Cell><Data ss:Type="String">EffectiveUntil</Data></Cell><Cell><Data ss:Type="String">IsActive</Data></Cell></Row>""")
                for (r in rows) {
                    val distStr = r.distributorPricePkr?.toInt()?.toString() ?: ""
                    appendLine("""<Row><Cell><Data ss:Type="String">${r.sku}</Data></Cell><Cell><Data ss:Type="String">DISTRIBUTOR</Data></Cell><Cell><Data ss:Type="String">$distStr</Data></Cell><Cell><Data ss:Type="String"></Data></Cell><Cell><Data ss:Type="Number">1</Data></Cell><Cell><Data ss:Type="String">2026-11-03</Data></Cell><Cell><Data ss:Type="String"></Data></Cell><Cell><Data ss:Type="String">TRUE</Data></Cell></Row>""")
                }
                appendLine("""</Table></Worksheet>""")

                // Sheet 5: Price Groups
                appendLine("""<Worksheet ss:Name="Price Groups"><Table>""")
                appendLine("""<Row><Cell><Data ss:Type="String">PriceGroupCode</Data></Cell><Cell><Data ss:Type="String">PriceGroupName</Data></Cell><Cell><Data ss:Type="String">Description</Data></Cell><Cell><Data ss:Type="String">IsActive</Data></Cell></Row>""")
                for (g in AttachedPriceListCatalog.initialPriceGroups) {
                    appendLine("""<Row><Cell><Data ss:Type="String">${g.groupCode}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(g.groupName)}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(g.description)}</Data></Cell><Cell><Data ss:Type="String">TRUE</Data></Cell></Row>""")
                }
                appendLine("""</Table></Worksheet>""")

                // Sheet 6: Inventory
                appendLine("""<Worksheet ss:Name="Inventory"><Table>""")
                appendLine("""<Row><Cell><Data ss:Type="String">SKU</Data></Cell><Cell><Data ss:Type="String">StockQty</Data></Cell><Cell><Data ss:Type="String">SyncStock</Data></Cell><Cell><Data ss:Type="String">StockNote</Data></Cell></Row>""")
                for (r in rows) {
                    appendLine("""<Row><Cell><Data ss:Type="String">${r.sku}</Data></Cell><Cell><Data ss:Type="Number">100</Data></Cell><Cell><Data ss:Type="String">FALSE</Data></Cell><Cell><Data ss:Type="String">Initial Warehouse Stock</Data></Cell></Row>""")
                }
                appendLine("""</Table></Worksheet>""")
                appendLine("""</Workbook>""")
            }

            workbookFile.writeText(xml)
            shareFile(context, workbookFile, "application/vnd.ms-excel", "Download / Share SNL Excel Template")
            Result.success(workbookFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateDefaultWideCsvContent(): String = buildString {
        appendLine("SKU,ProductName,CategoryName,GradeViscosity,PackSize,Distributor_Price_PKR,Standard_Dealer_Price_PKR,Premium_Dealer_Price_PKR,Special_Dealer_Price_PKR,MRP_PKR,StockQty,MinimumOrderQty,EffectiveFrom,IsActive")
        for (r in AttachedPriceListCatalog.attachedImageExtractedRows) {
            val dist = r.distributorPricePkr?.toInt()?.toString() ?: ""
            appendLine("${r.sku},\"${r.productName}\",\"${r.categoryName}\",\"${r.grade}\",\"${r.packagingSize}\",$dist,,,,,100,1,2026-11-03,TRUE")
        }
    }

    /**
     * Generates live CSV export content from the current Firestore `products` and `groupPrices`
     * (or falls back to the 59 SNL products if Firestore is empty), aligned with the SNL Wide Price Matrix schema.
     */
    fun generateLiveCatalogCsvContent(
        products: List<Product>,
        groupPrices: List<GroupPrice>
    ): String {
        if (products.isEmpty()) return generateDefaultWideCsvContent()
        val priceLookup = groupPrices.associate {
            (it.sku.uppercase() to it.priceGroupCode.uppercase()) to it.sellingPricePkr
        }
        return buildString {
            appendLine("SKU,ProductName,CategoryName,GradeViscosity,PackSize,Distributor_Price_PKR,Standard_Dealer_Price_PKR,Premium_Dealer_Price_PKR,Special_Dealer_Price_PKR,MRP_PKR,StockQty,MinimumOrderQty,EffectiveFrom,IsActive")
            for (p in products.sortedBy { it.serialNo }) {
                val skuKey = p.sku.uppercase()
                val dist = (priceLookup[skuKey to "DISTRIBUTOR"] ?: p.priceForGroup("DISTRIBUTOR"))
                    ?.takeIf { it > 0.0 }?.toLong()?.toString() ?: ""
                val std = (priceLookup[skuKey to "STANDARD_DEALER"] ?: p.priceForGroup("STANDARD_DEALER"))
                    ?.takeIf { it > 0.0 }?.toLong()?.toString() ?: ""
                val prem = (priceLookup[skuKey to "PREMIUM_DEALER"] ?: p.priceForGroup("PREMIUM_DEALER"))
                    ?.takeIf { it > 0.0 }?.toLong()?.toString() ?: ""
                val spec = (priceLookup[skuKey to "SPECIAL_DEALER"] ?: p.priceForGroup("SPECIAL_DEALER"))
                    ?.takeIf { it > 0.0 }?.toLong()?.toString() ?: ""
                val mrp = p.mrpPkr.takeIf { it > 0.0 }?.toLong()?.toString() ?: ""
                appendLine(
                    "${p.sku},\"${p.productName.replace("\"", "'")}\",\"${p.categoryName.replace("\"", "'")}\",\"${p.grade.replace("\"", "'")}\",\"${p.packagingSize.replace("\"", "'")}\",$dist,$std,$prem,$spec,$mrp,${p.stockQty},${p.minOrderQty},2026-11-03,${if (p.isActive) "TRUE" else "FALSE"}"
                )
            }
        }
    }

    /**
     * Generates a multi-sheet Excel XML Workbook (`.xls`) from the live Firestore `products` and `groupPrices`
     * ready to save or share directly to Google Drive.
     */
    fun generateLiveCatalogExcelXmlContent(
        products: List<Product>,
        groupPrices: List<GroupPrice>
    ): String {
        val priceLookup = groupPrices.associate {
            (it.sku.uppercase() to it.priceGroupCode.uppercase()) to it.sellingPricePkr
        }
        val activeProducts = if (products.isNotEmpty()) {
            products.sortedBy { it.serialNo }
        } else {
            AttachedPriceListCatalog.attachedImageExtractedRows.map { r ->
                Product(
                    id = r.sku,
                    sku = r.sku,
                    serialNo = r.serialNo.toLong(),
                    name = r.productName,
                    productName = r.productName,
                    category = r.categoryName,
                    categoryId = r.categoryCode,
                    categoryName = r.categoryName,
                    priceTiers = r.distributorPricePkr?.let { mapOf("DISTRIBUTOR" to it) } ?: emptyMap(),
                    grade = r.grade,
                    packagingSize = r.packagingSize,
                    packagingUnit = r.packagingUnit,
                    cartonQty = r.cartonQty.toLong(),
                    minOrderQty = 1L,
                    stockQty = 100L,
                    isActive = true
                )
            }
        }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<?mso-application progid="Excel.Sheet"?>""")
            appendLine("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""")

            appendLine("""<Worksheet ss:Name="START HERE"><Table>""")
            appendLine("""<Row><Cell><Data ss:Type="String">SAIFI NATIONAL LUBRICANTS (SNL) — GOOGLE DRIVE LIVE CATALOG &amp; PRICE MATRIX EXPORT</Data></Cell></Row>""")
            appendLine("""<Row><Cell><Data ss:Type="String">Tier Alignment Rule: Distributor_Price_PKR &lt;= Premium_Dealer_Price_PKR &lt;= Standard_Dealer_Price_PKR &lt;= MRP_PKR</Data></Cell></Row>""")
            appendLine("""</Table></Worksheet>""")

            appendLine("""<Worksheet ss:Name="Products"><Table>""")
            appendLine("""<Row><Cell><Data ss:Type="String">SKU</Data></Cell><Cell><Data ss:Type="String">ProductName</Data></Cell><Cell><Data ss:Type="String">CategoryName</Data></Cell><Cell><Data ss:Type="String">GradeViscosity</Data></Cell><Cell><Data ss:Type="String">PackSize</Data></Cell><Cell><Data ss:Type="String">PackUnit</Data></Cell><Cell><Data ss:Type="String">Description</Data></Cell><Cell><Data ss:Type="String">ImageURL</Data></Cell><Cell><Data ss:Type="String">IsActive</Data></Cell></Row>""")
            for (p in activeProducts) {
                appendLine("""<Row><Cell><Data ss:Type="String">${escapeXml(p.sku)}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(p.productName)}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(p.categoryName)}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(p.grade)}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(p.packagingSize)}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(p.packagingUnit)}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(p.description.ifBlank { "SNL ${p.productName} ${p.grade}" })}</Data></Cell><Cell><Data ss:Type="String">${escapeXml(p.imageUrl)}</Data></Cell><Cell><Data ss:Type="String">${if (p.isActive) "TRUE" else "FALSE"}</Data></Cell></Row>""")
            }
            appendLine("""</Table></Worksheet>""")

            appendLine("""<Worksheet ss:Name="Price Matrix"><Table>""")
            appendLine("""<Row><Cell><Data ss:Type="String">SKU</Data></Cell><Cell><Data ss:Type="String">Standard_Dealer_Price_PKR</Data></Cell><Cell><Data ss:Type="String">Premium_Dealer_Price_PKR</Data></Cell><Cell><Data ss:Type="String">Distributor_Price_PKR</Data></Cell><Cell><Data ss:Type="String">Special_Dealer_Price_PKR</Data></Cell><Cell><Data ss:Type="String">MRP_PKR</Data></Cell><Cell><Data ss:Type="String">MinimumOrderQty</Data></Cell><Cell><Data ss:Type="String">EffectiveFrom</Data></Cell><Cell><Data ss:Type="String">EffectiveUntil</Data></Cell><Cell><Data ss:Type="String">IsActive</Data></Cell></Row>""")
            for (p in activeProducts) {
                val skuKey = p.sku.uppercase()
                val dist = (priceLookup[skuKey to "DISTRIBUTOR"] ?: p.priceForGroup("DISTRIBUTOR"))
                    ?.takeIf { it > 0.0 }?.toLong()?.toString() ?: ""
                val std = (priceLookup[skuKey to "STANDARD_DEALER"] ?: p.priceForGroup("STANDARD_DEALER"))
                    ?.takeIf { it > 0.0 }?.toLong()?.toString() ?: ""
                val prem = (priceLookup[skuKey to "PREMIUM_DEALER"] ?: p.priceForGroup("PREMIUM_DEALER"))
                    ?.takeIf { it > 0.0 }?.toLong()?.toString() ?: ""
                val spec = (priceLookup[skuKey to "SPECIAL_DEALER"] ?: p.priceForGroup("SPECIAL_DEALER"))
                    ?.takeIf { it > 0.0 }?.toLong()?.toString() ?: ""
                val mrp = p.mrpPkr.takeIf { it > 0.0 }?.toLong()?.toString() ?: ""
                appendLine("""<Row><Cell><Data ss:Type="String">${escapeXml(p.sku)}</Data></Cell><Cell><Data ss:Type="String">$std</Data></Cell><Cell><Data ss:Type="String">$prem</Data></Cell><Cell><Data ss:Type="String">$dist</Data></Cell><Cell><Data ss:Type="String">$spec</Data></Cell><Cell><Data ss:Type="String">$mrp</Data></Cell><Cell><Data ss:Type="Number">${p.minOrderQty}</Data></Cell><Cell><Data ss:Type="String">2026-11-03</Data></Cell><Cell><Data ss:Type="String"></Data></Cell><Cell><Data ss:Type="String">${if (p.isActive) "TRUE" else "FALSE"}</Data></Cell></Row>""")
            }
            appendLine("""</Table></Worksheet>""")
            appendLine("""</Workbook>""")
        }
    }

    /**
     * Exports live product & price-tier data as either a `.csv` or `.xls` workbook file and launches
     * the Android Share / Google Drive upload chooser.
     */
    fun exportLiveCatalogToGoogleDrive(
        context: Context,
        products: List<Product>,
        groupPrices: List<GroupPrice>,
        asExcelWorkbook: Boolean
    ): Result<File> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outFile = if (asExcelWorkbook) {
                File(exportDir, "SNL_Google_Drive_Product_Catalog_2026.xls").apply {
                    writeText(generateLiveCatalogExcelXmlContent(products, groupPrices))
                }
            } else {
                File(exportDir, "SNL_Google_Drive_Product_Catalog_2026.csv").apply {
                    writeText(generateLiveCatalogCsvContent(products, groupPrices))
                }
            }
            val mime = if (asExcelWorkbook) "application/vnd.ms-excel" else "text/csv"
            shareFile(context, outFile, mime, "Save / Upload SNL Catalog to Google Drive")
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolves a Google Drive file sharing link (`drive.google.com/file/d/{ID}/view`),
     * Google Drive open link (`drive.google.com/open?id={ID}`), Google Sheets link
     * (`docs.google.com/spreadsheets/d/{ID}`), or raw Google Drive File ID into a direct
     * CSV/Excel download URL.
     */
    fun resolveGoogleDriveOrSheetDownloadUrl(rawUrlOrId: String): String {
        val trimmed = rawUrlOrId.trim()
        if (trimmed.isBlank()) return ""

        val sheetsMatch = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)").find(trimmed)
        if (sheetsMatch != null) {
            val docId = sheetsMatch.groupValues[1]
            return "https://docs.google.com/spreadsheets/d/$docId/export?format=csv"
        }

        val driveFileMatch = Regex("/file/d/([a-zA-Z0-9-_]+)").find(trimmed)
        if (driveFileMatch != null) {
            val fileId = driveFileMatch.groupValues[1]
            return "https://drive.google.com/uc?export=download&id=$fileId"
        }

        val driveQueryIdMatch = Regex("[?&]id=([a-zA-Z0-9-_]+)").find(trimmed)
        if (driveQueryIdMatch != null && trimmed.contains("drive.google.com", ignoreCase = true)) {
            val fileId = driveQueryIdMatch.groupValues[1]
            return "https://drive.google.com/uc?export=download&id=$fileId"
        }

        // If the user pasted a raw Google Drive / Sheets File ID (alphanumeric/dash/underscore, >=16 chars)
        if (!trimmed.startsWith("http", ignoreCase = true) && trimmed.matches(Regex("[a-zA-Z0-9-_]{16,}"))) {
            return "https://drive.google.com/uc?export=download&id=$trimmed"
        }

        return trimmed
    }

    suspend fun fetchGoogleSheetCsv(sheetOrDriveUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmed = sheetOrDriveUrl.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Please enter a valid Google Drive CSV/Excel URL, Google Sheets link, or File ID.")
                )
            }
            val exportUrl = resolveGoogleDriveOrSheetDownloadUrl(trimmed)

            val req = Request.Builder().url(exportUrl).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${resp.code}: File requires Google Drive sharing permission ('Anyone with the link') or local Drive picker selection.")
                    )
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank() || body.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true)) {
                    return@withContext Result.failure(
                        Exception("Private Google Drive/Sheet file detected. Enable link sharing or use 'Import from Google Drive' file picker.")
                    )
                }
                Result.success(body)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Ignored in headless test environments
        }
    }

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun unescapeXml(input: String): String =
        input.replace("&quot;", "\"")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&amp;", "&")
}
