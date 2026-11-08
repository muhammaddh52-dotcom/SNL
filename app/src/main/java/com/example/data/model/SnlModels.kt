package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UiLanguage(val code: String, val displayName: String, val isRtl: Boolean) {
    URDU("ur", "اردو (Urdu)", true),
    ENGLISH("en", "English", false)
}

enum class UserRole(val labelEn: String, val labelUr: String) {
    SUPER_ADMIN("Super Admin", "سپر ایڈمن"),
    STAFF("Company Staff", "کمپنی اسٹاف"),
    DISTRIBUTOR("Distributor", "ڈسٹری بیوٹر"),
    DEALER("Dealer", "ڈیلر")
}

enum class AccountStatus(val labelEn: String, val labelUr: String) {
    PENDING_APPROVAL("Pending Approval", "منظوری کا منتظر"),
    ACTIVE("Active", "فعال"),
    REJECTED("Rejected", "مسترد"),
    DEACTIVATED("Deactivated", "غیر فعال")
}

enum class OrderStatus(val labelEn: String, val labelUr: String) {
    PENDING_ADMIN_APPROVAL("Pending Admin Approval", "ایڈمن کی منظوری کا منتظر"),
    CONFIRMED("Confirmed", "تصدیق شدہ"),
    PROCESSING("Processing", "پروسیسنگ"),
    READY_FOR_DISPATCH("Ready for Dispatch", "ترسیل کے لیے تیار"),
    DISPATCHED("Dispatched", "روانہ کر دیا گیا"),
    DELIVERED("Delivered", "پہنچا دیا گیا"),
    CANCELLED("Cancelled", "منسوخ شدہ"),
    REJECTED("Rejected (Archived)", "مسترد شدہ")
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val fullName: String = "",
    val businessName: String = "",
    val phone: String = "",
    val email: String = "",
    val role: String = "DEALER",
    val customerCode: String = "",
    val city: String = "",
    val address: String = "",
    val priceGroupId: String = "STANDARD_DEALER",
    val accountStatus: String = "PENDING_APPROVAL",
    val staffPermissions: String = "",
    val creditAllowed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun hasStaffPermission(permission: String): Boolean {
        if (role == "SUPER_ADMIN") return true
        if (role != "STAFF") return false
        return staffPermissions.split(",").map { it.trim() }.contains(permission)
    }

    fun toCustomer(): Customer = Customer(
        id = id,
        uid = uid,
        customerCode = customerCode,
        fullName = fullName,
        businessName = businessName,
        customerType = if (role == "DISTRIBUTOR") "DISTRIBUTOR" else "DEALER",
        role = role,
        phone = phone,
        email = email,
        city = city,
        address = address,
        priceGroupId = priceGroupId,
        accountStatus = accountStatus,
        creditAllowed = creditAllowed,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

/**
 * Customer entity representing an authorized Dealer or Distributor account in Firestore (`/users/{userId}`),
 * including assigned `priceGroupId`, `accountStatus` tracking (`PENDING_APPROVAL`, `ACTIVE`, `REJECTED`, `DEACTIVATED`),
 * and B2B credit permissions.
 */
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val uid: String = "",
    val customerCode: String = "",
    val fullName: String = "",
    val businessName: String = "",
    val customerType: String = "DEALER",
    val role: String = "DEALER",
    val phone: String = "",
    val email: String = "",
    val city: String = "",
    val address: String = "",
    val priceGroupId: String = "STANDARD_DEALER",
    val accountStatus: String = "PENDING_APPROVAL",
    val creditAllowed: Boolean = false,
    val creditLimitPkr: Double = 0.0,
    val approvedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isApprovedAndActive: Boolean
        get() = accountStatus == "ACTIVE"

    fun toUserProfile(): UserProfile = UserProfile(
        id = id,
        uid = uid,
        fullName = fullName,
        businessName = businessName,
        phone = phone,
        email = email,
        role = role.ifBlank { customerType },
        customerCode = customerCode,
        city = city,
        address = address,
        priceGroupId = priceGroupId,
        accountStatus = accountStatus,
        creditAllowed = creditAllowed,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@IgnoreExtraProperties
data class AdminRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val role: String = "SUPER_ADMIN",
    @ServerTimestamp val createdAt: Timestamp? = null
)

/**
 * Firestore Collection & Field Schema Constants for Product Data (`/products/{productId}`).
 */
object ProductCollectionSchema {
    const val COLLECTION_PRODUCTS = "products"
    const val COLLECTION_CATEGORIES = "categories"
    const val COLLECTION_PRICE_GROUPS = "priceGroups"
    const val COLLECTION_GROUP_PRICES = "groupPrices"
    const val COLLECTION_ORDERS = "orders"
    const val COLLECTION_CUSTOMERS = "users"

    // Core Product Document Fields
    const val FIELD_SKU = "sku"
    const val FIELD_NAME = "name"
    const val FIELD_PRODUCT_NAME = "productName"
    const val FIELD_CATEGORY = "category"
    const val FIELD_CATEGORY_ID = "categoryId"
    const val FIELD_CATEGORY_NAME = "categoryName"
    const val FIELD_PRICE_TIERS = "priceTiers"
    const val FIELD_STOCK_AVAILABILITY = "stockAvailability"
    const val FIELD_IN_STOCK = "inStock"
    const val FIELD_STOCK_QTY = "stockQty"
    const val FIELD_RESERVED_QTY = "reservedQty"

    // Standard Distributor & Dealer Price Tier Keys
    const val TIER_DISTRIBUTOR = "DISTRIBUTOR"
    const val TIER_STANDARD_DEALER = "STANDARD_DEALER"
    const val TIER_PREMIUM_DEALER = "PREMIUM_DEALER"
    const val TIER_SPECIAL_DEALER = "SPECIAL_DEALER"

    fun computeStockAvailability(stockQty: Long, reservedQty: Long, lowStockThreshold: Long = 20L): String {
        val available = (stockQty - reservedQty).coerceAtLeast(0L)
        return when {
            available <= 0L -> "OUT_OF_STOCK"
            available <= lowStockThreshold -> "LOW_STOCK"
            else -> "IN_STOCK"
        }
    }
}

/**
 * Product entity stored in `/products/{productId}` containing product metadata, category,
 * multi-tier pricing (`priceTiers`), and real-time stock availability (`stockAvailability`, `inStock`, `availableStock`).
 */
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val sku: String = "",
    val serialNo: Long = 0L,
    val name: String = "",
    val productName: String = "",
    val category: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val priceTiers: Map<String, Double> = emptyMap(),
    val stockAvailability: String = "IN_STOCK",
    val inStock: Boolean = true,
    val imageUrl: String = "",
    val description: String = "",
    val grade: String = "",
    val packagingSize: String = "",
    val packagingUnit: String = "Carton",
    val cartonQty: Long = 12L,
    val minOrderQty: Long = 1L,
    val mrpPkr: Double = 0.0,
    val stockQty: Long = 100L,
    val reservedQty: Long = 0L,
    val isFeatured: Boolean = false,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val availableStock: Long
        get() = (stockQty - reservedQty).coerceAtLeast(0L)

    val displayName: String
        get() = name.ifBlank { productName }

    val displayCategory: String
        get() = category.ifBlank { categoryName }

    fun priceForGroup(priceGroupCode: String): Double? {
        return priceTiers[priceGroupCode.uppercase()]?.takeIf { it > 0.0 }
    }
}

@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val categoryCode: String = "",
    val categoryName: String = "",
    val nameUrdu: String = "",
    val sortOrder: Long = 0L,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

/**
 * PriceGroup entity stored in `/priceGroups/{groupId}` representing a distributor or dealer pricing tier
 * (`DISTRIBUTOR`, `STANDARD_DEALER`, `PREMIUM_DEALER`, `SPECIAL_DEALER`).
 */
@IgnoreExtraProperties
data class PriceGroup(
    @DocumentId val id: String = "",
    val groupCode: String = "",
    val groupName: String = "",
    val description: String = "",
    val tierType: String = "DEALER",
    val defaultDiscountPercent: Double = 0.0,
    val minOrderQty: Long = 1L,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GroupPrice(
    @DocumentId val id: String = "",
    val sku: String = "",
    val priceGroupCode: String = "",
    val sellingPricePkr: Double = 0.0,
    val mrpPkr: Double = 0.0,
    val minOrderQty: Long = 1L,
    val discountPercent: Double = 0.0,
    val effectiveFrom: String = "2026-11-03",
    val isActive: Boolean = true,
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Immutable snapshot of a single ordered line item and its locked unit price at checkout time.
 */
@IgnoreExtraProperties
data class OrderPriceSnapshot(
    val sku: String = "",
    val productName: String = "",
    val packagingSize: String = "",
    val grade: String = "",
    val priceGroupCode: String = "STANDARD_DEALER",
    val quantity: Int = 1,
    val unitPricePkr: Double = 0.0,
    val lineTotalPkr: Double = 0.0
)

data class OrderLineItem(
    val sku: String = "",
    val productName: String = "",
    val packagingSize: String = "",
    val grade: String = "",
    val quantity: Int = 1,
    val unitPricePkr: Double = 0.0,
    val lineTotalPkr: Double = 0.0
) {
    fun toPriceSnapshot(priceGroupCode: String): OrderPriceSnapshot = OrderPriceSnapshot(
        sku = sku,
        productName = productName,
        packagingSize = packagingSize,
        grade = grade,
        priceGroupCode = priceGroupCode,
        quantity = quantity,
        unitPricePkr = unitPricePkr,
        lineTotalPkr = lineTotalPkr
    )

    fun encode(): String =
        listOf(
            sku.replace("|", "/"),
            productName.replace("|", "/"),
            packagingSize.replace("|", "/"),
            grade.replace("|", "/"),
            quantity.toString(),
            unitPricePkr.toString(),
            lineTotalPkr.toString()
        ).joinToString("|")

    companion object {
        fun decodeList(snapshotText: String): List<OrderLineItem> {
            if (snapshotText.isBlank()) return emptyList()
            return snapshotText.split(";;").mapNotNull { row ->
                val parts = row.split("|")
                if (parts.size >= 7) {
                    OrderLineItem(
                        sku = parts[0],
                        productName = parts[1],
                        packagingSize = parts[2],
                        grade = parts[3],
                        quantity = parts[4].toIntOrNull() ?: 1,
                        unitPricePkr = parts[5].toDoubleOrNull() ?: 0.0,
                        lineTotalPkr = parts[6].toDoubleOrNull() ?: 0.0
                    )
                } else null
            }
        }

        fun encodeList(items: List<OrderLineItem>): String =
            items.joinToString(";;") { it.encode() }
    }
}

/**
 * Order entity stored in `/orders/{orderId}` with immutable price snapshot fields (`priceSnapshotText`,
 * `priceSnapshotMap`, `parsedPriceSnapshots()`) and full status tracking (`orderStatus`, `paymentStatus`,
 * `visibleToCustomer` for soft-delete rejection archive, `approvedBy`, `rejectedBy`, `rejectionReason`).
 */
@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val customerId: String = "",
    val customerCode: String = "",
    val customerName: String = "",
    val businessName: String = "",
    val customerType: String = "DEALER",
    val phone: String = "",
    val city: String = "",
    val deliveryAddress: String = "",
    val orderInstructions: String = "",
    val priceGroupId: String = "STANDARD_DEALER",
    val itemsSummary: String = "",
    val itemsCount: Long = 1L,
    val totalCartons: Long = 1L,
    val priceSnapshotText: String = "",
    val priceSnapshotMap: Map<String, Double> = emptyMap(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val deliveryCharges: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paymentMethod: String = "COD",
    val paymentStatus: String = "UNPAID",
    val orderStatus: String = "PENDING_ADMIN_APPROVAL",
    val statusHistory: List<String> = emptyList(),
    val visibleToCustomer: Boolean = true,
    val rejectionReason: String = "",
    val approvedBy: String = "",
    val rejectedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedItems(): List<OrderLineItem> = OrderLineItem.decodeList(priceSnapshotText)

    fun parsedPriceSnapshots(): List<OrderPriceSnapshot> =
        parsedItems().map { it.toPriceSnapshot(priceGroupId) }

    fun snapshotUnitPriceForSku(sku: String): Double? =
        priceSnapshotMap[sku.uppercase()] ?: parsedItems().firstOrNull { it.sku.equals(sku, ignoreCase = true) }?.unitPricePkr

    val isPendingApproval: Boolean
        get() = orderStatus == "PENDING_ADMIN_APPROVAL"

    val isRejectedArchived: Boolean
        get() = orderStatus == "REJECTED" && !visibleToCustomer
}

@IgnoreExtraProperties
data class InventoryMovement(
    @DocumentId val id: String = "",
    val sku: String = "",
    val productName: String = "",
    val movementType: String = "MANUAL_ADJUSTMENT",
    val quantityDelta: Long = 0L,
    val previousStock: Long = 0L,
    val newStock: Long = 0L,
    val referenceId: String = "",
    val note: String = "",
    val performedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PriceImportBatch(
    @DocumentId val id: String = "",
    val fileName: String = "",
    val sourceType: String = "AI_IMAGE_OCR",
    val adminId: String = "",
    val totalRecords: Long = 0L,
    val productsCreated: Long = 0L,
    val productsUpdated: Long = 0L,
    val pricesUpdated: Long = 0L,
    val failedRows: Long = 0L,
    val status: String = "COMPLETED",
    val beforeSnapshotData: String = "",
    val afterSnapshotData: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PriceHistoryRecord(
    @DocumentId val id: String = "",
    val sku: String = "",
    val productName: String = "",
    val priceGroupCode: String = "",
    val oldPricePkr: Double = 0.0,
    val newPricePkr: Double = 0.0,
    val adminId: String = "",
    val importId: String = "",
    val effectiveDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val targetUserId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "GENERAL",
    val referenceId: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WhatsAppDeliveryLog(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val recipientPhone: String = "",
    val eventType: String = "NEW_ORDER",
    val deliveryMode: String = "CLICK_TO_CHAT_PREPARED",
    val messagePreview: String = "",
    val apiResponse: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CompanySettings(
    @DocumentId val id: String = "",
    val companyName: String = "SAIFI NATIONAL LUBRICANTS",
    val brandName: String = "SNL",
    val companyPhone: String = "+92 310 100 52 53",
    val companyCity: String = "KARACHI",
    val whatsappBusinessAccountId: String = "",
    val whatsappPhoneNumberId: String = "",
    val companyWhatsappNumber: String = "+923101005253",
    val whatsappNotificationsEnabled: Boolean = true,
    val googleSheetUrl: String = "",
    val googleSheetWorksheet: String = "Price Matrix",
    val googleSheetLastSync: String = "Not synced yet",
    val googleSheetAutoSync: Boolean = false,
    val defaultDeliveryChargesPkr: Double = 0.0,
    val lowStockThreshold: Long = 20L,
    val allowExcelStockUpdate: Boolean = false,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class ExtractedPriceRow(
    val serialNo: Int,
    val sku: String,
    val productName: String,
    val categoryCode: String,
    val categoryName: String,
    val packagingSize: String,
    val packagingUnit: String,
    val cartonQty: Int,
    val grade: String,
    val rawPriceText: String,
    val distributorPricePkr: Double?,
    val standardDealerPricePkr: Double? = null,
    val premiumDealerPricePkr: Double? = null,
    val specialDealerPricePkr: Double? = null,
    val mrpPkr: Double? = null,
    val stockQty: Int? = null,
    val ocrConfidence: Int = 99,
    val isUncertainOrMissingPrice: Boolean = false,
    val uncertaintyNote: String = "",
    val isExistingProduct: Boolean = false,
    val oldPricePkr: Double? = null
)

data class CartItem(
    val product: Product,
    val unitPricePkr: Double,
    val priceGroupCode: String,
    val quantity: Int
) {
    val lineTotalPkr: Double
        get() = unitPricePkr * quantity
}

/**
 * Real-time customer support chat message backed by Firestore (`/notifications/{messageId}`).
 */
data class SupportChatMessage(
    val id: String = "",
    val threadUserId: String = "",
    val senderRole: String = "CUSTOMER", // "CUSTOMER" or "SUPPORT"
    val senderName: String = "",
    val businessName: String = "",
    val messageText: String = "",
    val isRead: Boolean = false,
    val createdAt: Timestamp? = null
)

