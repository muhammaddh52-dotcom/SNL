package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.CompanySettings
import com.example.data.model.ExtractedPriceRow
import com.example.data.model.GroupPrice
import com.example.data.model.InventoryMovement
import com.example.data.model.Order
import com.example.data.model.OrderLineItem
import com.example.data.model.PriceGroup
import com.example.data.model.PriceHistoryRecord
import com.example.data.model.PriceImportBatch
import com.example.data.model.Product
import com.example.data.model.ProductCollectionSchema
import com.example.data.model.UserProfile
import com.example.data.model.WhatsAppDeliveryLog
import com.example.data.seed.AttachedPriceListCatalog
import com.example.util.InvoiceAndReportPdfGenerator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SnlRepository(private val db: FirebaseFirestore) {

    private var appContext: Context? = null

    init {
        FirestorePersistenceConfig.configureOfflinePersistence(db)
    }

    // CRITICAL: Always initialize with R.string.firestore_database_id and offline persistence enabled
    constructor(context: Context) : this(
        FirestorePersistenceConfig.getConfiguredFirestore(context)
    ) {
        this.appContext = context.applicationContext
    }

    private val auth = Firebase.auth

    fun setOfflineMode(offline: Boolean) {
        FirestorePersistenceConfig.setFirestoreOfflineMode(db, offline)
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- 1. USER PROFILE & ROLE MANAGEMENT ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.GET, path)
                throw error
            }
    }

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> {
        val path = "users"
        return db.collection("users")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                throw error
            }
    }

    /**
     * Bootstraps the signed-in user as Super Admin (registers in /admins/{uid} and /users/{uid})
     * and seeds initial price groups, categories, and company settings if not yet present.
     */
    suspend fun bootstrapSuperAdminAndSettings(
        fullName: String = "Muhammad (SNL Super Admin)",
        email: String = "muhammad.dh52@gmail.com"
    ): Result<UserProfile> {
        val uid = requireUserId()
        return try {
            val adminRef = db.collection("admins").document(uid)
            adminRef.set(
                mapOf(
                    "uid" to uid,
                    "email" to email.ifBlank { "admin@snl.pk" },
                    "role" to "SUPER_ADMIN",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val userRef = db.collection("users").document(uid)
            val userPayload = mapOf(
                "uid" to uid,
                "fullName" to fullName.ifBlank { "SNL Super Admin" },
                "businessName" to "SAIFI NATIONAL LUBRICANTS (Head Office)",
                "phone" to "+92 310 100 52 53",
                "email" to email.ifBlank { "admin@snl.pk" },
                "role" to "SUPER_ADMIN",
                "customerCode" to "SNL-HQ-001",
                "city" to "Karachi",
                "address" to "SAIFI National Lubricants HQ, Karachi, Pakistan",
                "priceGroupId" to "DISTRIBUTOR",
                "accountStatus" to "ACTIVE",
                "staffPermissions" to "ALL",
                "creditAllowed" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userRef.set(userPayload).await()

            // Ensure default price groups and categories exist
            seedCoreMetadataIfMissing(uid)

            val saved = userRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = uid, uid = uid, fullName = fullName, role = "SUPER_ADMIN", accountStatus = "ACTIVE")
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    /**
     * Method B: Dealer or Distributor Self-Registration (initial status is always PENDING_APPROVAL).
     */
    suspend fun submitSelfRegistration(
        fullName: String,
        businessName: String,
        phone: String,
        email: String,
        city: String,
        address: String,
        role: String // DEALER or DISTRIBUTOR
    ): Result<String> {
        val uid = requireUserId()
        val safeRole = if (role == "DISTRIBUTOR") "DISTRIBUTOR" else "DEALER"
        val defaultGroup = if (safeRole == "DISTRIBUTOR") "DISTRIBUTOR" else "STANDARD_DEALER"
        val codePrefix = if (safeRole == "DISTRIBUTOR") "DST" else "DLR"
        val customerCode = "$codePrefix-${uid.take(5).uppercase()}"
        val docRef = db.collection("users").document(uid)
        val payload = mapOf(
            "uid" to uid,
            "fullName" to fullName.trim(),
            "businessName" to businessName.trim(),
            "phone" to phone.trim(),
            "email" to email.trim().ifBlank { "customer@snl.pk" },
            "role" to safeRole,
            "customerCode" to customerCode,
            "city" to city.trim(),
            "address" to address.trim(),
            "priceGroupId" to defaultGroup,
            "accountStatus" to "PENDING_APPROVAL",
            "staffPermissions" to "",
            "creditAllowed" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            createNotificationInternal(
                targetUserId = "ADMIN_ALL",
                title = "New $safeRole Registration",
                message = "$businessName ($fullName, $city) registered and is awaiting Admin approval.",
                type = "REGISTRATION",
                referenceId = uid
            )
            Result.success(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Method A: Admin creates or updates a Dealer, Distributor, or Staff account.
     */
    suspend fun adminCreateOrUpdateAccount(
        targetUid: String,
        fullName: String,
        businessName: String,
        phone: String,
        email: String,
        role: String,
        customerCode: String,
        city: String,
        address: String,
        priceGroupId: String,
        accountStatus: String,
        staffPermissions: String = "",
        creditAllowed: Boolean = false
    ): Result<String> {
        requireUserId()
        val docId = targetUid.ifBlank { "usr_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("users").document(docId)
        return try {
            val existingSnap = docRef.get().await()
            val createdAtVal = if (existingSnap.exists()) {
                existingSnap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            } else {
                FieldValue.serverTimestamp()
            }

            val payload = mapOf(
                "uid" to docId,
                "fullName" to fullName.trim(),
                "businessName" to businessName.trim(),
                "phone" to phone.trim(),
                "email" to email.trim().ifBlank { "$docId@snl.pk" },
                "role" to role,
                "customerCode" to customerCode.trim().ifBlank { "SNL-${docId.take(5).uppercase()}" },
                "city" to city.trim(),
                "address" to address.trim(),
                "priceGroupId" to priceGroupId,
                "accountStatus" to accountStatus,
                "staffPermissions" to staffPermissions,
                "creditAllowed" to creditAllowed,
                "createdAt" to createdAtVal,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            if (role == "STAFF" || role == "SUPER_ADMIN") {
                db.collection("admins").document(docId).set(
                    mapOf(
                        "uid" to docId,
                        "email" to email.trim().ifBlank { "$docId@snl.pk" },
                        "role" to role,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            createNotificationInternal(
                targetUserId = docId,
                title = "Account Status Updated: $accountStatus",
                message = "Your SNL Lubricants account ($businessName) is now $accountStatus with Price Group: $priceGroupId.",
                type = "REGISTRATION",
                referenceId = docId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- 2. CORE METADATA, CATEGORIES & PRICE GROUPS ---

    suspend fun seedCoreMetadataIfMissing(adminUid: String) {
        try {
            for (group in AttachedPriceListCatalog.initialPriceGroups) {
                val ref = db.collection("priceGroups").document(group.groupCode)
                if (!ref.get().await().exists()) {
                    ref.set(
                        mapOf(
                            "groupCode" to group.groupCode,
                            "groupName" to group.groupName,
                            "description" to group.description,
                            "defaultDiscountPercent" to group.defaultDiscountPercent,
                            "minOrderQty" to group.minOrderQty,
                            "isActive" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            for (cat in AttachedPriceListCatalog.initialCategories) {
                val ref = db.collection("categories").document(cat.categoryCode)
                if (!ref.get().await().exists()) {
                    ref.set(
                        mapOf(
                            "categoryCode" to cat.categoryCode,
                            "categoryName" to cat.categoryName,
                            "nameUrdu" to cat.nameUrdu,
                            "sortOrder" to cat.sortOrder,
                            "isActive" to true,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            val settingsRef = db.collection("appSettings").document("global")
            if (!settingsRef.get().await().exists()) {
                settingsRef.set(
                    mapOf(
                        "companyName" to "SAIFI NATIONAL LUBRICANTS",
                        "brandName" to "SNL",
                        "companyPhone" to "+92 310 100 52 53",
                        "companyCity" to "KARACHI",
                        "whatsappBusinessAccountId" to "",
                        "whatsappPhoneNumberId" to "",
                        "companyWhatsappNumber" to "+923101005253",
                        "whatsappNotificationsEnabled" to true,
                        "googleSheetUrl" to "",
                        "googleSheetWorksheet" to "Price Matrix",
                        "googleSheetLastSync" to "Not synced yet",
                        "googleSheetAutoSync" to false,
                        "defaultDeliveryChargesPkr" to 0.0,
                        "lowStockThreshold" to 20L,
                        "allowExcelStockUpdate" to false,
                        "updatedBy" to adminUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "priceGroups")
        }
    }

    fun observeActiveCategories(): Flow<List<Category>> {
        val path = "categories"
        return db.collection(path)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(Category::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.sortOrder }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeActivePriceGroups(): Flow<List<PriceGroup>> {
        val path = "priceGroups"
        return db.collection(path)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(PriceGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.groupCode }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun savePriceGroup(
        groupCode: String,
        groupName: String,
        description: String,
        discountPercent: Double,
        minOrderQty: Long
    ): Result<String> {
        requireUserId()
        val cleanCode = groupCode.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "_")
        val docRef = db.collection("priceGroups").document(cleanCode)
        return try {
            val existing = docRef.get().await()
            val createdAt = if (existing.exists()) {
                existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            } else {
                FieldValue.serverTimestamp()
            }
            docRef.set(
                mapOf(
                    "groupCode" to cleanCode,
                    "groupName" to groupName.trim(),
                    "description" to description.trim(),
                    "defaultDiscountPercent" to discountPercent,
                    "minOrderQty" to minOrderQty.coerceAtLeast(1L),
                    "isActive" to true,
                    "createdAt" to createdAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(cleanCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveCategory(
        categoryCode: String,
        categoryName: String,
        nameUrdu: String,
        sortOrder: Long
    ): Result<String> {
        requireUserId()
        val cleanCode = categoryCode.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "_")
        val docRef = db.collection("categories").document(cleanCode)
        return try {
            val existing = docRef.get().await()
            val createdAt = if (existing.exists()) {
                existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            } else {
                FieldValue.serverTimestamp()
            }
            docRef.set(
                mapOf(
                    "categoryCode" to cleanCode,
                    "categoryName" to categoryName.trim(),
                    "nameUrdu" to nameUrdu.trim().ifBlank { categoryName.trim() },
                    "sortOrder" to sortOrder,
                    "isActive" to true,
                    "createdAt" to createdAt
                )
            ).await()
            Result.success(cleanCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- 3. PRODUCTS & GROUP-BASED PRICING ---

    fun observeActiveProducts(): Flow<List<Product>> {
        val path = "products"
        return db.collection(path)
            .whereEqualTo("isActive", true)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                FirestorePersistenceConfig.updateCacheSourceStatus(
                    snap.metadata.isFromCache || FirestorePersistenceConfig.isSimulatedOffline.value
                )
                val parsed = snap.toObjects(
                    Product::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.serialNo }
                if (parsed.isNotEmpty()) {
                    appContext?.let { FirestorePersistenceConfig.cacheProductsLocally(it, parsed) }
                    parsed
                } else {
                    FirestorePersistenceConfig.getOfflineFallbackProducts(appContext)
                }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                emit(FirestorePersistenceConfig.getOfflineFallbackProducts(appContext))
            }
    }

    /**
     * Constructs a Firestore [Query] on `/products` with server-side query filtering (`whereEqualTo`,
     * `whereGreaterThanOrEqualTo`, `whereLessThanOrEqualTo`) for active products, category filter,
     * and field-scoped prefix matching (`SKU`, `NAME`, or `CATEGORY`).
     */
    fun buildProductSearchFirestoreQuery(
        searchQuery: String = "",
        categoryFilter: String = "ALL",
        searchFieldScope: String = "ALL"
    ): Query {
        var query: Query = db.collection("products")
            .whereEqualTo("isActive", true)

        when {
            categoryFilter.equals("FEATURED", ignoreCase = true) -> {
                query = query.whereEqualTo("isFeatured", true)
            }
            categoryFilter.isNotBlank() &&
                !categoryFilter.equals("ALL", ignoreCase = true) &&
                !categoryFilter.equals("FAVOURITES", ignoreCase = true) -> {
                query = query.whereEqualTo("categoryId", categoryFilter.uppercase(Locale.US))
            }
        }

        val trimmed = searchQuery.trim()
        if (trimmed.isNotEmpty() && categoryFilter.equals("ALL", ignoreCase = true)) {
            when (searchFieldScope.uppercase(Locale.US)) {
                "SKU" -> {
                    val skuPrefix = trimmed.uppercase(Locale.US)
                    query = query
                        .whereGreaterThanOrEqualTo("sku", skuPrefix)
                        .whereLessThanOrEqualTo("sku", skuPrefix + "\uf8ff")
                }
                "NAME" -> {
                    query = query
                        .whereGreaterThanOrEqualTo("productName", trimmed)
                        .whereLessThanOrEqualTo("productName", trimmed + "\uf8ff")
                }
                "CATEGORY" -> {
                    query = query
                        .whereGreaterThanOrEqualTo("categoryName", trimmed)
                        .whereLessThanOrEqualTo("categoryName", trimmed + "\uf8ff")
                }
            }
        }
        return query
    }

    /**
     * Real-time Firestore product search stream that combines server-side Firestore query filtering
     * (`whereEqualTo("isActive", true)`, category filtering, and prefix range queries) with
     * multi-field matching across product name (`productName` / `name`), `sku`, and category
     * (`categoryName` / `category` / `categoryId`), including full offline cache support.
     */
    fun observeProductsWithGlobalSearch(
        searchQuery: String = "",
        categoryFilter: String = "ALL",
        searchFieldScope: String = "ALL"
    ): Flow<List<Product>> {
        val path = "products"
        val trimmedQuery = searchQuery.trim()
        // Use the server-side filtered Firestore query when no composite-index-requiring range is active,
        // and apply multi-field real-time filtering across name, SKU, and category on every snapshot emission.
        val baseQuery = if (trimmedQuery.isEmpty()) {
            buildProductSearchFirestoreQuery(
                searchQuery = "",
                categoryFilter = categoryFilter,
                searchFieldScope = searchFieldScope
            )
        } else {
            db.collection(path).whereEqualTo("isActive", true)
        }

        return baseQuery
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                FirestorePersistenceConfig.updateCacheSourceStatus(
                    snap.metadata.isFromCache || FirestorePersistenceConfig.isSimulatedOffline.value
                )
                val snapshotItems = snap.toObjects(
                    Product::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (snapshotItems.isNotEmpty() && trimmedQuery.isEmpty() && categoryFilter.equals("ALL", ignoreCase = true)) {
                    appContext?.let { FirestorePersistenceConfig.cacheProductsLocally(it, snapshotItems) }
                }
                val allActive = snapshotItems.ifEmpty {
                    FirestorePersistenceConfig.getOfflineFallbackProducts(appContext)
                }
                allActive.filter { prod ->
                    val matchesCategory = when (categoryFilter.uppercase(Locale.US)) {
                        "ALL", "FAVOURITES", "" -> true
                        "FEATURED" -> prod.isFeatured
                        else -> prod.categoryId.equals(categoryFilter, ignoreCase = true) ||
                            prod.categoryName.equals(categoryFilter, ignoreCase = true) ||
                            prod.category.equals(categoryFilter, ignoreCase = true)
                    }
                    val matchesSearch = if (trimmedQuery.isBlank()) {
                        true
                    } else {
                        when (searchFieldScope.uppercase(Locale.US)) {
                            "NAME" -> prod.displayName.contains(trimmedQuery, ignoreCase = true) ||
                                prod.productName.contains(trimmedQuery, ignoreCase = true) ||
                                prod.name.contains(trimmedQuery, ignoreCase = true)
                            "SKU" -> prod.sku.contains(trimmedQuery, ignoreCase = true)
                            "CATEGORY" -> prod.categoryName.contains(trimmedQuery, ignoreCase = true) ||
                                prod.category.contains(trimmedQuery, ignoreCase = true) ||
                                prod.categoryId.contains(trimmedQuery, ignoreCase = true)
                            else -> prod.displayName.contains(trimmedQuery, ignoreCase = true) ||
                                prod.productName.contains(trimmedQuery, ignoreCase = true) ||
                                prod.name.contains(trimmedQuery, ignoreCase = true) ||
                                prod.sku.contains(trimmedQuery, ignoreCase = true) ||
                                prod.categoryName.contains(trimmedQuery, ignoreCase = true) ||
                                prod.category.contains(trimmedQuery, ignoreCase = true) ||
                                prod.categoryId.contains(trimmedQuery, ignoreCase = true) ||
                                prod.grade.contains(trimmedQuery, ignoreCase = true) ||
                                prod.packagingSize.contains(trimmedQuery, ignoreCase = true)
                        }
                    }
                    matchesCategory && matchesSearch
                }.sortedBy { it.serialNo }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                emit(FirestorePersistenceConfig.getOfflineFallbackProducts(appContext))
            }
    }

    fun observeActiveGroupPrices(): Flow<List<GroupPrice>> {
        val path = "groupPrices"
        return db.collection(path)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(GroupPrice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun saveProductAndPrices(
        sku: String,
        serialNo: Long,
        productName: String,
        categoryId: String,
        categoryName: String,
        grade: String,
        packagingSize: String,
        packagingUnit: String,
        cartonQty: Long,
        minOrderQty: Long,
        mrpPkr: Double,
        stockQty: Long,
        isFeatured: Boolean,
        isActive: Boolean,
        description: String = "",
        imageUrl: String = "",
        groupPricesMap: Map<String, Double> = emptyMap()
    ): Result<String> {
        val adminUid = requireUserId()
        val cleanSku = sku.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "-")
        val prodRef = db.collection("products").document(cleanSku)
        return try {
            val existing = prodRef.get().await()
            val createdAt = if (existing.exists()) {
                existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            } else {
                FieldValue.serverTimestamp()
            }
            val reservedQty = if (existing.exists()) {
                existing.getLong("reservedQty") ?: 0L
            } else 0L
            val preservedImage = if (imageUrl.isNotBlank()) imageUrl else (existing.getString("imageUrl") ?: "")

            @Suppress("UNCHECKED_CAST")
            val existingTiers = if (existing.exists()) {
                (existing.get("priceTiers") as? Map<String, Number>)
                    ?.mapValues { it.value.toDouble() }
                    ?: emptyMap()
            } else emptyMap()

            val mergedPriceTiers = existingTiers.toMutableMap()
            for ((groupCode, pricePkr) in groupPricesMap) {
                if (pricePkr > 0.0) {
                    mergedPriceTiers[groupCode.uppercase()] = pricePkr
                }
            }

            val coercedStock = stockQty.coerceAtLeast(0L)
            val available = (coercedStock - reservedQty).coerceAtLeast(0L)
            val stockAvailability = ProductCollectionSchema.computeStockAvailability(coercedStock, reservedQty)

            val payload = mapOf(
                "sku" to cleanSku,
                "serialNo" to serialNo,
                "name" to productName.trim(),
                "productName" to productName.trim(),
                "category" to categoryName,
                "categoryId" to categoryId,
                "categoryName" to categoryName,
                "priceTiers" to mergedPriceTiers,
                "stockAvailability" to stockAvailability,
                "inStock" to (available > 0L),
                "imageUrl" to preservedImage,
                "description" to description.ifBlank { "SNL ${productName.trim()} ($grade) - $packagingSize" },
                "grade" to grade.trim(),
                "packagingSize" to packagingSize.trim(),
                "packagingUnit" to packagingUnit.trim().ifBlank { "Carton" },
                "cartonQty" to cartonQty.coerceAtLeast(1L),
                "minOrderQty" to minOrderQty.coerceAtLeast(1L),
                "mrpPkr" to mrpPkr.coerceAtLeast(0.0),
                "stockQty" to coercedStock,
                "reservedQty" to reservedQty,
                "isFeatured" to isFeatured,
                "isActive" to isActive,
                "createdAt" to createdAt,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            prodRef.set(payload).await()

            for ((groupCode, pricePkr) in groupPricesMap) {
                if (pricePkr > 0.0) {
                    setGroupPriceInternal(
                        sku = cleanSku,
                        productName = productName.trim(),
                        priceGroupCode = groupCode,
                        sellingPricePkr = pricePkr,
                        mrpPkr = mrpPkr,
                        minOrderQty = minOrderQty,
                        adminUid = adminUid,
                        importId = "MANUAL"
                    )
                }
            }
            Result.success(cleanSku)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, prodRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(sku: String): Result<Unit> {
        requireUserId()
        val prodRef = db.collection("products").document(sku)
        return try {
            prodRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, prodRef.path)
            Result.failure(e)
        }
    }

    private suspend fun setGroupPriceInternal(
        sku: String,
        productName: String,
        priceGroupCode: String,
        sellingPricePkr: Double,
        mrpPkr: Double,
        minOrderQty: Long,
        adminUid: String,
        importId: String
    ) {
        val priceDocId = "${sku}_${priceGroupCode}"
        val priceRef = db.collection("groupPrices").document(priceDocId)
        val existingSnap = priceRef.get().await()
        val oldPrice = if (existingSnap.exists()) (existingSnap.getDouble("sellingPricePkr") ?: 0.0) else 0.0
        val createdAt = if (existingSnap.exists()) {
            existingSnap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
        } else {
            FieldValue.serverTimestamp()
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        priceRef.set(
            mapOf(
                "sku" to sku,
                "priceGroupCode" to priceGroupCode,
                "sellingPricePkr" to sellingPricePkr,
                "mrpPkr" to mrpPkr.coerceAtLeast(0.0),
                "minOrderQty" to minOrderQty.coerceAtLeast(1L),
                "discountPercent" to 0.0,
                "effectiveFrom" to todayStr,
                "isActive" to true,
                "updatedBy" to adminUid,
                "createdAt" to createdAt,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        if (oldPrice != sellingPricePkr) {
            val histRef = db.collection("priceHistory").document()
            histRef.set(
                mapOf(
                    "sku" to sku,
                    "productName" to productName,
                    "priceGroupCode" to priceGroupCode,
                    "oldPricePkr" to oldPrice,
                    "newPricePkr" to sellingPricePkr,
                    "adminId" to adminUid,
                    "importId" to importId,
                    "effectiveDate" to todayStr,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    // --- 4. AI IMAGE & EXCEL PRICE LIST COMMIT + ROLLBACK ---

    /**
     * Commits verified rows from either the attached SNL Price List image, AI OCR reader,
     * Excel/CSV upload, or Google Sheets sync.
     * - Empty/null prices are NEVER written as zero (no change).
     * - Saves before-and-after price snapshots so the Super Admin can roll back the import in one tap!
     */
    suspend fun commitPriceListImport(
        fileName: String,
        sourceType: String,
        rows: List<ExtractedPriceRow>,
        targetSingleColumnGroup: String = "DISTRIBUTOR",
        alsoCopyToStandardDealerIfBlank: Boolean = true,
        allowStockUpdate: Boolean = false
    ): Result<String> {
        val adminUid = requireUserId()
        val importId = "IMP-${System.currentTimeMillis()}"
        return try {
            seedCoreMetadataIfMissing(adminUid)

            var createdCount = 0L
            var updatedCount = 0L
            var pricesUpdatedCount = 0L
            var skippedRows = 0L

            val beforePairs = mutableListOf<String>()
            val afterPairs = mutableListOf<String>()

            for (row in rows) {
                val cleanSku = row.sku.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "-")
                if (cleanSku.isBlank() || row.productName.isBlank()) {
                    skippedRows++
                    continue
                }

                val prodRef = db.collection("products").document(cleanSku)
                val existingSnap = prodRef.get().await()
                val isExisting = existingSnap.exists()

                val createdAt = if (isExisting) {
                    existingSnap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
                } else {
                    FieldValue.serverTimestamp()
                }
                val currentStock = if (isExisting) (existingSnap.getLong("stockQty") ?: 100L) else 100L
                val finalStock = if (allowStockUpdate && row.stockQty != null && row.stockQty >= 0) {
                    row.stockQty.toLong()
                } else {
                    currentStock
                }
                val reservedQty = if (isExisting) (existingSnap.getLong("reservedQty") ?: 0L) else 0L
                val preservedImage = existingSnap.getString("imageUrl") ?: ""
                val mrpVal = row.mrpPkr ?: (if (isExisting) existingSnap.getDouble("mrpPkr") ?: 0.0 else 0.0)

                // Collect applicable group prices to update (ignoring null/blank prices per rule "Empty cells mean NO CHANGE")
                val groupUpdates = mutableMapOf<String, Double>()
                row.distributorPricePkr?.takeIf { it > 0.0 }?.let { price ->
                    groupUpdates[targetSingleColumnGroup] = price
                    if (alsoCopyToStandardDealerIfBlank && row.standardDealerPricePkr == null) {
                        // Ensure Standard Dealer, Premium Dealer, and Special Dealer also have authorized prices
                        // if not separately specified on a single-column sheet
                        groupUpdates.putIfAbsent("DISTRIBUTOR", price)
                        groupUpdates.putIfAbsent("STANDARD_DEALER", price)
                        groupUpdates.putIfAbsent("PREMIUM_DEALER", price)
                        groupUpdates.putIfAbsent("SPECIAL_DEALER", price)
                    }
                }
                row.standardDealerPricePkr?.takeIf { it > 0.0 }?.let { groupUpdates["STANDARD_DEALER"] = it }
                row.premiumDealerPricePkr?.takeIf { it > 0.0 }?.let { groupUpdates["PREMIUM_DEALER"] = it }
                row.specialDealerPricePkr?.takeIf { it > 0.0 }?.let { groupUpdates["SPECIAL_DEALER"] = it }

                @Suppress("UNCHECKED_CAST")
                val existingTiers = if (isExisting) {
                    (existingSnap.get("priceTiers") as? Map<String, Number>)
                        ?.mapValues { it.value.toDouble() }
                        ?: emptyMap()
                } else emptyMap()
                val mergedPriceTiers = existingTiers.toMutableMap().apply {
                    putAll(groupUpdates)
                }
                val availableStock = (finalStock - reservedQty).coerceAtLeast(0L)
                val stockAvailability = ProductCollectionSchema.computeStockAvailability(finalStock, reservedQty)
                val catNameClean = row.categoryName.ifBlank { "MIX Brands" }

                prodRef.set(
                    mapOf(
                        "sku" to cleanSku,
                        "serialNo" to row.serialNo.toLong(),
                        "name" to row.productName.trim(),
                        "productName" to row.productName.trim(),
                        "category" to catNameClean,
                        "categoryId" to row.categoryCode.ifBlank { "MIX_BRANDS" },
                        "categoryName" to catNameClean,
                        "priceTiers" to mergedPriceTiers,
                        "stockAvailability" to stockAvailability,
                        "inStock" to (availableStock > 0L),
                        "imageUrl" to preservedImage,
                        "description" to "SNL ${row.productName.trim()} (${row.grade.trim()}) - ${row.packagingSize.trim()}",
                        "grade" to row.grade.trim(),
                        "packagingSize" to row.packagingSize.trim(),
                        "packagingUnit" to row.packagingUnit.ifBlank { "Carton" },
                        "cartonQty" to row.cartonQty.toLong().coerceAtLeast(1L),
                        "minOrderQty" to 1L,
                        "mrpPkr" to mrpVal,
                        "stockQty" to finalStock,
                        "reservedQty" to reservedQty,
                        "isFeatured" to (row.serialNo in listOf(1, 20, 25, 31, 37, 47, 54)),
                        "isActive" to true,
                        "createdAt" to createdAt,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                if (isExisting) updatedCount++ else createdCount++

                if (groupUpdates.isEmpty()) {
                    skippedRows++
                }

                for ((groupCode, newPrice) in groupUpdates) {
                    val priceDocId = "${cleanSku}_${groupCode}"
                    val priceSnap = db.collection("groupPrices").document(priceDocId).get().await()
                    val prevPrice = if (priceSnap.exists()) (priceSnap.getDouble("sellingPricePkr") ?: -1.0) else -1.0
                    beforePairs.add("$cleanSku|$groupCode|$prevPrice")
                    afterPairs.add("$cleanSku|$groupCode|$newPrice")

                    setGroupPriceInternal(
                        sku = cleanSku,
                        productName = row.productName.trim(),
                        priceGroupCode = groupCode,
                        sellingPricePkr = newPrice,
                        mrpPkr = mrpVal,
                        minOrderQty = 1L,
                        adminUid = adminUid,
                        importId = importId
                    )
                    pricesUpdatedCount++
                }
            }

            val importRef = db.collection("priceImports").document(importId)
            importRef.set(
                mapOf(
                    "fileName" to fileName,
                    "sourceType" to sourceType,
                    "adminId" to adminUid,
                    "totalRecords" to rows.size.toLong(),
                    "productsCreated" to createdCount,
                    "productsUpdated" to updatedCount,
                    "pricesUpdated" to pricesUpdatedCount,
                    "failedRows" to skippedRows,
                    "status" to "COMPLETED",
                    "beforeSnapshotData" to beforePairs.joinToString(";;").take(45000),
                    "afterSnapshotData" to afterPairs.joinToString(";;").take(45000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            createNotificationInternal(
                targetUserId = "ADMIN_ALL",
                title = "Price List Import Completed ($sourceType)",
                message = "$fileName: $createdCount created, $updatedCount updated, $pricesUpdatedCount group prices synchronized ($skippedRows flagged/unchanged).",
                type = "PRICE_IMPORT",
                referenceId = importId
            )

            Result.success(importId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "priceImports/$importId")
            Result.failure(e)
        }
    }

    /**
     * Rolls back a previous Price Import batch using its saved beforeSnapshotData
     * without deleting unrelated products or customer orders!
     */
    suspend fun rollbackPriceImport(importBatch: PriceImportBatch): Result<Unit> {
        val adminUid = requireUserId()
        return try {
            val entries = importBatch.beforeSnapshotData.split(";;").filter { it.isNotBlank() }
            for (entry in entries) {
                val parts = entry.split("|")
                if (parts.size >= 3) {
                    val sku = parts[0]
                    val groupCode = parts[1]
                    val prevPrice = parts[2].toDoubleOrNull() ?: -1.0
                    val priceDocId = "${sku}_${groupCode}"
                    val priceRef = db.collection("groupPrices").document(priceDocId)
                    if (prevPrice > 0.0) {
                        setGroupPriceInternal(
                            sku = sku,
                            productName = sku,
                            priceGroupCode = groupCode,
                            sellingPricePkr = prevPrice,
                            mrpPkr = 0.0,
                            minOrderQty = 1L,
                            adminUid = adminUid,
                            importId = "ROLLBACK_${importBatch.id}"
                        )
                    } else {
                        // Was newly added in that import -> deactivate price record
                        val snap = priceRef.get().await()
                        if (snap.exists()) {
                            priceRef.update(
                                mapOf(
                                    "isActive" to false,
                                    "updatedBy" to adminUid,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }
                }
            }
            db.collection("priceImports").document(importBatch.id).update(
                mapOf(
                    "status" to "ROLLED_BACK",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "priceImports/${importBatch.id}")
            Result.failure(e)
        }
    }

    // --- 5. SHOPPING CART & ORDER PLACEMENT WITH IMMUTABLE PRICE SNAPSHOT ---

    /**
     * Places a B2B order after verifying:
     * 1. Authenticated user & ACTIVE account status
     * 2. Live Firestore group prices match cart prices (if changed, returns a price-change confirmation error so customer is alerted!)
     * 3. Minimum order quantities
     * 4. Saves immutable priceSnapshotText so future Excel imports never alter past orders!
     */
    suspend fun placeCustomerOrder(
        userProfile: UserProfile,
        cartItems: List<CartItem>,
        deliveryAddress: String,
        city: String,
        phone: String,
        orderInstructions: String,
        paymentMethod: String,
        deliveryChargesPkr: Double = 0.0,
        discountPkr: Double = 0.0
    ): Result<Order> {
        val uid = requireUserId()
        if (userProfile.accountStatus != "ACTIVE" && userProfile.role != "SUPER_ADMIN") {
            return Result.failure(IllegalStateException("Your account is ${userProfile.accountStatus}. Only approved ACTIVE accounts can place orders."))
        }
        if (cartItems.isEmpty()) {
            return Result.failure(IllegalArgumentException("Shopping cart is empty."))
        }

        return try {
            // Verify live prices in Firestore to detect any price change between adding to cart and checkout
            val verifiedLineItems = mutableListOf<OrderLineItem>()
            for (cartItem in cartItems) {
                val priceDocId = "${cartItem.product.sku}_${userProfile.priceGroupId}"
                val livePriceSnap = db.collection("groupPrices").document(priceDocId).get().await()
                val livePrice = if (livePriceSnap.exists() && livePriceSnap.getBoolean("isActive") == true) {
                    livePriceSnap.getDouble("sellingPricePkr") ?: cartItem.unitPricePkr
                } else {
                    cartItem.unitPricePkr
                }

                if (livePrice <= 0.0) {
                    return Result.failure(IllegalStateException("Price for ${cartItem.product.productName} is not assigned for group ${userProfile.priceGroupId}."))
                }
                if (kotlin.math.abs(livePrice - cartItem.unitPricePkr) > 0.01) {
                    return Result.failure(
                        IllegalStateException(
                            "PRICE_UPDATED: The price for ${cartItem.product.productName} was updated from PKR ${cartItem.unitPricePkr.toLong()} to PKR ${livePrice.toLong()}. Your cart has been refreshed—please review and confirm."
                        )
                    )
                }

                verifiedLineItems.add(
                    OrderLineItem(
                        sku = cartItem.product.sku,
                        productName = cartItem.product.productName,
                        packagingSize = cartItem.product.packagingSize,
                        grade = cartItem.product.grade,
                        quantity = cartItem.quantity,
                        unitPricePkr = livePrice,
                        lineTotalPkr = livePrice * cartItem.quantity
                    )
                )
            }

            val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val randomSuffix = (1000..9999).random()
            val orderNumber = "SNL-$datePart-$randomSuffix"
            val docId = "ord_${datePart}_$randomSuffix"

            val subtotal = verifiedLineItems.sumOf { it.lineTotalPkr }
            val grandTotal = (subtotal - discountPkr + deliveryChargesPkr).coerceAtLeast(0.0)
            val totalCartons = verifiedLineItems.sumOf { it.quantity }.toLong()
            val itemsSummary = verifiedLineItems.joinToString(", ") { "${it.productName} (${it.packagingSize}) x${it.quantity}" }
            val snapshotText = OrderLineItem.encodeList(verifiedLineItems)

            val docRef = db.collection("orders").document(docId)
            val payload = mapOf(
                "orderNumber" to orderNumber,
                "customerId" to uid,
                "customerCode" to userProfile.customerCode.ifBlank { "DLR-${uid.take(4).uppercase()}" },
                "customerName" to userProfile.fullName,
                "businessName" to userProfile.businessName,
                "customerType" to userProfile.role,
                "phone" to phone.trim().ifBlank { userProfile.phone },
                "city" to city.trim().ifBlank { userProfile.city },
                "deliveryAddress" to deliveryAddress.trim().ifBlank { userProfile.address },
                "orderInstructions" to orderInstructions.trim(),
                "priceGroupId" to userProfile.priceGroupId,
                "itemsSummary" to itemsSummary.take(3900),
                "itemsCount" to verifiedLineItems.size.toLong(),
                "totalCartons" to totalCartons,
                "priceSnapshotText" to snapshotText,
                "subtotal" to subtotal,
                "discount" to discountPkr,
                "deliveryCharges" to deliveryChargesPkr,
                "grandTotal" to grandTotal,
                "paymentMethod" to paymentMethod,
                "paymentStatus" to "UNPAID",
                "orderStatus" to "PENDING_ADMIN_APPROVAL",
                "visibleToCustomer" to true,
                "rejectionReason" to "",
                "approvedBy" to "",
                "rejectedBy" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            val createdOrder = Order(
                id = docId,
                orderNumber = orderNumber,
                customerId = uid,
                customerCode = userProfile.customerCode,
                customerName = userProfile.fullName,
                businessName = userProfile.businessName,
                customerType = userProfile.role,
                phone = phone.trim().ifBlank { userProfile.phone },
                city = city.trim().ifBlank { userProfile.city },
                deliveryAddress = deliveryAddress.trim().ifBlank { userProfile.address },
                orderInstructions = orderInstructions.trim(),
                priceGroupId = userProfile.priceGroupId,
                itemsSummary = itemsSummary,
                itemsCount = verifiedLineItems.size.toLong(),
                totalCartons = totalCartons,
                priceSnapshotText = snapshotText,
                subtotal = subtotal,
                discount = discountPkr,
                deliveryCharges = deliveryChargesPkr,
                grandTotal = grandTotal,
                paymentMethod = paymentMethod,
                paymentStatus = "UNPAID",
                orderStatus = "PENDING_ADMIN_APPROVAL",
                visibleToCustomer = true
            )

            // Notify Admin in-app & log WhatsApp notification
            createNotificationInternal(
                targetUserId = "ADMIN_ALL",
                title = "New Order Received: $orderNumber",
                message = "${userProfile.businessName} (${userProfile.city}) placed an order for PKR ${grandTotal.toLong()} — Pending Admin Approval.",
                type = "NEW_ORDER",
                referenceId = docId
            )

            logWhatsAppNotificationInternal(
                order = createdOrder,
                eventType = "NEW_ORDER",
                deliveryMode = "CLICK_TO_CHAT_PREPARED",
                apiResponse = "Order summary prepared for SNL WhatsApp (+92 310 100 52 53)"
            )

            Result.success(createdOrder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "orders")
            Result.failure(e)
        }
    }

    // --- 6. ADMIN ORDER ACCEPTANCE, REJECTION (SOFT-DELETE ARCHIVE) & STATUS UPDATES ---

    /**
     * Customer order query: strictly filters by `customerId == uid` AND `visibleToCustomer == true`
     * so rejected orders immediately disappear from the customer's order screens!
     */
    fun observeCustomerOrders(customerId: String): Flow<List<Order>> {
        val path = "orders"
        return db.collection(path)
            .whereEqualTo("customerId", customerId)
            .whereEqualTo("visibleToCustomer", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                throw error
            }
    }

    /**
     * Resolves either an order document ID (`ord_YYYYMMDD_XXXX`) or a human-readable order number
     * (`SNL-YYYYMMDD-XXXX`) to its canonical Firestore document ID in `/orders/{orderId}`.
     */
    fun resolveOrderDocId(orderIdOrNumber: String): String {
        val trimmed = orderIdOrNumber.trim()
        return if (trimmed.startsWith("SNL-", ignoreCase = true)) {
            "ord_" + trimmed.substring(4).replace('-', '_')
        } else {
            trimmed
        }
    }

    /**
     * Observes the real-time status and details of a specific order document in `/orders/{orderId}`
     * using a Firestore snapshot listener (`addSnapshotListener`) wrapped in `callbackFlow`.
     */
    fun observeOrderRealtime(orderId: String): Flow<Order?> = callbackFlow {
        val resolvedDocId = resolveOrderDocId(orderId)
        if (resolvedDocId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val path = "orders/$resolvedDocId"
        val registration = db.collection("orders")
            .document(resolvedDocId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logIfSignedIn(error, OperationType.GET, path)
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val order = snapshot.toObject(
                        Order::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    trySend(order)
                } else {
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Admin order query: returns all orders (Pending, Confirmed, Dispatched, and Rejected Archive).
     */
    fun observeAllOrdersForAdmin(): Flow<List<Order>> {
        val path = "orders"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                throw error
            }
    }

    /**
     * Section 13.A: ACCEPT ORDER
     * Verifies order is pending, reserves stock for each item, sets status to CONFIRMED,
     * records Admin ID & timestamp, and notifies the customer.
     */
    suspend fun adminAcceptOrder(order: Order): Result<Unit> {
        val adminUid = requireUserId()
        if (order.orderStatus != "PENDING_ADMIN_APPROVAL") {
            return Result.failure(IllegalStateException("Only PENDING_ADMIN_APPROVAL orders can be accepted."))
        }
        val orderRef = db.collection("orders").document(order.id)
        return try {
            // Reserve stock for each line item
            for (item in order.parsedItems()) {
                val prodRef = db.collection("products").document(item.sku)
                val prodSnap = prodRef.get().await()
                if (prodSnap.exists()) {
                    val currentStock = prodSnap.getLong("stockQty") ?: 0L
                    val currentReserved = prodSnap.getLong("reservedQty") ?: 0L
                    val available = (currentStock - currentReserved).coerceAtLeast(0L)
                    if (available < item.quantity) {
                        return Result.failure(
                            IllegalStateException("Insufficient available stock for ${item.productName} (${item.sku}): Available $available, Required ${item.quantity}")
                        )
                    }
                    val nextReserved = currentReserved + item.quantity
                    val nextAvail = (currentStock - nextReserved).coerceAtLeast(0L)
                    prodRef.update(
                        mapOf(
                            "reservedQty" to nextReserved,
                            "stockAvailability" to ProductCollectionSchema.computeStockAvailability(currentStock, nextReserved),
                            "inStock" to (nextAvail > 0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()

                    db.collection("inventoryMovements").document().set(
                        mapOf(
                            "sku" to item.sku,
                            "productName" to item.productName,
                            "movementType" to "ORDER_RESERVED",
                            "quantityDelta" to item.quantity.toLong(),
                            "previousStock" to available,
                            "newStock" to (available - item.quantity),
                            "referenceId" to order.orderNumber,
                            "note" to "Reserved upon Admin order acceptance",
                            "performedBy" to adminUid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            orderRef.update(
                mapOf(
                    "orderStatus" to "CONFIRMED",
                    "approvedBy" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            createNotificationInternal(
                targetUserId = order.customerId,
                title = "Order Confirmed: ${order.orderNumber}",
                message = "Your order ${order.orderNumber} (PKR ${order.grandTotal.toLong()}) has been approved by SAIFI NATIONAL LUBRICANTS.",
                type = "ORDER_ACCEPTED",
                referenceId = order.id
            )

            logWhatsAppNotificationInternal(
                order = order.copy(orderStatus = "CONFIRMED"),
                eventType = "ORDER_ACCEPTED",
                deliveryMode = "CLICK_TO_CHAT_PREPARED",
                apiResponse = "Acceptance notification prepared for customer ${order.phone}"
            )

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, orderRef.path)
            Result.failure(e)
        }
    }

    /**
     * Section 13.B: REJECT ORDER (Soft-Deletion to Admin-only Rejected Orders Archive)
     * - Sets orderStatus = REJECTED
     * - Sets visibleToCustomer = false so the order immediately disappears from the customer's app!
     * - Preserves original order details, customer info, price snapshot, rejection reason, rejectedBy, and timestamp in the Admin Rejected Orders Archive.
     * - Terminal state lock in firestore.rules ensures a rejected order cannot be accepted later.
     */
    suspend fun adminRejectOrder(order: Order, rejectionReason: String): Result<Unit> {
        val adminUid = requireUserId()
        if (order.orderStatus == "REJECTED") {
            return Result.failure(IllegalStateException("Order is already rejected."))
        }
        val orderRef = db.collection("orders").document(order.id)
        return try {
            // If stock was previously reserved, release it
            if (order.orderStatus in setOf("CONFIRMED", "PROCESSING", "READY_FOR_DISPATCH")) {
                for (item in order.parsedItems()) {
                    val prodRef = db.collection("products").document(item.sku)
                    val prodSnap = prodRef.get().await()
                    if (prodSnap.exists()) {
                        val currentStock = prodSnap.getLong("stockQty") ?: 0L
                        val currentReserved = prodSnap.getLong("reservedQty") ?: 0L
                        val newReserved = (currentReserved - item.quantity).coerceAtLeast(0L)
                        val nextAvail = (currentStock - newReserved).coerceAtLeast(0L)
                        prodRef.update(
                            mapOf(
                                "reservedQty" to newReserved,
                                "stockAvailability" to ProductCollectionSchema.computeStockAvailability(currentStock, newReserved),
                                "inStock" to (nextAvail > 0L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }

            val cleanReason = rejectionReason.trim().ifBlank { "Order declined by SNL Administration" }
            orderRef.update(
                mapOf(
                    "orderStatus" to "REJECTED",
                    "visibleToCustomer" to false, // Soft deletion: disappears from customer app, moves to Admin Rejected Archive!
                    "rejectionReason" to cleanReason,
                    "rejectedBy" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            createNotificationInternal(
                targetUserId = order.customerId,
                title = "Order Update: ${order.orderNumber}",
                message = "Your order ${order.orderNumber} could not be processed ($cleanReason). Please submit a new order if needed.",
                type = "ORDER_REJECTED",
                referenceId = order.id
            )

            logWhatsAppNotificationInternal(
                order = order.copy(orderStatus = "REJECTED", rejectionReason = cleanReason),
                eventType = "ORDER_REJECTED",
                deliveryMode = "CLICK_TO_CHAT_PREPARED",
                apiResponse = "Rejection notification logged for ${order.phone}"
            )

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, orderRef.path)
            Result.failure(e)
        }
    }

    /**
     * Updates order status through PROCESSING -> READY_FOR_DISPATCH -> DISPATCHED -> DELIVERED,
     * and deducts physical stock when DISPATCHED.
     */
    suspend fun adminUpdateOrderStatus(
        order: Order,
        newStatus: String,
        newPaymentStatus: String = order.paymentStatus
    ): Result<Unit> {
        val adminUid = requireUserId()
        if (order.orderStatus == "REJECTED") {
            return Result.failure(IllegalStateException("Rejected orders are locked in archive and cannot be modified."))
        }
        val orderRef = db.collection("orders").document(order.id)
        return try {
            if (newStatus == "DISPATCHED" && order.orderStatus != "DISPATCHED") {
                for (item in order.parsedItems()) {
                    val prodRef = db.collection("products").document(item.sku)
                    val prodSnap = prodRef.get().await()
                    if (prodSnap.exists()) {
                        val currentStock = prodSnap.getLong("stockQty") ?: 0L
                        val currentReserved = prodSnap.getLong("reservedQty") ?: 0L
                        val newStock = (currentStock - item.quantity).coerceAtLeast(0L)
                        val newReserved = (currentReserved - item.quantity).coerceAtLeast(0L)
                        val nextAvail = (newStock - newReserved).coerceAtLeast(0L)
                        prodRef.update(
                            mapOf(
                                "stockQty" to newStock,
                                "reservedQty" to newReserved,
                                "stockAvailability" to ProductCollectionSchema.computeStockAvailability(newStock, newReserved),
                                "inStock" to (nextAvail > 0L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()

                        db.collection("inventoryMovements").document().set(
                            mapOf(
                                "sku" to item.sku,
                                "productName" to item.productName,
                                "movementType" to "ORDER_DEDUCTED",
                                "quantityDelta" to -item.quantity.toLong(),
                                "previousStock" to currentStock,
                                "newStock" to newStock,
                                "referenceId" to order.orderNumber,
                                "note" to "Stock deducted on order dispatch",
                                "performedBy" to adminUid,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }

            orderRef.update(
                mapOf(
                    "orderStatus" to newStatus,
                    "paymentStatus" to newPaymentStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val normalizedUpper = newStatus.trim().uppercase(Locale.US)
            val isFcmPushTarget = normalizedUpper == "DISPATCHED" || normalizedUpper == "DELIVERED"
            val notifType = when (normalizedUpper) {
                "DISPATCHED" -> "FCM_ORDER_DISPATCHED"
                "DELIVERED" -> "FCM_ORDER_DELIVERED"
                else -> "ORDER_STATUS"
            }
            val canonicalLabel = com.example.notifications.SnlFcmNotificationManager.formatCanonicalPushStatus(newStatus)
            val notifTitle = if (isFcmPushTarget) {
                "FCM Alert: Order ${order.orderNumber} $canonicalLabel"
            } else {
                "Order ${order.orderNumber}: ${newStatus.replace('_', ' ')}"
            }
            val notifMessage = when (normalizedUpper) {
                "DISPATCHED" ->
                    "Great news ${order.businessName.ifBlank { order.customerName }}! Your SNL Lubricants order ${order.orderNumber} (${order.totalCartons} cartons) has been Dispatched to ${order.city}."
                "DELIVERED" ->
                    "Your SNL Lubricants order ${order.orderNumber} has been Delivered at ${order.deliveryAddress.ifBlank { order.city }}. Thank you for partnering with SAIFI National Lubricants!"
                else ->
                    "Your SNL order ${order.orderNumber} status is now ${newStatus.replace('_', ' ')}."
            }

            createNotificationInternal(
                targetUserId = order.customerId,
                title = notifTitle,
                message = notifMessage,
                type = notifType,
                referenceId = order.id
            )

            if (isFcmPushTarget) {
                appContext?.let { ctx ->
                    com.example.notifications.SnlFcmNotificationManager.triggerOrderStatusPushNotification(
                        context = ctx,
                        order = order.copy(orderStatus = newStatus, paymentStatus = newPaymentStatus),
                        newStatus = newStatus,
                        forceEvenIfAlreadyNotified = true
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, orderRef.path)
            Result.failure(e)
        }
    }

    // --- 7. INVENTORY MANAGEMENT ---

    suspend fun adjustProductStock(
        product: Product,
        newPhysicalStock: Long,
        note: String
    ): Result<Unit> {
        val adminUid = requireUserId()
        if (newPhysicalStock < 0L) {
            return Result.failure(IllegalArgumentException("Stock cannot be negative."))
        }
        val prodRef = db.collection("products").document(product.sku)
        return try {
            val delta = newPhysicalStock - product.stockQty
            val nextAvail = (newPhysicalStock - product.reservedQty).coerceAtLeast(0L)
            prodRef.update(
                mapOf(
                    "stockQty" to newPhysicalStock,
                    "stockAvailability" to ProductCollectionSchema.computeStockAvailability(newPhysicalStock, product.reservedQty),
                    "inStock" to (nextAvail > 0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("inventoryMovements").document().set(
                mapOf(
                    "sku" to product.sku,
                    "productName" to product.productName,
                    "movementType" to "MANUAL_ADJUSTMENT",
                    "quantityDelta" to delta,
                    "previousStock" to product.stockQty,
                    "newStock" to newPhysicalStock,
                    "referenceId" to "MANUAL",
                    "note" to note.ifBlank { "Manual Admin stock update" },
                    "performedBy" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, prodRef.path)
            Result.failure(e)
        }
    }

    fun observeInventoryMovements(): Flow<List<InventoryMovement>> {
        val path = "inventoryMovements"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(InventoryMovement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observePriceImports(): Flow<List<PriceImportBatch>> {
        val path = "priceImports"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(PriceImportBatch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observePriceHistory(): Flow<List<PriceHistoryRecord>> {
        val path = "priceHistory"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(PriceHistoryRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                throw error
            }
    }

    // --- 8. NOTIFICATIONS, WHATSAPP LOGS & APP SETTINGS ---

    fun observeNotificationsForUser(userId: String, isAdmin: Boolean): Flow<List<AppNotification>> {
        val path = "notifications"
        val query = if (isAdmin) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("targetUserId", userId)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                throw error
            }
    }

    /**
     * Real-time Firestore snapshot listener (`addSnapshotListener` inside `callbackFlow`) observing
     * customer support chat messages for the logged-in user.
     */
    fun observeSupportChatMessages(
        userId: String = auth.currentUser?.uid.orEmpty()
    ): Flow<List< com.example.data.model.SupportChatMessage >> = callbackFlow {
        val resolvedUid = userId.ifBlank { auth.currentUser?.uid.orEmpty() }
        if (resolvedUid.isBlank() || auth.currentUser == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val path = "notifications"
        val query = db.collection(path).whereEqualTo("targetUserId", resolvedUid)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                logIfSignedIn(error, OperationType.LIST, path)
                close(error)
                return@addSnapshotListener
            }
            val docs = snapshot?.documents.orEmpty()
            val chatMessages = docs.mapNotNull { doc ->
                val notif = doc.toObject(
                    AppNotification::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return@mapNotNull null

                if (!notif.type.startsWith("SUPPORT_CHAT")) return@mapNotNull null

                val titleParts = notif.title.split("|")
                val roleFromTitle = titleParts.getOrNull(0)?.uppercase(Locale.US).orEmpty()
                val senderRole = when {
                    notif.type == "SUPPORT_CHAT_AGENT" || roleFromTitle == "SUPPORT" -> "SUPPORT"
                    else -> "CUSTOMER"
                }
                val senderName = titleParts.getOrNull(1)?.ifBlank {
                    if (senderRole == "SUPPORT") "SNL Customer Support" else "Authorized Partner"
                } ?: if (senderRole == "SUPPORT") "SNL Customer Support" else "Authorized Partner"
                val businessName = titleParts.getOrNull(2).orEmpty()

                com.example.data.model.SupportChatMessage(
                    id = doc.id,
                    threadUserId = notif.targetUserId,
                    senderRole = senderRole,
                    senderName = senderName,
                    businessName = businessName,
                    messageText = notif.message,
                    isRead = notif.isRead,
                    createdAt = notif.createdAt
                )
            }.sortedWith(
                compareBy<com.example.data.model.SupportChatMessage> { it.createdAt?.seconds ?: Long.MAX_VALUE }
                    .thenBy { it.createdAt?.nanoseconds ?: 0 }
                    .thenBy { it.id }
            )
            trySend(chatMessages)
        }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * Sends a real-time customer support chat message to Firestore and generates a contextual
     * SNL Support Desk response in Firestore so the user has an immediate live support thread.
     */
    suspend fun sendSupportChatMessage(
        userProfile: UserProfile,
        messageText: String,
        includeSupportDeskAutoReply: Boolean = true
    ): Result<Unit> {
        val uid = requireUserId()
        val trimmed = messageText.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Support message cannot be empty."))
        }

        val senderName = userProfile.fullName.ifBlank { "Authorized Partner" }.replace("|", "/").take(45)
        val businessName = userProfile.businessName.ifBlank { userProfile.role }.replace("|", "/").take(55)
        val userTitle = "CUSTOMER|$senderName|$businessName"

        val userMsgRef = db.collection("notifications").document()
        val userPayload = mapOf(
            "targetUserId" to uid,
            "title" to userTitle.take(150),
            "message" to trimmed.take(950),
            "type" to "SUPPORT_CHAT_USER",
            "referenceId" to uid,
            "isRead" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            userMsgRef.set(userPayload).await()

            // Notify SNL HQ Admins as well
            createNotificationInternal(
                targetUserId = "ADMIN_ALL",
                title = "Support Chat: $businessName ($senderName)",
                message = trimmed.take(950),
                type = "SUPPORT_CHAT_USER",
                referenceId = uid
            )

            if (includeSupportDeskAutoReply) {
                val replyText = buildContextualSupportReply(trimmed, userProfile)
                val agentTitle = "SUPPORT|SNL Customer Support Desk|Karachi HQ (+92 310 100 52 53)"
                db.collection("notifications").document().set(
                    mapOf(
                        "targetUserId" to uid,
                        "title" to agentTitle,
                        "message" to replyText.take(950),
                        "type" to "SUPPORT_CHAT_AGENT",
                        "referenceId" to uid,
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, userMsgRef.path)
            Result.failure(e)
        }
    }

    private fun buildContextualSupportReply(
        query: String,
        userProfile: UserProfile
    ): String {
        val q = query.lowercase(Locale.US)
        val tier = userProfile.priceGroupId.ifBlank { "STANDARD_DEALER" }
        val biz = userProfile.businessName.ifBlank { "Valued Partner" }
        return when {
            q.contains("price") || q.contains("rate") || q.contains("discount") || q.contains("قیمت") -> {
                "Wa-Alaikum-Salaam $biz! Your account is configured under the $tier tier. All prices shown in your catalogue reflect your locked $tier rate in PKR. For special bulk carton rebates (50+ cartons), our Karachi HQ sales officer has been notified of your message."
            }
            q.contains("order") || q.contains("dispatch") || q.contains("delivery") || q.contains("track") || q.contains("آرڈر") -> {
                "Thank you for contacting SNL Dispatch Support, $biz. We have logged your inquiry for priority review. Standard Karachi & Sindh dispatches depart within 24 hours of Admin confirmation, and up-country bilty details are shared in real time under Order Tracking."
            }
            q.contains("stock") || q.contains("available") || q.contains("20w-50") || q.contains("10w-40") || q.contains("اسٹاک") -> {
                "SNL Warehouse Update for $biz: Live stock availability is synchronized directly in your catalogue cards. Your support ticket has been routed to the Karachi Inventory Desk (+92 310 100 52 53) to reserve your requested cartons."
            }
            else -> {
                "Assalam-o-Alaikum $biz! Your message has been received by SAIFI National Lubricants Customer Support (Karachi HQ). An authorized B2B representative is reviewing your thread ($tier tier) and will assist you shortly. For urgent dispatch helpline: +92 310 100 52 53."
            }
        }
    }

    fun observeWhatsAppLogs(): Flow<List<WhatsAppDeliveryLog>> {
        val path = "whatsappDeliveryLogs"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(WhatsAppDeliveryLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeCompanySettings(): Flow<CompanySettings> {
        val path = "appSettings/global"
        return db.collection("appSettings").document("global")
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(CompanySettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: CompanySettings()
                } else {
                    CompanySettings()
                }
            }
            .catch { error ->
                logIfSignedIn(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun saveCompanySettings(settings: CompanySettings): Result<Unit> {
        val adminUid = requireUserId()
        val docRef = db.collection("appSettings").document("global")
        return try {
            docRef.set(
                mapOf(
                    "companyName" to settings.companyName.trim(),
                    "brandName" to settings.brandName.trim(),
                    "companyPhone" to settings.companyPhone.trim(),
                    "companyCity" to settings.companyCity.trim(),
                    "whatsappBusinessAccountId" to settings.whatsappBusinessAccountId.trim(),
                    "whatsappPhoneNumberId" to settings.whatsappPhoneNumberId.trim(),
                    "companyWhatsappNumber" to settings.companyWhatsappNumber.trim(),
                    "whatsappNotificationsEnabled" to settings.whatsappNotificationsEnabled,
                    "googleSheetUrl" to settings.googleSheetUrl.trim(),
                    "googleSheetWorksheet" to settings.googleSheetWorksheet.trim(),
                    "googleSheetLastSync" to settings.googleSheetLastSync.trim(),
                    "googleSheetAutoSync" to settings.googleSheetAutoSync,
                    "defaultDeliveryChargesPkr" to settings.defaultDeliveryChargesPkr.coerceAtLeast(0.0),
                    "lowStockThreshold" to settings.lowStockThreshold.coerceAtLeast(1L),
                    "allowExcelStockUpdate" to settings.allowExcelStockUpdate,
                    "updatedBy" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    private suspend fun createNotificationInternal(
        targetUserId: String,
        title: String,
        message: String,
        type: String,
        referenceId: String
    ) {
        try {
            db.collection("notifications").document().set(
                mapOf(
                    "targetUserId" to targetUserId,
                    "title" to title.take(150),
                    "message" to message.take(950),
                    "type" to type,
                    "referenceId" to referenceId,
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Non-blocking notification helper
        }
    }

    private suspend fun logWhatsAppNotificationInternal(
        order: Order,
        eventType: String,
        deliveryMode: String,
        apiResponse: String
    ) {
        try {
            val uid = auth.currentUser?.uid ?: "SYSTEM"
            val msg = InvoiceAndReportPdfGenerator.buildWhatsAppOrderMessage(order)
            db.collection("whatsappDeliveryLogs").document().set(
                mapOf(
                    "orderNumber" to order.orderNumber,
                    "recipientPhone" to "+923101005253",
                    "eventType" to eventType,
                    "deliveryMode" to deliveryMode,
                    "messagePreview" to msg.take(3900),
                    "apiResponse" to apiResponse.take(900),
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Non-blocking log helper
        }
    }

    private fun logIfSignedIn(error: Throwable, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser != null) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
