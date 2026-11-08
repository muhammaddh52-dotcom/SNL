package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.ai.AiPriceListReader
import com.example.data.model.AppNotification
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.CompanySettings
import com.example.data.model.ExtractedPriceRow
import com.example.data.model.GroupPrice
import com.example.data.model.InventoryMovement
import com.example.data.model.Order
import com.example.data.model.PriceGroup
import com.example.data.model.PriceHistoryRecord
import com.example.data.model.PriceImportBatch
import com.example.data.model.Product
import com.example.data.model.UiLanguage
import com.example.data.model.UserProfile
import com.example.data.model.WhatsAppDeliveryLog
import com.example.data.repository.SnlRepository
import com.example.data.seed.AttachedPriceListCatalog
import com.example.ui.theme.AppThemeMode
import com.example.util.ExcelAndSheetsProcessor
import com.example.util.ExcelParseSummary
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "SnlViewModel"
private const val STOP_TIMEOUT_MS = 5000L

@OptIn(ExperimentalCoroutinesApi::class)
class SnlViewModel(
    private val repository: SnlRepository,
    private val currentUserId: String,
    private val currentUserEmail: String = ""
) : ViewModel() {

    private val aiReader = AiPriceListReader()

    // Default language is Urdu (RTL) per Section 21 of the Master Prompt
    private val _language = MutableStateFlow(UiLanguage.URDU)
    val language: StateFlow<UiLanguage> = _language.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Defaults to AppThemeMode.SYSTEM so the UI adapts to system-wide dark mode automatically
    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    // Material3 Dynamic Color enabled by default on Android 12+ with toggle support
    private val _useDynamicColor = MutableStateFlow(true)
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // Shopping cart state
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    // Favourite product SKUs
    private val _favouriteSkus = MutableStateFlow<Set<String>>(emptySet())
    val favouriteSkus: StateFlow<Set<String>> = _favouriteSkus.asStateFlow()

    // AI Price List Image Reader Draft Preview (initialized with the 59 rows from attached SNL image)
    private val _draftExtractedRows = MutableStateFlow(AttachedPriceListCatalog.attachedImageExtractedRows)
    val draftExtractedRows: StateFlow<List<ExtractedPriceRow>> = _draftExtractedRows.asStateFlow()

    private val _draftTargetPriceGroup = MutableStateFlow("DISTRIBUTOR")
    val draftTargetPriceGroup: StateFlow<String> = _draftTargetPriceGroup.asStateFlow()

    private val _aiExtractionStatus = MutableStateFlow(
        "Attached SNL Price List (03-11-26) loaded as initial draft: 59 items detected (56 priced, 3 flagged '-' rows at S.No 34-36)."
    )
    val aiExtractionStatus: StateFlow<String> = _aiExtractionStatus.asStateFlow()

    // Excel / CSV / Google Sheets Preview state
    private val _excelPreview = MutableStateFlow<ExcelParseSummary?>(null)
    val excelPreview: StateFlow<ExcelParseSummary?> = _excelPreview.asStateFlow()

    // Real-time Firestore flows with two-tier error unwrapping
    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { e ->
            unwrapAndLog("observeUserProfile", e)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val products: StateFlow<List<Product>> = repository.observeActiveProducts()
        .catch { e ->
            unwrapAndLog("observeActiveProducts", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val categories: StateFlow<List<Category>> = repository.observeActiveCategories()
        .catch { e ->
            unwrapAndLog("observeActiveCategories", e)
            emit(AttachedPriceListCatalog.initialCategories)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            AttachedPriceListCatalog.initialCategories
        )

    val priceGroups: StateFlow<List<PriceGroup>> = repository.observeActivePriceGroups()
        .catch { e ->
            unwrapAndLog("observeActivePriceGroups", e)
            emit(AttachedPriceListCatalog.initialPriceGroups)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            AttachedPriceListCatalog.initialPriceGroups
        )

    val groupPrices: StateFlow<List<GroupPrice>> = repository.observeActiveGroupPrices()
        .catch { e ->
            unwrapAndLog("observeActiveGroupPrices", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val customerOrders: StateFlow<List<Order>> = repository.observeCustomerOrders(currentUserId)
        .catch { e ->
            unwrapAndLog("observeCustomerOrders", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val isAdminFlow: Flow<Boolean> = userProfile.combine(flowOf(currentUserEmail)) { profile, email ->
        profile?.role in setOf("SUPER_ADMIN", "STAFF") || email.equals("muhammad.dh52@gmail.com", ignoreCase = true)
    }

    val allOrdersForAdmin: StateFlow<List<Order>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (isAdmin) repository.observeAllOrdersForAdmin() else flowOf(emptyList())
        }
        .catch { e ->
            unwrapAndLog("observeAllOrdersForAdmin", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val allUsersForAdmin: StateFlow<List<UserProfile>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (isAdmin) repository.observeAllUsersForAdmin() else flowOf(emptyList())
        }
        .catch { e ->
            unwrapAndLog("observeAllUsersForAdmin", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val inventoryMovements: StateFlow<List<InventoryMovement>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (isAdmin) repository.observeInventoryMovements() else flowOf(emptyList())
        }
        .catch { e ->
            unwrapAndLog("observeInventoryMovements", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val priceImports: StateFlow<List<PriceImportBatch>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (isAdmin) repository.observePriceImports() else flowOf(emptyList())
        }
        .catch { e ->
            unwrapAndLog("observePriceImports", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val priceHistory: StateFlow<List<PriceHistoryRecord>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (isAdmin) repository.observePriceHistory() else flowOf(emptyList())
        }
        .catch { e ->
            unwrapAndLog("observePriceHistory", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val notifications: StateFlow<List<AppNotification>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            repository.observeNotificationsForUser(currentUserId, isAdmin)
        }
        .catch { e ->
            unwrapAndLog("observeNotifications", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val whatsappLogs: StateFlow<List<WhatsAppDeliveryLog>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (isAdmin) repository.observeWhatsAppLogs() else flowOf(emptyList())
        }
        .catch { e ->
            unwrapAndLog("observeWhatsAppLogs", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val companySettings: StateFlow<CompanySettings> = repository.observeCompanySettings()
        .catch { e ->
            unwrapAndLog("observeCompanySettings", e)
            emit(CompanySettings())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CompanySettings())

    fun toggleLanguage() {
        _language.value = if (_language.value == UiLanguage.URDU) UiLanguage.ENGLISH else UiLanguage.URDU
    }

    fun setLanguage(lang: UiLanguage) {
        _language.value = lang
    }

    fun toggleDarkMode(systemIsDark: Boolean = false) {
        val nextDark = when (_themeMode.value) {
            AppThemeMode.SYSTEM -> !systemIsDark
            AppThemeMode.DARK -> false
            AppThemeMode.LIGHT -> true
        }
        _isDarkMode.value = nextDark
        _themeMode.value = if (nextDark) AppThemeMode.DARK else AppThemeMode.LIGHT
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        if (mode == AppThemeMode.DARK) _isDarkMode.value = true
        if (mode == AppThemeMode.LIGHT) _isDarkMode.value = false
    }

    fun toggleDynamicColor() {
        _useDynamicColor.value = !_useDynamicColor.value
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun postMessage(msg: String) {
        _statusBannerMessage.value = msg
    }

    fun toggleFavourite(sku: String) {
        val current = _favouriteSkus.value.toMutableSet()
        if (!current.add(sku)) current.remove(sku)
        _favouriteSkus.value = current
    }

    fun setDraftTargetPriceGroup(groupCode: String) {
        _draftTargetPriceGroup.value = groupCode
    }

    fun updateDraftRowPrice(sku: String, newPricePkr: Double?) {
        _draftExtractedRows.value = _draftExtractedRows.value.map { row ->
            if (row.sku == sku) {
                row.copy(
                    distributorPricePkr = newPricePkr,
                    rawPriceText = newPricePkr?.toLong()?.toString() ?: "-",
                    isUncertainOrMissingPrice = newPricePkr == null || newPricePkr <= 0.0,
                    uncertaintyNote = if (newPricePkr != null && newPricePkr > 0.0) {
                        "Manually verified by Admin"
                    } else {
                        row.uncertaintyNote
                    }
                )
            } else row
        }
    }

    /**
     * Resolves the authorized price in PKR for a given SKU and customer price group.
     * Customers ONLY ever see the price for their assigned priceGroupId.
     */
    fun getAuthorizedPriceForCustomer(sku: String, priceGroupId: String): Double? {
        val match = groupPrices.value.firstOrNull {
            it.sku.equals(sku, ignoreCase = true) &&
                it.priceGroupCode.equals(priceGroupId, ignoreCase = true) &&
                it.isActive &&
                it.sellingPricePkr > 0.0
        }
        if (match != null) return match.sellingPricePkr

        // Also check the embedded priceTiers map on the Product document (/products/{productId})
        val productTierPrice = products.value
            .firstOrNull { it.sku.equals(sku, ignoreCase = true) }
            ?.priceForGroup(priceGroupId)
        if (productTierPrice != null) return productTierPrice

        // Fallback to initial attached price list if group is DISTRIBUTOR and Firestore is still syncing
        val fallbackRow = AttachedPriceListCatalog.attachedImageExtractedRows.firstOrNull {
            it.sku.equals(sku, ignoreCase = true)
        }
        return fallbackRow?.distributorPricePkr
    }

    // --- ACCOUNT ACTIONS ---

    fun initializeAsSuperAdmin(fullName: String, email: String, autoImportAttachedList: Boolean = true) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.bootstrapSuperAdminAndSettings(fullName, email)
            if (res.isSuccess) {
                if (autoImportAttachedList && products.value.isEmpty()) {
                    repository.commitPriceListImport(
                        fileName = "Attached_SNL_Sole_Distributor_Price_List_03-11-26.jpg",
                        sourceType = "AI_IMAGE_OCR",
                        rows = _draftExtractedRows.value,
                        targetSingleColumnGroup = "DISTRIBUTOR",
                        alsoCopyToStandardDealerIfBlank = true,
                        allowStockUpdate = false
                    )
                }
                _statusBannerMessage.value = "Super Admin account & SNL 59-product catalogue initialized!"
            } else {
                _statusBannerMessage.value = "Failed to initialize Super Admin: ${res.exceptionOrNull()?.localizedMessage}"
            }
            _isBusy.value = false
        }
    }

    fun submitSelfRegistration(
        fullName: String,
        businessName: String,
        phone: String,
        email: String,
        city: String,
        address: String,
        role: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.submitSelfRegistration(fullName, businessName, phone, email, city, address, role)
            _isBusy.value = false
            _statusBannerMessage.value = if (res.isSuccess) {
                "Registration submitted! Status: PENDING_APPROVAL. Administrator will review your account."
            } else {
                "Registration failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun adminSaveAccount(
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
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminCreateOrUpdateAccount(
                targetUid = targetUid,
                fullName = fullName,
                businessName = businessName,
                phone = phone,
                email = email,
                role = role,
                customerCode = customerCode,
                city = city,
                address = address,
                priceGroupId = priceGroupId,
                accountStatus = accountStatus,
                staffPermissions = staffPermissions,
                creditAllowed = creditAllowed
            )
            _isBusy.value = false
            _statusBannerMessage.value = if (res.isSuccess) {
                "Account '$businessName' ($role - $accountStatus) saved."
            } else {
                "Account save failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    /**
     * Allows the signed-in admin/tester to switch their active profile role & price group
     * to test Super Admin, Staff, Distributor, and Dealer experiences on one device.
     */
    fun switchCurrentUserRoleForTesting(newRole: String, newPriceGroup: String) {
        val current = userProfile.value ?: return
        adminSaveAccount(
            targetUid = currentUserId,
            fullName = current.fullName,
            businessName = current.businessName,
            phone = current.phone,
            email = current.email,
            role = newRole,
            customerCode = current.customerCode,
            city = current.city,
            address = current.address,
            priceGroupId = newPriceGroup,
            accountStatus = "ACTIVE",
            staffPermissions = if (newRole == "STAFF") "VIEW_ORDERS,PROCESS_ORDERS,VIEW_INVENTORY,VIEW_REPORTS" else "ALL",
            creditAllowed = true
        )
    }

    // --- PRODUCT, CATEGORY & PRICE GROUP ACTIONS ---

    fun saveProduct(
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
        distributorPrice: Double,
        standardDealerPrice: Double,
        premiumDealerPrice: Double,
        specialDealerPrice: Double
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val pricesMap = mutableMapOf<String, Double>()
            if (distributorPrice > 0) pricesMap["DISTRIBUTOR"] = distributorPrice
            if (standardDealerPrice > 0) pricesMap["STANDARD_DEALER"] = standardDealerPrice
            if (premiumDealerPrice > 0) pricesMap["PREMIUM_DEALER"] = premiumDealerPrice
            if (specialDealerPrice > 0) pricesMap["SPECIAL_DEALER"] = specialDealerPrice

            val res = repository.saveProductAndPrices(
                sku = sku,
                serialNo = serialNo,
                productName = productName,
                categoryId = categoryId,
                categoryName = categoryName,
                grade = grade,
                packagingSize = packagingSize,
                packagingUnit = packagingUnit,
                cartonQty = cartonQty,
                minOrderQty = minOrderQty,
                mrpPkr = mrpPkr,
                stockQty = stockQty,
                isFeatured = isFeatured,
                isActive = isActive,
                groupPricesMap = pricesMap
            )
            _isBusy.value = false
            _statusBannerMessage.value = if (res.isSuccess) {
                "Product ${res.getOrNull()} saved and synchronized across catalogues."
            } else {
                "Error saving product: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun deleteProduct(sku: String) {
        viewModelScope.launch {
            val res = repository.deleteProduct(sku)
            _statusBannerMessage.value = if (res.isSuccess) {
                "Product $sku deleted."
            } else {
                "Failed to delete $sku: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun saveCategory(code: String, nameEn: String, nameUr: String, sortOrder: Long) {
        viewModelScope.launch {
            val res = repository.saveCategory(code, nameEn, nameUr, sortOrder)
            _statusBannerMessage.value = if (res.isSuccess) {
                "Category '$nameEn' saved."
            } else {
                "Error saving category: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun savePriceGroup(code: String, name: String, description: String, discount: Double, minQty: Long) {
        viewModelScope.launch {
            val res = repository.savePriceGroup(code, name, description, discount, minQty)
            _statusBannerMessage.value = if (res.isSuccess) {
                "Price Group '$name' ($code) saved."
            } else {
                "Error saving price group: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    // --- AI PRICE LIST IMAGE EXTRACTION & IMPORT ---

    fun runGeminiExtractionOnBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _isBusy.value = true
            _aiExtractionStatus.value = "Analyzing price list image with Gemini 3.8 Flash multimodal OCR..."
            val result = aiReader.extractPriceListFromBitmap(bitmap)
            _isBusy.value = false
            result.onSuccess { extracted ->
                _draftExtractedRows.value = extracted.rows
                val uncertainCount = extracted.rows.count { it.isUncertainOrMissingPrice }
                _aiExtractionStatus.value =
                    "Gemini extracted ${extracted.rows.size} rows from '${extracted.documentTitle}' (Column: ${extracted.detectedPriceGroupLabel}). $uncertainCount uncertain/missing price rows flagged for manual review."
            }.onFailure { err ->
                _aiExtractionStatus.value = "Gemini OCR error: ${err.localizedMessage ?: "Extraction failed"}"
                _statusBannerMessage.value = "AI Extraction Error: ${err.localizedMessage}"
            }
        }
    }

    fun runGeminiOnAttachedPriceListAsset(context: Context) {
        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.img_snl_pricelist_source)
        if (bmp != null) {
            runGeminiExtractionOnBitmap(bmp)
        } else {
            _draftExtractedRows.value = AttachedPriceListCatalog.attachedImageExtractedRows
        }
    }

    fun resetDraftToAttachedPriceList() {
        _draftExtractedRows.value = AttachedPriceListCatalog.attachedImageExtractedRows
        _aiExtractionStatus.value =
            "Reset to attached SNL Price List (03-11-26): 59 items (56 verified prices, 3 '-' rows at S.No 34-36)."
    }

    fun confirmAndCommitDraftImport(
        fileName: String,
        sourceType: String,
        copyToStandardDealerIfBlank: Boolean = true,
        allowStockUpdate: Boolean = false
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.commitPriceListImport(
                fileName = fileName,
                sourceType = sourceType,
                rows = _draftExtractedRows.value,
                targetSingleColumnGroup = _draftTargetPriceGroup.value,
                alsoCopyToStandardDealerIfBlank = copyToStandardDealerIfBlank,
                allowStockUpdate = allowStockUpdate
            )
            _isBusy.value = false
            _statusBannerMessage.value = if (res.isSuccess) {
                "Price List Import (${res.getOrNull()}) committed to Firestore!"
            } else {
                "Import failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    // --- EXCEL, CSV & GOOGLE SHEETS IMPORT & ROLLBACK ---

    fun parseExcelOrCsvPreview(rawContent: String, formatMode: String) {
        val existingPriceMap = groupPrices.value.associate { (it.sku.uppercase() to it.priceGroupCode.uppercase()) to it.sellingPricePkr }
        val knownGroups = priceGroups.value.map { it.groupCode.uppercase() }.toSet()
        val summary = ExcelAndSheetsProcessor.parseDelimitedPriceList(
            rawContent = rawContent,
            formatMode = formatMode,
            existingProducts = products.value,
            existingPricesBySkuAndGroup = existingPriceMap,
            knownPriceGroups = knownGroups
        )
        _excelPreview.value = summary
    }

    fun confirmExcelPreviewImport(fileName: String, sourceType: String, allowStockUpdate: Boolean) {
        val preview = _excelPreview.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.commitPriceListImport(
                fileName = fileName,
                sourceType = sourceType,
                rows = preview.validRows,
                targetSingleColumnGroup = "DISTRIBUTOR",
                alsoCopyToStandardDealerIfBlank = false,
                allowStockUpdate = allowStockUpdate
            )
            _isBusy.value = false
            if (res.isSuccess) {
                _excelPreview.value = null
                _statusBannerMessage.value = "Excel/CSV Import (${res.getOrNull()}) synchronized!"
            } else {
                _statusBannerMessage.value = "Import error: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun fetchAndPreviewGoogleSheet(sheetUrl: String, formatMode: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = ExcelAndSheetsProcessor.fetchGoogleSheetCsv(sheetUrl)
            _isBusy.value = false
            res.onSuccess { csvContent ->
                parseExcelOrCsvPreview(csvContent, formatMode)
                _statusBannerMessage.value = "Google Sheet fetched! Review the Import Preview before confirming."
            }.onFailure { err ->
                _statusBannerMessage.value = "Google Sheets error: ${err.localizedMessage}"
            }
        }
    }

    fun rollbackImport(batch: PriceImportBatch) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.rollbackPriceImport(batch)
            _isBusy.value = false
            _statusBannerMessage.value = if (res.isSuccess) {
                "Import ${batch.id} rolled back to previous price snapshot!"
            } else {
                "Rollback failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    // --- SHOPPING CART & ORDER PLACEMENT ---

    fun addToCart(product: Product, quantity: Int = 1) {
        val profile = userProfile.value
        val groupCode = profile?.priceGroupId ?: "STANDARD_DEALER"
        val unitPrice = getAuthorizedPriceForCustomer(product.sku, groupCode)
        if (unitPrice == null || unitPrice <= 0.0) {
            _statusBannerMessage.value =
                "Price for ${product.productName} is pending Admin confirmation ('-' on price list)."
            return
        }
        val current = _cartItems.value.toMutableList()
        val idx = current.indexOfFirst { it.product.sku == product.sku }
        if (idx >= 0) {
            val existing = current[idx]
            current[idx] = existing.copy(
                quantity = existing.quantity + quantity,
                unitPricePkr = unitPrice
            )
        } else {
            current.add(
                CartItem(
                    product = product,
                    unitPricePkr = unitPrice,
                    priceGroupCode = groupCode,
                    quantity = quantity.coerceAtLeast(product.minOrderQty.toInt())
                )
            )
        }
        _cartItems.value = current
        _statusBannerMessage.value = "${product.productName} added to cart (PKR ${unitPrice.toLong()})."
    }

    fun updateCartQuantity(sku: String, newQty: Int) {
        val current = _cartItems.value.toMutableList()
        if (newQty <= 0) {
            current.removeAll { it.product.sku == sku }
        } else {
            val idx = current.indexOfFirst { it.product.sku == sku }
            if (idx >= 0) {
                current[idx] = current[idx].copy(quantity = newQty)
            }
        }
        _cartItems.value = current
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }

    fun reorderFromPastOrder(order: Order) {
        val profile = userProfile.value ?: return
        val prodBySku = products.value.associateBy { it.sku }
        val newCart = mutableListOf<CartItem>()
        for (line in order.parsedItems()) {
            val prod = prodBySku[line.sku] ?: continue
            val currentPrice = getAuthorizedPriceForCustomer(prod.sku, profile.priceGroupId) ?: line.unitPricePkr
            newCart.add(
                CartItem(
                    product = prod,
                    unitPricePkr = currentPrice,
                    priceGroupCode = profile.priceGroupId,
                    quantity = line.quantity
                )
            )
        }
        if (newCart.isNotEmpty()) {
            _cartItems.value = newCart
            _statusBannerMessage.value = "Loaded ${newCart.size} items from ${order.orderNumber} into Cart with current authorized prices."
        }
    }

    fun submitOrder(
        deliveryAddress: String,
        city: String,
        phone: String,
        orderInstructions: String,
        paymentMethod: String,
        onOrderCreated: (Order) -> Unit = {}
    ) {
        val profile = userProfile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val settings = companySettings.value
            val res = repository.placeCustomerOrder(
                userProfile = profile,
                cartItems = _cartItems.value,
                deliveryAddress = deliveryAddress,
                city = city,
                phone = phone,
                orderInstructions = orderInstructions,
                paymentMethod = paymentMethod,
                deliveryChargesPkr = settings.defaultDeliveryChargesPkr,
                discountPkr = 0.0
            )
            _isBusy.value = false
            res.onSuccess { createdOrder ->
                _cartItems.value = emptyList()
                _statusBannerMessage.value =
                    "Order ${createdOrder.orderNumber} submitted! Status: PENDING ADMIN APPROVAL."
                onOrderCreated(createdOrder)
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Order submission failed."
                if (msg.startsWith("PRICE_UPDATED:")) {
                    // Refresh cart prices automatically so customer can review and confirm
                    _cartItems.value = _cartItems.value.map { item ->
                        val updatedPrice = getAuthorizedPriceForCustomer(item.product.sku, profile.priceGroupId)
                            ?: item.unitPricePkr
                        item.copy(unitPricePkr = updatedPrice)
                    }
                }
                _statusBannerMessage.value = msg
            }
        }
    }

    // --- ADMIN ORDER APPROVAL & REJECTION ---

    fun acceptOrder(order: Order) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminAcceptOrder(order)
            _isBusy.value = false
            _statusBannerMessage.value = if (res.isSuccess) {
                "Order ${order.orderNumber} ACCEPTED & CONFIRMED! Stock reserved & customer notified."
            } else {
                "Cannot accept order: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun rejectOrder(order: Order, reason: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminRejectOrder(order, reason)
            _isBusy.value = false
            _statusBannerMessage.value = if (res.isSuccess) {
                "Order ${order.orderNumber} REJECTED, hidden from customer screens, and moved to Admin Rejected Archive."
            } else {
                "Reject failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun updateOrderStatus(order: Order, newStatus: String, newPaymentStatus: String = order.paymentStatus) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminUpdateOrderStatus(order, newStatus, newPaymentStatus)
            _isBusy.value = false
            _statusBannerMessage.value = if (res.isSuccess) {
                val isFcmStatus = com.example.notifications.SnlFcmNotificationManager.isTargetPushStatus(newStatus)
                if (isFcmStatus) {
                    val label = com.example.notifications.SnlFcmNotificationManager.formatCanonicalPushStatus(newStatus)
                    "Order ${order.orderNumber} updated to '$label' ($newPaymentStatus) — FCM Push Notification sent to distributor!"
                } else {
                    "Order ${order.orderNumber} updated to $newStatus ($newPaymentStatus)."
                }
            } else {
                "Status update failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun adjustInventory(product: Product, newStock: Long, note: String) {
        viewModelScope.launch {
            val res = repository.adjustProductStock(product, newStock, note)
            _statusBannerMessage.value = if (res.isSuccess) {
                "Stock for ${product.productName} updated to $newStock."
            } else {
                "Stock update failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun saveSettings(settings: CompanySettings) {
        viewModelScope.launch {
            val res = repository.saveCompanySettings(settings)
            _statusBannerMessage.value = if (res.isSuccess) {
                "Company, WhatsApp & Google Sheets settings saved."
            } else {
                "Failed to save settings: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    private fun unwrapAndLog(op: String, error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Stream warning in $op: ${(firestoreError ?: error).message}")
    }
}
