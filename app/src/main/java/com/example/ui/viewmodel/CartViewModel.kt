package com.example.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CartItem
import com.example.data.model.Product
import com.example.data.seed.AttachedPriceListCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dedicated `CartViewModel` managing a persistent local shopping cart with quantity selection
 * and dynamic subtotal calculation based on the customer's assigned `priceGroupId`
 * (`DISTRIBUTOR`, `STANDARD_DEALER`, `PREMIUM_DEALER`, `SPECIAL_DEALER`).
 */
class CartViewModel(
    private var prefs: SharedPreferences? = null,
    initialPriceGroup: String = "DISTRIBUTOR"
) : ViewModel() {

    private var activeUserId: String = "default_user"

    private val _assignedPriceGroup = MutableStateFlow(initialPriceGroup.ifBlank { "DISTRIBUTOR" })
    val assignedPriceGroup: StateFlow<String> = _assignedPriceGroup.asStateFlow()

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    val subtotalPkr: StateFlow<Double> = _cartItems
        .map { items -> items.sumOf { it.lineTotalPkr } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalCartons: StateFlow<Int> = _cartItems
        .map { items -> items.sumOf { it.quantity } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val totalLineItems: StateFlow<Int> = _cartItems
        .map { items -> items.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        loadPersistedCart()
    }

    /**
     * Binds Android SharedPreferences for local cart persistence scoped to `userId`.
     */
    fun bindLocalPersistence(context: Context, userId: String, priceGroupId: String) {
        val cleanUser = userId.ifBlank { "default_user" }
        val changedUser = activeUserId != cleanUser || prefs == null
        activeUserId = cleanUser
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (changedUser) {
            loadPersistedCart()
        }
        if (priceGroupId.isNotBlank() && priceGroupId != _assignedPriceGroup.value) {
            setAssignedPriceGroup(priceGroupId)
        }
    }

    /**
     * Updates the customer's assigned price group and recalculates all cart item unit prices and subtotal.
     */
    fun setAssignedPriceGroup(
        priceGroupCode: String,
        priceResolver: ((String, String) -> Double?)? = null
    ) {
        val normalizedGroup = priceGroupCode.trim().uppercase().ifBlank { "DISTRIBUTOR" }
        _assignedPriceGroup.value = normalizedGroup
        recalculatePricesForGroup(normalizedGroup, priceResolver)
    }

    /**
     * Recalculates unit prices and subtotal for all items in the cart based on `priceGroupCode`.
     */
    fun recalculatePricesForGroup(
        priceGroupCode: String = _assignedPriceGroup.value,
        priceResolver: ((String, String) -> Double?)? = null
    ) {
        val normalizedGroup = priceGroupCode.trim().uppercase().ifBlank { "DISTRIBUTOR" }
        _assignedPriceGroup.value = normalizedGroup
        val updated = _cartItems.value.map { item ->
            val resolvedPrice = resolveUnitPrice(item.product, normalizedGroup, priceResolver)
                ?: item.unitPricePkr
            item.copy(
                unitPricePkr = resolvedPrice,
                priceGroupCode = normalizedGroup
            )
        }
        _cartItems.value = updated
        persistCart(updated)
    }

    /**
     * Adds a product to the persistent local cart with quantity selection and price group calculation.
     */
    fun addToCart(
        product: Product,
        quantity: Int = 1,
        unitPriceOverridePkr: Double? = null,
        priceResolver: ((String, String) -> Double?)? = null
    ): Boolean {
        val groupCode = _assignedPriceGroup.value
        val unitPrice = unitPriceOverridePkr?.takeIf { it > 0.0 }
            ?: resolveUnitPrice(product, groupCode, priceResolver)
            ?: return false

        val minQty = product.minOrderQty.toInt().coerceAtLeast(1)
        val safeQty = quantity.coerceAtLeast(minQty)
        val current = _cartItems.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.product.sku.equals(product.sku, ignoreCase = true) }

        if (existingIdx >= 0) {
            val existing = current[existingIdx]
            current[existingIdx] = existing.copy(
                product = product,
                unitPricePkr = unitPrice,
                priceGroupCode = groupCode,
                quantity = existing.quantity + safeQty
            )
        } else {
            current.add(
                CartItem(
                    product = product,
                    unitPricePkr = unitPrice,
                    priceGroupCode = groupCode,
                    quantity = safeQty
                )
            )
        }

        _cartItems.value = current
        persistCart(current)
        return true
    }

    /**
     * Updates the quantity of an existing SKU in the cart (or removes it when `newQuantity <= 0`).
     */
    fun updateQuantity(sku: String, newQuantity: Int) {
        val current = _cartItems.value.toMutableList()
        val idx = current.indexOfFirst { it.product.sku.equals(sku, ignoreCase = true) }
        if (idx >= 0) {
            if (newQuantity <= 0) {
                current.removeAt(idx)
            } else {
                current[idx] = current[idx].copy(quantity = newQuantity)
            }
            _cartItems.value = current
            persistCart(current)
        }
    }

    fun incrementQuantity(sku: String) {
        val existing = _cartItems.value.firstOrNull { it.product.sku.equals(sku, ignoreCase = true) } ?: return
        updateQuantity(sku, existing.quantity + 1)
    }

    fun decrementQuantity(sku: String) {
        val existing = _cartItems.value.firstOrNull { it.product.sku.equals(sku, ignoreCase = true) } ?: return
        updateQuantity(sku, existing.quantity - 1)
    }

    fun removeItem(sku: String) {
        updateQuantity(sku, 0)
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        persistCart(emptyList())
    }

    /**
     * replaces the current cart items (e.g. during a 1-click reorder) and persists locally.
     */
    fun replaceCartItems(items: List<CartItem>) {
        _cartItems.value = items
        persistCart(items)
    }

    /**
     * Calculates the cart subtotal in PKR for the specified `priceGroupCode`.
     */
    fun calculateSubtotal(
        priceGroupCode: String = _assignedPriceGroup.value,
        priceResolver: ((String, String) -> Double?)? = null
    ): Double {
        return _cartItems.value.sumOf { item ->
            val unitPrice = if (item.priceGroupCode.equals(priceGroupCode, ignoreCase = true) && priceResolver == null) {
                item.unitPricePkr
            } else {
                resolveUnitPrice(item.product, priceGroupCode, priceResolver) ?: item.unitPricePkr
            }
            unitPrice * item.quantity
        }
    }

    private fun resolveUnitPrice(
        product: Product,
        priceGroupCode: String,
        priceResolver: ((String, String) -> Double?)?
    ): Double? {
        priceResolver?.invoke(product.sku, priceGroupCode)?.takeIf { it > 0.0 }?.let { return it }
        product.priceForGroup(priceGroupCode)?.takeIf { it > 0.0 }?.let { return it }
        product.priceForGroup("DISTRIBUTOR")?.takeIf { it > 0.0 }?.let { return it }
        return AttachedPriceListCatalog.attachedImageExtractedRows
            .firstOrNull { it.sku.equals(product.sku, ignoreCase = true) }
            ?.distributorPricePkr
    }

    private fun storageKey(): String = "${KEY_CART_ITEMS}_$activeUserId"

    private fun persistCart(items: List<CartItem>) {
        val sp = prefs ?: return
        val jsonArray = JSONArray()
        for (item in items) {
            val tiersJson = JSONObject()
            for ((k, v) in item.product.priceTiers) {
                tiersJson.put(k, v)
            }
            val obj = JSONObject().apply {
                put("sku", item.product.sku)
                put("serialNo", item.product.serialNo)
                put("name", item.product.displayName)
                put("productName", item.product.productName)
                put("category", item.product.displayCategory)
                put("categoryId", item.product.categoryId)
                put("categoryName", item.product.categoryName)
                put("grade", item.product.grade)
                put("packagingSize", item.product.packagingSize)
                put("packagingUnit", item.product.packagingUnit)
                put("cartonQty", item.product.cartonQty)
                put("minOrderQty", item.product.minOrderQty)
                put("stockQty", item.product.stockQty)
                put("reservedQty", item.product.reservedQty)
                put("stockAvailability", item.product.stockAvailability)
                put("inStock", item.product.inStock)
                put("priceTiers", tiersJson)
                put("unitPricePkr", item.unitPricePkr)
                put("priceGroupCode", item.priceGroupCode)
                put("quantity", item.quantity)
            }
            jsonArray.put(obj)
        }
        sp.edit().putString(storageKey(), jsonArray.toString()).apply()
    }

    private fun loadPersistedCart() {
        val sp = prefs ?: return
        val raw = sp.getString(storageKey(), null) ?: return
        if (raw.isBlank()) return
        try {
            val jsonArray = JSONArray(raw)
            val restored = mutableListOf<CartItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val tiersObj = obj.optJSONObject("priceTiers")
                val tiersMap = mutableMapOf<String, Double>()
                if (tiersObj != null) {
                    val keys = tiersObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        tiersMap[k] = tiersObj.optDouble(k, 0.0)
                    }
                }
                val productName = obj.optString("productName", obj.optString("name", ""))
                val categoryName = obj.optString("categoryName", obj.optString("category", ""))
                val prod = Product(
                    id = obj.optString("sku"),
                    sku = obj.optString("sku"),
                    serialNo = obj.optLong("serialNo", 0L),
                    name = obj.optString("name", productName),
                    productName = productName,
                    category = obj.optString("category", categoryName),
                    categoryId = obj.optString("categoryId", "MIX_BRANDS"),
                    categoryName = categoryName,
                    priceTiers = tiersMap,
                    stockAvailability = obj.optString("stockAvailability", "IN_STOCK"),
                    inStock = obj.optBoolean("inStock", true),
                    grade = obj.optString("grade", ""),
                    packagingSize = obj.optString("packagingSize", ""),
                    packagingUnit = obj.optString("packagingUnit", "Carton"),
                    cartonQty = obj.optLong("cartonQty", 12L),
                    minOrderQty = obj.optLong("minOrderQty", 1L),
                    stockQty = obj.optLong("stockQty", 100L),
                    reservedQty = obj.optLong("reservedQty", 0L)
                )
                restored.add(
                    CartItem(
                        product = prod,
                        unitPricePkr = obj.optDouble("unitPricePkr", 0.0),
                        priceGroupCode = obj.optString("priceGroupCode", _assignedPriceGroup.value),
                        quantity = obj.optInt("quantity", 1).coerceAtLeast(1)
                    )
                )
            }
            _cartItems.value = restored
        } catch (_: Exception) {
            // Ignore malformed persisted cart data
        }
    }

    companion object {
        private const val PREFS_NAME = "snl_persistent_local_cart"
        private const val KEY_CART_ITEMS = "cart_items_json"
    }
}
