package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ExtractedPriceRow
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiPriceListReader"

data class AiExtractionResult(
    val documentTitle: String,
    val detectedPriceGroupLabel: String,
    val requiresPriceGroupSelection: Boolean,
    val rows: List<ExtractedPriceRow>
)

class AiPriceListReader {

    private val priceRowSchema = Schema.obj(
        properties = mapOf(
            "serialNo" to Schema.integer(description = "Row serial number (1, 2, 3...)"),
            "sku" to Schema.string(description = "Product SKU or code (e.g. SNL-001 if not explicitly printed)"),
            "categoryName" to Schema.string(description = "Category name (e.g. MIX Brands, M. Cycle, GASOLINE / CNG, DIESEL, FLUIDS, HYD. OIL, GEAR OIL)"),
            "productName" to Schema.string(description = "Exact item or product name"),
            "packagingSize" to Schema.string(description = "Exact packing size (e.g. 700ml x 12, 1ltr x 12, 10ltr)"),
            "grade" to Schema.string(description = "Exact SAE grade, viscosity, or colour (e.g. SC 20w50, SP 5w30)"),
            "rawPriceText" to Schema.string(description = "Exact visible text in the price column (e.g. 5,105 or -)"),
            "distributorPricePkr" to Schema.double(description = "Extracted numeric price in PKR, or -1 if missing/unreadable/dash", nullable = true),
            "standardDealerPricePkr" to Schema.double(description = "Standard dealer price in PKR if a separate column exists, or -1", nullable = true),
            "mrpPkr" to Schema.double(description = "MRP in PKR if a separate column exists, or -1", nullable = true),
            "ocrConfidence" to Schema.integer(description = "Confidence score from 0 to 100"),
            "isUncertainOrMissingPrice" to Schema.boolean(description = "True if the price is '-', blank, smudged, or ambiguous"),
            "uncertaintyNote" to Schema.string(description = "Explanation if price is missing or uncertain")
        ),
        optionalProperties = listOf("distributorPricePkr", "standardDealerPricePkr", "mrpPkr")
    )

    private val extractionSchema = Schema.obj(
        properties = mapOf(
            "documentTitle" to Schema.string(description = "Header title of the price list"),
            "detectedPriceGroupLabel" to Schema.string(description = "Detected price group heading (e.g. Sole Distributor, Dealer, etc.)"),
            "singlePriceColumnOnly" to Schema.boolean(description = "True if the table has only one price column"),
            "items" to Schema.array(
                items = priceRowSchema,
                description = "All product rows detected in the price list image"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extractionSchema
            }
        )
    }

    suspend fun extractPriceListFromBitmap(bitmap: Bitmap): Result<AiExtractionResult> {
        return try {
            val prompt = """
                You are an expert OCR and B2B lubricant price list analyzer for SAIFI NATIONAL LUBRICANTS (SNL) in Pakistan.
                Carefully read the complete attached price list image.
                1. Detect table headings, category groupings, serial numbers, item names, packing sizes, grade/viscosity, and all visible price columns.
                2. Preserve exact numerical digits and decimal values.
                3. CRITICAL OCR SAFETY: Never guess unclear or missing prices. If a row shows '-' or is unreadable, set isUncertainOrMissingPrice = true, distributorPricePkr = -1, and explain in uncertaintyNote.
                4. Never invent product names or prices.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }

            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))

            val root = JSONObject(jsonText)
            val docTitle = root.optString("documentTitle", "SNL Price List")
            val groupLabel = root.optString("detectedPriceGroupLabel", "DISTRIBUTOR")
            val singleColumn = root.optBoolean("singlePriceColumnOnly", true)
            val itemsArray = root.getJSONArray("items")

            val extractedRows = mutableListOf<ExtractedPriceRow>()
            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.getJSONObject(i)
                val sNo = obj.optInt("serialNo", i + 1)
                val rawSku = obj.optString("sku", "").trim()
                val sku = if (rawSku.isNotBlank()) rawSku else "SNL-%03d".format(sNo)
                val catName = obj.optString("categoryName", "General").trim()
                val catCode = catName.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')
                val productName = obj.optString("productName", "").trim()
                val packing = obj.optString("packagingSize", "").trim()
                val grade = obj.optString("grade", "").trim()
                val rawPrice = obj.optString("rawPriceText", "-").trim()
                val distPriceRaw = obj.optDouble("distributorPricePkr", -1.0)
                val dealerPriceRaw = obj.optDouble("standardDealerPricePkr", -1.0)
                val mrpRaw = obj.optDouble("mrpPkr", -1.0)
                val confidence = obj.optInt("ocrConfidence", 90)
                val uncertainFlag = obj.optBoolean("isUncertainOrMissingPrice", false) || distPriceRaw <= 0.0
                val note = obj.optString("uncertaintyNote", "")

                val lowerPack = packing.lowercase()
                val unit = when {
                    lowerPack.contains("bucket") -> "Bucket"
                    lowerPack.contains("can") -> "Can"
                    lowerPack.contains("x") -> "Carton"
                    else -> "Carton"
                }
                val cartonQty = if (lowerPack.contains("x")) {
                    lowerPack.substringAfter("x").trim().toIntOrNull() ?: 1
                } else 1

                extractedRows.add(
                    ExtractedPriceRow(
                        serialNo = sNo,
                        sku = sku,
                        productName = productName,
                        categoryCode = catCode.ifBlank { "GENERAL" },
                        categoryName = catName.ifBlank { "General" },
                        packagingSize = packing,
                        packagingUnit = unit,
                        cartonQty = cartonQty,
                        grade = grade,
                        rawPriceText = rawPrice,
                        distributorPricePkr = if (distPriceRaw > 0) distPriceRaw else null,
                        standardDealerPricePkr = if (dealerPriceRaw > 0) dealerPriceRaw else null,
                        mrpPkr = if (mrpRaw > 0) mrpRaw else null,
                        ocrConfidence = confidence,
                        isUncertainOrMissingPrice = uncertainFlag,
                        uncertaintyNote = note
                    )
                )
            }

            Result.success(
                AiExtractionResult(
                    documentTitle = docTitle,
                    detectedPriceGroupLabel = groupLabel,
                    requiresPriceGroupSelection = singleColumn,
                    rows = extractedRows
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI Price List extraction timed out after 60s", e)
            Result.failure(Exception("AI extraction timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Price List extraction failed", e)
            Result.failure(e)
        }
    }
}
