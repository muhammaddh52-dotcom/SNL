package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.R
import com.example.data.model.Product
import com.example.data.seed.AttachedPriceListCatalog
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/**
 * Configures Firestore offline persistence (`PersistentCacheSettings` with `CACHE_SIZE_UNLIMITED`)
 * and local catalog/cart fallback caching so the product catalog and shopping cart remain
 * accessible and editable even when the device is offline.
 */
object FirestorePersistenceConfig {

    private val configuredInstances: MutableSet<Int> =
        Collections.synchronizedSet(mutableSetOf())

    private val _isSimulatedOffline = MutableStateFlow(false)
    val isSimulatedOffline: StateFlow<Boolean> = _isSimulatedOffline.asStateFlow()

    private val _isServingFromOfflineCache = MutableStateFlow(false)
    val isServingFromOfflineCache: StateFlow<Boolean> = _isServingFromOfflineCache.asStateFlow()

    private const val PREFS_OFFLINE_CATALOG = "snl_offline_catalog_cache"
    private const val KEY_CACHED_PRODUCTS_JSON = "cached_products_json"

    /**
     * Enables unlimited on-disk offline persistence (`PersistentCacheSettings`) on the supplied
     * [FirebaseFirestore] instance if it has not yet been started.
     */
    fun configureOfflinePersistence(db: FirebaseFirestore): FirebaseFirestore {
        val identityHash = System.identityHashCode(db)
        if (configuredInstances.add(identityHash)) {
            try {
                val persistentCache = PersistentCacheSettings.newBuilder()
                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
                val settings = FirebaseFirestoreSettings.Builder(db.firestoreSettings)
                    .setLocalCacheSettings(persistentCache)
                    .build()
                db.firestoreSettings = settings
            } catch (_: IllegalStateException) {
                // Firestore instance was already started; persistent cache is already active by default.
            } catch (_: Exception) {
                // Ignore if running in a mock/test environment where settings cannot be mutated.
            }
        }
        return db
    }

    /**
     * Returns the named [FirebaseFirestore] instance (`R.string.firestore_database_id`) configured
     * with unlimited offline disk persistence.
     */
    fun getConfiguredFirestore(context: Context): FirebaseFirestore {
        val dbId = context.applicationContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(dbId)
        return configureOfflinePersistence(db)
    }

    /**
     * Returns the named [FirebaseFirestore] instance by `databaseId` configured with unlimited
     * offline disk persistence.
     */
    fun getConfiguredFirestore(databaseId: String): FirebaseFirestore {
        val db = Firebase.firestore(databaseId)
        return configureOfflinePersistence(db)
    }

    fun updateCacheSourceStatus(isFromCache: Boolean) {
        _isServingFromOfflineCache.value = isFromCache
    }

    /**
     * Toggles Firestore network connectivity (`disableNetwork()` / `enableNetwork()`) so users
     * can operate or test the app in offline mode while keeping the catalog and cart accessible.
     */
    fun setFirestoreOfflineMode(db: FirebaseFirestore, offline: Boolean) {
        _isSimulatedOffline.value = offline
        if (offline) {
            _isServingFromOfflineCache.value = true
            db.disableNetwork()
        } else {
            db.enableNetwork()
        }
    }

    /**
     * Checks current OS network connectivity via [ConnectivityManager].
     */
    fun isDeviceNetworkConnected(context: Context): Boolean {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Real-time Flow emitting `true` when the app is online and `false` when the device is offline
     * (either via OS network disconnect or Firestore offline mode).
     */
    fun observeOnlineStatus(context: Context): Flow<Boolean> {
        val osNetworkFlow = callbackFlow {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                trySend(true)
                close()
                return@callbackFlow
            }

            trySend(isDeviceNetworkConnected(context))

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(true)
                }

                override fun onLost(network: Network) {
                    trySend(isDeviceNetworkConnected(context))
                }
            }

            try {
                cm.registerDefaultNetworkCallback(callback)
            } catch (_: Exception) {
                trySend(true)
            }

            awaitClose {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                }
            }
        }

        return osNetworkFlow.combine(_isSimulatedOffline) { osOnline, simulatedOffline ->
            osOnline && !simulatedOffline
        }
    }

    /**
     * Saves the latest list of [Product] items to local storage (`SharedPreferences`) as a durable
     * mirror alongside Firestore's persistent disk cache.
     */
    fun cacheProductsLocally(context: Context, products: List<Product>) {
        if (products.isEmpty()) return
        try {
            val sp = context.applicationContext
                .getSharedPreferences(PREFS_OFFLINE_CATALOG, Context.MODE_PRIVATE)
            val arr = JSONArray()
            for (p in products) {
                val tiersObj = JSONObject()
                for ((k, v) in p.priceTiers) {
                    tiersObj.put(k, v)
                }
                val obj = JSONObject().apply {
                    put("id", p.id.ifBlank { p.sku })
                    put("sku", p.sku)
                    put("serialNo", p.serialNo)
                    put("name", p.displayName)
                    put("productName", p.productName.ifBlank { p.displayName })
                    put("category", p.displayCategory)
                    put("categoryId", p.categoryId)
                    put("categoryName", p.categoryName.ifBlank { p.displayCategory })
                    put("grade", p.grade)
                    put("packagingSize", p.packagingSize)
                    put("packagingUnit", p.packagingUnit)
                    put("cartonQty", p.cartonQty)
                    put("minOrderQty", p.minOrderQty)
                    put("mrpPkr", p.mrpPkr)
                    put("stockQty", p.stockQty)
                    put("reservedQty", p.reservedQty)
                    put("stockAvailability", p.stockAvailability)
                    put("inStock", p.inStock)
                    put("isFeatured", p.isFeatured)
                    put("isActive", p.isActive)
                    put("priceTiers", tiersObj)
                }
                arr.put(obj)
            }
            sp.edit().putString(KEY_CACHED_PRODUCTS_JSON, arr.toString()).apply()
        } catch (_: Exception) {
            // Non-blocking cache write
        }
    }

    /**
     * Loads cached [Product] records from local storage, or synthesizes the 59-product SNL catalog
     * from [AttachedPriceListCatalog] if the device went offline prior to initial sync.
     */
    fun getOfflineFallbackProducts(context: Context? = null): List<Product> {
        if (context != null) {
            try {
                val sp = context.applicationContext
                    .getSharedPreferences(PREFS_OFFLINE_CATALOG, Context.MODE_PRIVATE)
                val raw = sp.getString(KEY_CACHED_PRODUCTS_JSON, null)
                if (!raw.isNullOrBlank()) {
                    val arr = JSONArray(raw)
                    val list = mutableListOf<Product>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val tiersObj = obj.optJSONObject("priceTiers")
                        val tiers = mutableMapOf<String, Double>()
                        if (tiersObj != null) {
                            val keys = tiersObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                tiers[k] = tiersObj.optDouble(k, 0.0)
                            }
                        }
                        list.add(
                            Product(
                                id = obj.optString("id"),
                                sku = obj.optString("sku"),
                                serialNo = obj.optLong("serialNo", 0L),
                                name = obj.optString("name"),
                                productName = obj.optString("productName"),
                                category = obj.optString("category"),
                                categoryId = obj.optString("categoryId"),
                                categoryName = obj.optString("categoryName"),
                                priceTiers = tiers,
                                stockAvailability = obj.optString("stockAvailability", "IN_STOCK"),
                                inStock = obj.optBoolean("inStock", true),
                                grade = obj.optString("grade"),
                                packagingSize = obj.optString("packagingSize"),
                                packagingUnit = obj.optString("packagingUnit", "Carton"),
                                cartonQty = obj.optLong("cartonQty", 12L),
                                minOrderQty = obj.optLong("minOrderQty", 1L),
                                mrpPkr = obj.optDouble("mrpPkr", 0.0),
                                stockQty = obj.optLong("stockQty", 100L),
                                reservedQty = obj.optLong("reservedQty", 0L),
                                isFeatured = obj.optBoolean("isFeatured", false),
                                isActive = obj.optBoolean("isActive", true)
                            )
                        )
                    }
                    if (list.isNotEmpty()) return list
                }
            } catch (_: Exception) {
                // Fall through to bundled SNL catalog seed
            }
        }

        // Synthesize from the bundled 59-product SNL price list so the catalog is never empty offline
        return AttachedPriceListCatalog.attachedImageExtractedRows.map { row ->
            val distPrice = row.distributorPricePkr ?: 0.0
            val stdPrice = row.standardDealerPricePkr ?: if (distPrice > 0) (distPrice * 1.04).toLong().toDouble() else 0.0
            val premPrice = row.premiumDealerPricePkr ?: if (distPrice > 0) (distPrice * 1.02).toLong().toDouble() else 0.0
            val specPrice = row.specialDealerPricePkr ?: distPrice
            val tiers = buildMap {
                if (distPrice > 0) put("DISTRIBUTOR", distPrice)
                if (stdPrice > 0) put("STANDARD_DEALER", stdPrice)
                if (premPrice > 0) put("PREMIUM_DEALER", premPrice)
                if (specPrice > 0) put("SPECIAL_DEALER", specPrice)
            }
            Product(
                id = row.sku,
                sku = row.sku,
                serialNo = row.serialNo.toLong(),
                name = row.productName,
                productName = row.productName,
                category = row.categoryName,
                categoryId = row.categoryCode,
                categoryName = row.categoryName,
                priceTiers = tiers,
                stockAvailability = "IN_STOCK",
                inStock = true,
                grade = row.grade,
                packagingSize = row.packagingSize,
                packagingUnit = row.packagingUnit,
                cartonQty = row.cartonQty.toLong(),
                minOrderQty = 1L,
                mrpPkr = row.mrpPkr ?: 0.0,
                stockQty = (row.stockQty ?: 100).toLong(),
                reservedQty = 0L,
                isFeatured = row.serialNo <= 8,
                isActive = true
            )
        }
    }
}
