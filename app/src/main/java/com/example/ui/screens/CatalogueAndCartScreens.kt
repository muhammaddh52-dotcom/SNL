package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.model.SupportChatMessage
import com.example.data.model.UiLanguage
import com.example.data.model.UserProfile
import com.example.ui.theme.SnlAmberWarn
import com.example.ui.theme.SnlGreenAccept
import com.example.ui.theme.SnlNavy
import com.example.ui.theme.SnlRed
import com.example.ui.theme.SnlYellow
import com.example.util.ExcelAndSheetsProcessor
import com.example.util.InvoiceAndReportPdfGenerator
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.FirestorePersistenceConfig
import com.example.data.repository.SnlRepository
import com.example.data.seed.AttachedPriceListCatalog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val numberFormatter = NumberFormat.getNumberInstance(Locale.US)
fun formatPkrPrice(value: Double): String = "PKR ${numberFormatter.format(value.toLong())}"

/**
 * Self-contained `CatalogScreen` composable that fetches the `products` collection from Firestore
 * (using the named Firestore database via `SnlRepository`) when `products` is not externally supplied,
 * and displays them in a `LazyColumn` with category filtering (`FilterChip`s) and search.
 */
@Composable
fun CatalogScreen(
    userProfile: UserProfile = UserProfile(
        businessName = "Authorized SNL Partner",
        role = "DISTRIBUTOR",
        priceGroupId = "DISTRIBUTOR",
        accountStatus = "ACTIVE"
    ),
    products: List<Product>? = null,
    categories: List<Category> = AttachedPriceListCatalog.initialCategories,
    favouriteSkus: Set<String> = emptySet(),
    language: UiLanguage = UiLanguage.URDU,
    getPriceForSku: ((String) -> Double?)? = null,
    onToggleFavourite: (String) -> Unit = {},
    onAddToCart: (Product, Int) -> Unit = { _, _ -> },
    onQuickSeedCatalogue: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember(context) { SnlRepository(context) }
    val localCartViewModel: com.example.ui.viewmodel.CartViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    androidx.compose.runtime.LaunchedEffect(userProfile.uid, userProfile.priceGroupId) {
        localCartViewModel.bindLocalPersistence(
            context = context,
            userId = userProfile.uid.ifBlank { userProfile.id.ifBlank { "default_user" } },
            priceGroupId = userProfile.priceGroupId.ifBlank { "DISTRIBUTOR" }
        )
    }

    var globalSearchQuery by remember { mutableStateOf("") }
    var globalCategoryFilter by remember { mutableStateOf("ALL") }
    var globalSearchScope by remember { mutableStateOf("ALL") }

    val firestoreProductsFlow = remember(repository, globalSearchQuery, globalCategoryFilter, globalSearchScope) {
        repository.observeProductsWithGlobalSearch(
            searchQuery = globalSearchQuery,
            categoryFilter = globalCategoryFilter,
            searchFieldScope = globalSearchScope
        ).catch { emit(FirestorePersistenceConfig.getOfflineFallbackProducts(context)) }
    }
    val observedFirestoreProducts by firestoreProductsFlow.collectAsStateWithLifecycle(
        initialValue = FirestorePersistenceConfig.getOfflineFallbackProducts(context)
    )
    val activeProducts = if (!products.isNullOrEmpty() && observedFirestoreProducts.isEmpty()) {
        products
    } else {
        observedFirestoreProducts.ifEmpty {
            products ?: FirestorePersistenceConfig.getOfflineFallbackProducts(context)
        }
    }

    CatalogueScreen(
        userProfile = userProfile,
        products = activeProducts,
        categories = categories,
        favouriteSkus = favouriteSkus,
        language = language,
        externalSearchQuery = globalSearchQuery,
        onExternalSearchQueryChange = { globalSearchQuery = it },
        externalSelectedCategory = globalCategoryFilter,
        onExternalSelectedCategoryChange = { globalCategoryFilter = it },
        externalSearchScope = globalSearchScope,
        onExternalSearchScopeChange = { globalSearchScope = it },
        getPriceForSku = getPriceForSku ?: { sku ->
            val matchedProduct = activeProducts.firstOrNull { it.sku.equals(sku, ignoreCase = true) }
            matchedProduct?.priceForGroup(userProfile.priceGroupId)
                ?: AttachedPriceListCatalog.attachedImageExtractedRows
                    .firstOrNull { it.sku.equals(sku, ignoreCase = true) }
                    ?.distributorPricePkr
        },
        onToggleFavourite = onToggleFavourite,
        onAddToCart = { prod, qty ->
            val resolvedPrice = (getPriceForSku?.invoke(prod.sku))
                ?: prod.priceForGroup(userProfile.priceGroupId)
                ?: AttachedPriceListCatalog.attachedImageExtractedRows
                    .firstOrNull { it.sku.equals(prod.sku, ignoreCase = true) }
                    ?.distributorPricePkr
            localCartViewModel.addToCart(prod, qty, resolvedPrice)
            onAddToCart(prod, qty)
        },
        onQuickSeedCatalogue = onQuickSeedCatalogue
    )
}

@Composable
fun CatalogueScreen(
    userProfile: UserProfile,
    products: List<Product>,
    categories: List<Category>,
    favouriteSkus: Set<String>,
    language: UiLanguage,
    externalSearchQuery: String? = null,
    onExternalSearchQueryChange: ((String) -> Unit)? = null,
    externalSelectedCategory: String? = null,
    onExternalSelectedCategoryChange: ((String) -> Unit)? = null,
    externalSearchScope: String? = null,
    onExternalSearchScopeChange: ((String) -> Unit)? = null,
    getPriceForSku: (String) -> Double?,
    onToggleFavourite: (String) -> Unit,
    onAddToCart: (Product, Int) -> Unit,
    onQuickSeedCatalogue: () -> Unit
) {
    val isUr = language == UiLanguage.URDU
    var internalSearchQuery by remember { mutableStateOf("") }
    var internalSelectedCategory by remember { mutableStateOf("ALL") }
    var internalSearchScope by remember { mutableStateOf("ALL") } // ALL, NAME, SKU, CATEGORY

    val searchQuery = externalSearchQuery ?: internalSearchQuery
    val updateSearchQuery: (String) -> Unit = { next ->
        internalSearchQuery = next
        onExternalSearchQueryChange?.invoke(next)
    }

    val selectedCategory = externalSelectedCategory ?: internalSelectedCategory
    val updateSelectedCategory: (String) -> Unit = { next ->
        internalSelectedCategory = next
        onExternalSelectedCategoryChange?.invoke(next)
    }

    val searchScope = externalSearchScope ?: internalSearchScope
    val updateSearchScope: (String) -> Unit = { next ->
        internalSearchScope = next
        onExternalSearchScopeChange?.invoke(next)
    }

    var sortBy by remember { mutableStateOf("SERIAL") } // SERIAL, PRICE_ASC, PRICE_DESC
    var selectedDetailProduct by remember { mutableStateOf<Product?>(null) }
    var showSupportChatDialog by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val supportRepository = remember(context) { SnlRepository(context) }
    val resolvedSupportUid = remember(userProfile.id, userProfile.uid) {
        userProfile.uid.ifBlank { userProfile.id.ifBlank { Firebase.auth.currentUser?.uid.orEmpty() } }
    }
    val supportMessagesFlow = remember(supportRepository, resolvedSupportUid) {
        supportRepository.observeSupportChatMessages(resolvedSupportUid)
            .catch { emit(emptyList()) }
    }
    val supportMessages by supportMessagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val filteredProducts = remember(products, searchQuery, selectedCategory, searchScope, sortBy, favouriteSkus) {
        val trimmedQuery = searchQuery.trim()
        products.filter { prod ->
            val matchesSearch = if (trimmedQuery.isBlank()) {
                true
            } else {
                when (searchScope.uppercase(Locale.US)) {
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

            val matchesCat = when (selectedCategory) {
                "ALL" -> true
                "FEATURED" -> prod.isFeatured
                "FAVOURITES" -> favouriteSkus.contains(prod.sku)
                else -> prod.categoryId.equals(selectedCategory, ignoreCase = true) ||
                    prod.categoryName.equals(selectedCategory, ignoreCase = true) ||
                    prod.category.equals(selectedCategory, ignoreCase = true)
            }
            matchesSearch && matchesCat
        }.let { list ->
            when (sortBy) {
                "PRICE_ASC" -> list.sortedBy { getPriceForSku(it.sku) ?: Double.MAX_VALUE }
                "PRICE_DESC" -> list.sortedByDescending { getPriceForSku(it.sku) ?: 0.0 }
                else -> list.sortedBy { it.serialNo }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("catalog_lazy_column"),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Brand & Authorized Price Group Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SnlNavy),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_snl_logo),
                        contentDescription = "SNL Logo",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(6.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SAIFI NATIONAL LUBRICANTS",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isUr) {
                                "${userProfile.businessName} | پرائس گروپ: ${userProfile.priceGroupId}"
                            } else {
                                "${userProfile.businessName} | Authorized Tier: ${userProfile.priceGroupId}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SnlYellow
                        )
                        Text(
                            text = if (isUr) {
                                "آفیشل کیٹلاگ — تمام قیمتیں پاکستانی روپے (PKR) میں درج ہیں"
                            } else {
                                "Official B2B Catalogue — Only your authorized ${userProfile.priceGroupId} prices are shown"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Global Real-Time Firestore Search Bar (Name, SKU, Category) & Scope Chips
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("global_catalog_search_card"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { updateSearchQuery(it) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Global Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { updateSearchQuery("") },
                                    modifier = Modifier.testTag("clear_global_search_button")
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                if (isUr) "پروڈکٹ کا نام، کوڈ (SNL-001) یا کیٹیگری تلاش کریں..."
                                else "Global Search by Product Name, SKU (SNL-001), or Category..."
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("catalogue_search_input")
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val scopes = listOf(
                            "ALL" to if (isUr) "تمام فیلڈز" else "All (Name, SKU, Category)",
                            "NAME" to if (isUr) "پروڈکٹ کا نام" else "Product Name",
                            "SKU" to if (isUr) "پروڈکٹ کوڈ (SKU)" else "SKU Code",
                            "CATEGORY" to if (isUr) "کیٹیگری" else "Category"
                        )
                        items(scopes, key = { it.first }) { (scopeCode, scopeLabel) ->
                            FilterChip(
                                selected = searchScope == scopeCode,
                                onClick = { updateSearchScope(scopeCode) },
                                label = { Text(scopeLabel) },
                                modifier = Modifier.testTag("search_scope_$scopeCode")
                            )
                        }
                    }

                    // Offline Persistence & Cache Readiness Row
                    val isSimulatedOffline by FirestorePersistenceConfig.isSimulatedOffline.collectAsStateWithLifecycle()
                    val isServingFromCache by FirestorePersistenceConfig.isServingFromOfflineCache.collectAsStateWithLifecycle()
                    val isOfflineActive = isSimulatedOffline || isServingFromCache
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isOfflineActive) {
                            SnlAmberWarn.copy(alpha = 0.14f)
                        } else {
                            SnlGreenAccept.copy(alpha = 0.12f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("catalog_offline_persistence_badge")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isOfflineActive) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = if (isOfflineActive) SnlAmberWarn else SnlGreenAccept,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isOfflineActive) {
                                        if (isUr) {
                                            "آف لائن کیش فعال (${filteredProducts.size} مصنوعات دستیاب)"
                                        } else {
                                            "Offline Cache Active (${filteredProducts.size} products accessible)"
                                        }
                                    } else {
                                        if (isUr) {
                                            "فائر اسٹور آف لائن کیشنگ فعال (کیٹلاگ اور کارٹ محفوظ)"
                                        } else {
                                            "Firestore Offline Persistence Enabled (Catalog & Cart Ready)"
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOfflineActive) SnlAmberWarn else SnlGreenAccept
                                )
                            }

                            Text(
                                text = if (isSimulatedOffline) {
                                    if (isUr) "آن لائن کریں" else "Go Online"
                                } else {
                                    if (isUr) "آف لائن ٹیسٹ" else "Test Offline"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SnlNavy,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        supportRepository.setOfflineMode(!isSimulatedOffline)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("catalog_toggle_offline_mode_btn")
                            )
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("catalog_category_filter_row")
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == "ALL",
                        onClick = { updateSelectedCategory("ALL") },
                        label = { Text(if (isUr) "تمام (${products.size})" else "All (${products.size})") },
                        modifier = Modifier.testTag("category_chip_ALL")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == "FEATURED",
                        onClick = { updateSelectedCategory("FEATURED") },
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(if (isUr) "نمایاں" else "Featured") },
                        modifier = Modifier.testTag("category_chip_FEATURED")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == "FAVOURITES",
                        onClick = { updateSelectedCategory("FAVOURITES") },
                        leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(if (isUr) "پسندیدہ (${favouriteSkus.size})" else "Saved (${favouriteSkus.size})") },
                        modifier = Modifier.testTag("category_chip_FAVOURITES")
                    )
                }
                items(categories, key = { it.categoryCode }) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.categoryCode,
                        onClick = { updateSelectedCategory(cat.categoryCode) },
                        label = { Text(if (isUr) "${cat.nameUrdu} (${cat.categoryName})" else cat.categoryName) },
                        modifier = Modifier.testTag("category_chip_${cat.categoryCode}")
                    )
                }
            }
        }

        // Sort Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUr) "دکھائی جانے والی مصنوعات: ${filteredProducts.size}" else "Showing ${filteredProducts.size} products",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("global_search_result_count")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = { sortBy = "SERIAL" },
                        label = { Text(if (isUr) "فہرست نمبر" else "S.No") }
                    )
                    AssistChip(
                        onClick = { sortBy = "PRICE_ASC" },
                        label = { Text(if (isUr) "کم قیمت" else "Price ↑") }
                    )
                    AssistChip(
                        onClick = { sortBy = "PRICE_DESC" },
                        label = { Text(if (isUr) "زیادہ قیمت" else "Price ↓") }
                    )
                }
            }
        }

        if (products.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isUr) "کیٹلاگ میں ابھی مصنوعات لوڈ نہیں ہوئیں" else "No Products Published in Live Catalogue Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onQuickSeedCatalogue,
                            colors = ButtonDefaults.buttonColors(containerColor = SnlRed),
                            modifier = Modifier.testTag("seed_attached_pricelist_btn")
                        ) {
                            Text(if (isUr) "منسلک پرائس لسٹ (59 مصنوعات) لوڈ کریں" else "Load 59 Products from Attached SNL Price List")
                        }
                    }
                }
            }
        } else {
            items(filteredProducts, key = { it.sku }) { product ->
                val pricePkr = getPriceForSku(product.sku)
                val isFav = favouriteSkus.contains(product.sku)
                ProductCatalogueCard(
                    product = product,
                    authorizedPricePkr = pricePkr,
                    priceGroupCode = userProfile.priceGroupId,
                    isFavourite = isFav,
                    isUr = isUr,
                    onToggleFavourite = { onToggleFavourite(product.sku) },
                    onAddToCart = { qty -> onAddToCart(product, qty) },
                    onOpenDetails = { selectedDetailProduct = product }
                )
            }
        }
    }

    // Floating Action Button in CatalogScreen / CatalogueScreen for Real-Time Customer Support Chat
    ExtendedFloatingActionButton(
        onClick = { showSupportChatDialog = true },
        containerColor = SnlRed,
        contentColor = Color.White,
        icon = {
            Icon(
                imageVector = Icons.Default.SupportAgent,
                contentDescription = if (isUr) "کسٹمر سپورٹ چیٹ" else "Customer Support Chat"
            )
        },
        text = {
            Text(
                text = if (supportMessages.isNotEmpty()) {
                    if (isUr) "لائیو سپورٹ (${supportMessages.size})" else "Live Support (${supportMessages.size})"
                } else {
                    if (isUr) "لائیو سپورٹ چیٹ" else "Live Support"
                },
                fontWeight = FontWeight.Bold
            )
        },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp)
            .testTag("customer_support_chat_fab")
    )
    }

    if (showSupportChatDialog) {
        CustomerSupportChatDialog(
            userProfile = userProfile,
            messages = supportMessages,
            repository = supportRepository,
            isUr = isUr,
            onDismiss = { showSupportChatDialog = false }
        )
    }

    selectedDetailProduct?.let { prod ->
        val pricePkr = getPriceForSku(prod.sku)
        AlertDialog(
            onDismissRequest = { selectedDetailProduct = null },
            title = { Text("${prod.productName} (${prod.sku})", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Category: ${prod.categoryName}")
                    Text("SAE Grade / Viscosity: ${prod.grade}")
                    Text("Packing Size: ${prod.packagingSize} (${prod.packagingUnit})")
                    Text("Carton Quantity: ${prod.cartonQty} units | Min Order Qty: ${prod.minOrderQty}")
                    Text("Available Warehouse Stock: ${prod.availableStock} ${prod.packagingUnit}s")
                    Text(
                        text = if (pricePkr != null && pricePkr > 0) {
                            "Your Authorized (${userProfile.priceGroupId}) Price: ${formatPkrPrice(pricePkr)}"
                        } else {
                            "Price: Marked '-' on price list (Contact SNL Admin for quote)"
                        },
                        fontWeight = FontWeight.Bold,
                        color = SnlRed
                    )
                    if (prod.description.isNotBlank()) {
                        Text(prod.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (pricePkr != null && pricePkr > 0) {
                    Button(
                        onClick = {
                            onAddToCart(prod, 1)
                            selectedDetailProduct = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SnlRed)
                    ) {
                        Text(if (isUr) "کارٹ میں شامل کریں" else "Add to Cart")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDetailProduct = null }) {
                    Text(if (isUr) "بند کریں" else "Close")
                }
            }
        )
    }
}

/**
 * Real-time Customer Support Chat Dialog backed by Firestore (`observeSupportChatMessages` and
 * `sendSupportChatMessage`) allowing dealers and distributors to message SNL Customer Support directly
 * from the Catalogue screen.
 */
@Composable
fun CustomerSupportChatDialog(
    userProfile: UserProfile,
    messages: List<SupportChatMessage>,
    repository: SnlRepository,
    isUr: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messageInput by rememberSaveable { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }

    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.US) }

    val sendMessage: (String) -> Unit = { rawText ->
        val trimmed = rawText.trim()
        if (trimmed.isNotBlank() && !isSending) {
            isSending = true
            sendError = null
            scope.launch {
                val result = repository.sendSupportChatMessage(
                    userProfile = userProfile,
                    messageText = trimmed,
                    includeSupportDeskAutoReply = true
                )
                isSending = false
                result.onSuccess {
                    messageInput = ""
                }.onFailure { err ->
                    sendError = err.localizedMessage ?: "Unable to send support message."
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_chat_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SnlNavy,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = SnlYellow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isUr) "ایس این ایل کسٹمر سپورٹ" else "SNL Live Customer Support",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (isUr) {
                                "کراچی ہیڈ آفس • ریئل ٹائم فائر اسٹور چیٹ"
                            } else {
                                "Karachi HQ Desk • Real-Time Firestore Chat"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SnlGreenAccept,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Inquiry Chips for 1-Tap B2B Support Questions
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val quickPrompts = listOf(
                        "pricing" to (if (isUr) "بلک کارٹن ڈسکاؤنٹ ریٹ" else "Bulk Carton Rebate & Tier Pricing"),
                        "dispatch" to (if (isUr) "آرڈر ڈسپیچ اور بلٹی اپ ڈیٹ" else "Order Dispatch & Bilty Status"),
                        "stock" to (if (isUr) "20W-50 اور 10W-40 اسٹاک" else "Check 20W-50 / 10W-40 Stock")
                    )
                    items(quickPrompts, key = { it.first }) { (key, label) ->
                        AssistChip(
                            onClick = { sendMessage(label) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("support_quick_chip_$key")
                        )
                    }
                }

                HorizontalDivider()

                // Real-Time Chat Messages Stream
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .testTag("support_chat_messages_container")
                ) {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = SnlNavy,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isUr) {
                                    "السلام علیکم! ایس این ایل لبریکنٹس سپورٹ ڈیسک میں خوش آمدید۔ قیمتوں، اسٹاک یا آرڈر ڈسپیچ کے بارے میں اپنا پیغام لکھیں۔"
                                } else {
                                    "Assalam-o-Alaikum! Welcome to SNL Lubricants B2B Support. Ask us about ${userProfile.priceGroupId} pricing, stock availability, or order dispatch."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                                .testTag("support_chat_lazy_column"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val isCustomer = msg.senderRole == "CUSTOMER"
                                val timestampLabel = msg.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Just now"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isCustomer) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 14.dp,
                                            topEnd = 14.dp,
                                            bottomStart = if (isCustomer) 14.dp else 4.dp,
                                            bottomEnd = if (isCustomer) 4.dp else 14.dp
                                        ),
                                        color = if (isCustomer) SnlNavy else MaterialTheme.colorScheme.surface,
                                        tonalElevation = 2.dp,
                                        shadowElevation = 1.dp,
                                        modifier = Modifier.fillMaxWidth(0.86f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isCustomer) {
                                                        if (isUr) "آپ (${msg.senderName})" else "You (${msg.senderName})"
                                                    } else {
                                                        msg.senderName
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCustomer) SnlYellow else SnlRed
                                                )
                                                Text(
                                                    text = timestampLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isCustomer) {
                                                        Color.White.copy(alpha = 0.7f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                            Text(
                                                text = msg.messageText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isCustomer) {
                                                    Color.White
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (sendError != null) {
                    Text(
                        text = sendError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("support_chat_error_text")
                    )
                }

                // Message Input & Send Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = {
                            messageInput = it
                            sendError = null
                        },
                        placeholder = {
                            Text(
                                text = if (isUr) "اپنا پیغام لکھیں..." else "Type your support message...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("support_chat_message_input")
                    )

                    Button(
                        onClick = { sendMessage(messageInput) },
                        enabled = messageInput.isNotBlank() && !isSending,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SnlRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        modifier = Modifier.testTag("support_chat_send_button")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (isUr) "پیغام بھیجیں" else "Send Message"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    InvoiceAndReportPdfGenerator.openWhatsAppClickToChat(
                        context = context,
                        phoneNumber = "+923101005253",
                        message = "Assalam-o-Alaikum SNL Support Desk, I am contacting you from the SNL B2B App (${userProfile.businessName} - ${userProfile.priceGroupId})."
                    )
                },
                modifier = Modifier.testTag("support_chat_whatsapp_button")
            ) {
                Text(if (isUr) "واٹس ایپ ہیلپ لائن" else "WhatsApp Helpline")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("support_chat_close_button")
            ) {
                Text(if (isUr) "بند کریں" else "Close")
            }
        }
    )
}

@Composable
private fun ProductCatalogueCard(
    product: Product,
    authorizedPricePkr: Double?,
    priceGroupCode: String,
    isFavourite: Boolean,
    isUr: Boolean,
    onToggleFavourite: () -> Unit,
    onAddToCart: (Int) -> Unit,
    onOpenDetails: () -> Unit
) {
    var selectedQty by remember { mutableIntStateOf(product.minOrderQty.toInt().coerceAtLeast(1)) }
    val inStock = product.availableStock > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetails() }
            .testTag("product_card_${product.sku}"),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Branded SNL Product Thumbnail
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    tonalElevation = 2.dp,
                    modifier = Modifier.size(64.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_snl_logo),
                        contentDescription = product.productName,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = SnlNavy.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${product.sku} • #${product.serialNo} • ${product.categoryName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SnlNavy,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleFavourite,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favourite",
                                tint = if (isFavourite) SnlRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = product.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = SnlRed.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Grade: ${product.grade}",
                                style = MaterialTheme.typography.labelMedium,
                                color = SnlRed,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Pack: ${product.packagingSize}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isUr) "آپ کی قیمت ($priceGroupCode):" else "Authorized Price ($priceGroupCode):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (authorizedPricePkr != null && authorizedPricePkr > 0) {
                        Text(
                            text = formatPkrPrice(authorizedPricePkr),
                            style = MaterialTheme.typography.titleLarge,
                            color = SnlGreenAccept,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = if (isUr) "قیمت زیرِ تصدیق (-)" else "Price On Request (-)",
                            style = MaterialTheme.typography.titleMedium,
                            color = SnlAmberWarn,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (inStock) {
                            if (isUr) "دستیاب اسٹاک: ${product.availableStock}" else "In Stock (${product.availableStock} ${product.packagingUnit}s)"
                        } else {
                            if (isUr) "اسٹاک ختم" else "Out of Stock"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (inStock) SnlGreenAccept else SnlRed
                    )
                }

                if (authorizedPricePkr != null && authorizedPricePkr > 0 && inStock) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (selectedQty > 1) selectedQty-- },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease quantity", modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "$selectedQty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        IconButton(
                            onClick = { selectedQty++ },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase quantity", modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onAddToCart(selectedQty) },
                            colors = ButtonDefaults.buttonColors(containerColor = SnlRed, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("add_to_cart_${product.sku}")
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isUr) "شامل کریں" else "Add")
                        }
                    }
                }
            }
        }
    }
}

/**
 * `CartScreen` composable that binds to `CartViewModel` (for persistent local cart state,
 * quantity selection, and price-group subtotal calculation) and renders the full B2B cart and checkout UI.
 */
@Composable
fun CartScreen(
    cartViewModel: com.example.ui.viewmodel.CartViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    userProfile: UserProfile = UserProfile(
        businessName = "Authorized SNL Partner",
        role = "DISTRIBUTOR",
        priceGroupId = "DISTRIBUTOR",
        accountStatus = "ACTIVE"
    ),
    cartItemsOverride: List<CartItem>? = null,
    deliveryChargesPkr: Double = 0.0,
    language: UiLanguage = UiLanguage.URDU,
    isBusy: Boolean = false,
    onUpdateQuantity: ((String, Int) -> Unit)? = null,
    onClearCart: (() -> Unit)? = null,
    onSubmitOrder: (
        deliveryAddress: String,
        city: String,
        phone: String,
        instructions: String,
        paymentMethod: String,
        onCreated: (Order) -> Unit
    ) -> Unit = { _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(userProfile.uid, userProfile.priceGroupId) {
        cartViewModel.bindLocalPersistence(
            context = context,
            userId = userProfile.uid.ifBlank { "default_user" },
            priceGroupId = userProfile.priceGroupId.ifBlank { "DISTRIBUTOR" }
        )
    }

    val vmCartItems by cartViewModel.cartItems.collectAsStateWithLifecycle()
    val activeItems = cartItemsOverride ?: vmCartItems

    CartAndCheckoutScreen(
        userProfile = userProfile,
        cartItems = activeItems,
        deliveryChargesPkr = deliveryChargesPkr,
        language = language,
        isBusy = isBusy,
        onUpdateQuantity = { sku, qty ->
            cartViewModel.updateQuantity(sku, qty)
            onUpdateQuantity?.invoke(sku, qty)
        },
        onClearCart = {
            cartViewModel.clearCart()
            onClearCart?.invoke()
        },
        onSubmitOrder = onSubmitOrder
    )
}

@Composable
fun CartAndCheckoutScreen(
    userProfile: UserProfile,
    cartItems: List<CartItem>,
    deliveryChargesPkr: Double,
    language: UiLanguage,
    isBusy: Boolean,
    onUpdateQuantity: (String, Int) -> Unit,
    onClearCart: () -> Unit,
    onSubmitOrder: (
        deliveryAddress: String,
        city: String,
        phone: String,
        instructions: String,
        paymentMethod: String,
        onCreated: (Order) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val isUr = language == UiLanguage.URDU
    var city by remember(userProfile.city) { mutableStateOf(userProfile.city.ifBlank { "Karachi" }) }
    var phone by remember(userProfile.phone) { mutableStateOf(userProfile.phone.ifBlank { "+92 310 100 52 53" }) }
    var deliveryAddress by remember(userProfile.address) { mutableStateOf(userProfile.address) }
    var instructions by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("COD") }
    var confirmedOrderDialog by remember { mutableStateOf<Order?>(null) }

    val subtotal = remember(cartItems, userProfile.priceGroupId) {
        cartItems.sumOf { item ->
            val unitPrice = item.product.priceForGroup(userProfile.priceGroupId) ?: item.unitPricePkr
            unitPrice * item.quantity
        }
    }
    val grandTotal = subtotal + deliveryChargesPkr

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cart_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUr) "بی ٹو بی شاپنگ کارٹ اور چیک آؤٹ" else "B2B Shopping Cart & Checkout",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isUr) {
                            "پرائس گروپ: ${userProfile.priceGroupId} • مقامی کارٹ محفوظ ہے (آف لائن قابلِ ترمیم)"
                        } else {
                            "Assigned Price Group: ${userProfile.priceGroupId} • Offline-Editable Persistent Cart"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = SnlNavy,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("cart_price_group_label")
                    )
                }
                if (cartItems.isNotEmpty()) {
                    TextButton(
                        onClick = onClearCart,
                        modifier = Modifier.testTag("clear_cart_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isUr) "خالی کریں" else "Clear Cart")
                    }
                }
            }
        }

        // Offline Cart & Firestore Persistence Readiness Banner
        item {
            val isSimulatedOffline by FirestorePersistenceConfig.isSimulatedOffline.collectAsStateWithLifecycle()
            val isServingFromCache by FirestorePersistenceConfig.isServingFromOfflineCache.collectAsStateWithLifecycle()
            val isOfflineActive = isSimulatedOffline || isServingFromCache
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isOfflineActive) {
                    SnlAmberWarn.copy(alpha = 0.14f)
                } else {
                    SnlGreenAccept.copy(alpha = 0.12f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cart_offline_persistence_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isOfflineActive) Icons.Default.CloudOff else Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = if (isOfflineActive) SnlAmberWarn else SnlGreenAccept,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isOfflineActive) {
                            if (isUr) {
                                "آف لائن موڈ: آپ کارٹ میں مقدار تبدیل کر سکتے ہیں اور نئے آئٹمز شامل کر سکتے ہیں۔"
                            } else {
                                "Offline Mode: Cart quantities and items remain fully editable on-device and sync when back online."
                            }
                        } else {
                            if (isUr) {
                                "آف لائن کارٹ سنک فعال: انٹرنیٹ منقطع ہونے پر بھی کارٹ محفوظ اور قابلِ ترمیم رہتی ہے۔"
                            } else {
                                "Offline Persistence Active: Cart quantities and items remain accessible and editable offline."
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOfflineActive) SnlAmberWarn else SnlGreenAccept
                    )
                }
            }
        }

        if (cartItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_cart_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isUr) "آپ کی شاپنگ کارٹ خالی ہے" else "Your B2B Shopping Cart is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isUr) "کیٹلاگ سے مصنوعات منتخب کریں۔" else "Browse the SNL Catalogue to add lubricants at your authorized tier price.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(cartItems, key = { it.product.sku }) { item ->
                val activeUnitPrice = item.product.priceForGroup(userProfile.priceGroupId) ?: item.unitPricePkr
                val activeLineTotal = activeUnitPrice * item.quantity
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cart_item_card_${item.product.sku}"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.product.displayName} (${item.product.sku})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${item.product.grade} • ${item.product.packagingSize} • Tier: ${userProfile.priceGroupId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${formatPkrPrice(activeUnitPrice)} x ${item.quantity} = ${formatPkrPrice(activeLineTotal)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = SnlGreenAccept
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onUpdateQuantity(item.product.sku, item.quantity - 1) },
                                modifier = Modifier.testTag("cart_qty_dec_${item.product.sku}")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }
                            Text(
                                text = "${item.quantity}",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("cart_qty_value_${item.product.sku}")
                            )
                            IconButton(
                                onClick = { onUpdateQuantity(item.product.sku, item.quantity + 1) },
                                modifier = Modifier.testTag("cart_qty_inc_${item.product.sku}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }
                }
            }

            // Checkout Details Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isUr) "ڈیلیوری اور ادائیگی کی تفصیلات" else "Delivery & Payment Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${userProfile.businessName} (${userProfile.fullName}) • ID: ${userProfile.customerCode} • Tier: ${userProfile.priceGroupId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text(if (isUr) "رابطہ نمبر (Phone)" else "Contact Phone *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text(if (isUr) "شہر (City)" else "City *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = deliveryAddress,
                            onValueChange = { deliveryAddress = it },
                            label = { Text(if (isUr) "مکمل ڈیلیوری ایڈریس" else "Complete Delivery Address *") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        OutlinedTextField(
                            value = instructions,
                            onValueChange = { instructions = it },
                            label = { Text(if (isUr) "خصوصی ہدایات (Order Instructions)" else "Order / Dispatch Instructions") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = if (isUr) "ادائیگی کا طریقہ (Payment Method):" else "Select Payment Method:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = paymentMethod == "COD",
                                onClick = { paymentMethod = "COD" },
                                label = { Text(if (isUr) "کیش آن ڈیلیوری" else "Cash on Delivery") }
                            )
                            FilterChip(
                                selected = paymentMethod == "BANK_TRANSFER",
                                onClick = { paymentMethod = "BANK_TRANSFER" },
                                label = { Text(if (isUr) "بینک ٹرانسفر" else "Bank Transfer") }
                            )
                            if (userProfile.creditAllowed || userProfile.role == "SUPER_ADMIN") {
                                FilterChip(
                                    selected = paymentMethod == "CREDIT_ACCOUNT",
                                    onClick = { paymentMethod = "CREDIT_ACCOUNT" },
                                    label = { Text(if (isUr) "کریڈٹ اکاؤنٹ" else "Credit Account") }
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isUr) "سب ٹوٹل (Subtotal):" else "Subtotal (${cartItems.sumOf { it.quantity }} Cartons/Units):")
                            Text(
                                text = formatPkrPrice(subtotal),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("cart_subtotal_text")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isUr) "ڈیلیوری چارجز:" else "Delivery Charges:")
                            Text(formatPkrPrice(deliveryChargesPkr), fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isUr) "مجموعی رقم (Grand Total):" else "Estimated Final Amount:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatPkrPrice(grandTotal),
                                style = MaterialTheme.typography.titleLarge,
                                color = SnlRed,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("cart_grand_total_text")
                            )
                        }

                        Button(
                            onClick = {
                                onSubmitOrder(
                                    deliveryAddress.ifBlank { "Karachi Warehouse" },
                                    city.ifBlank { "Karachi" },
                                    phone.ifBlank { "+923101005253" },
                                    instructions,
                                    paymentMethod
                                ) { created ->
                                    confirmedOrderDialog = created
                                }
                            },
                            enabled = !isBusy && cartItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = SnlRed, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("place_b2b_order_button")
                        ) {
                            Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isUr) "آرڈر جمع کروائیں (Submit for Admin Approval)" else "Place Order for SNL Admin Approval",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    confirmedOrderDialog?.let { order ->
        AlertDialog(
            onDismissRequest = { confirmedOrderDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SnlGreenAccept)
                    Spacer(Modifier.width(8.dp))
                    Text("Order ${order.orderNumber} Submitted!")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SAIFI NATIONAL LUBRICANTS has received your order.")
                    Text("Status: PENDING ADMIN APPROVAL", fontWeight = FontWeight.Bold, color = SnlAmberWarn)
                    Text("Grand Total: ${formatPkrPrice(order.grandTotal)} (Immutable Price Snapshot Saved)")
                    Text("You can download/print your official A4 PDF invoice or send the WhatsApp summary to SNL HQ (+92 310 100 52 53):")
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            InvoiceAndReportPdfGenerator.shareOrderInvoicePdf(context, order)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirmed_order_share_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Share PDF Invoice", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share / Save Printable PDF Invoice")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val msg = InvoiceAndReportPdfGenerator.buildWhatsAppOrderMessage(order)
                        InvoiceAndReportPdfGenerator.openWhatsAppClickToChat(
                            context,
                            "+923101005253",
                            msg
                        )
                        confirmedOrderDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnlGreenAccept)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Send to SNL WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmedOrderDialog = null }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun OrderHistoryScreen(
    currentUserId: String = remember {
        runCatching { Firebase.auth.currentUser?.uid.orEmpty() }.getOrDefault("")
    },
    ordersOverride: List<Order>? = null,
    language: UiLanguage = UiLanguage.ENGLISH,
    onTrackOrder: ((Order) -> Unit)? = null,
    onReorder: (Order) -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isUr = language == UiLanguage.URDU

    val repository = remember(context) { SnlRepository(context) }
    val firestoreOrders by remember(repository, currentUserId, ordersOverride) {
        if (ordersOverride != null || currentUserId.isBlank()) {
            flowOf(ordersOverride ?: emptyList())
        } else {
            repository.observeCustomerOrders(currentUserId).catch { emit(emptyList()) }
        }
    }.collectAsStateWithLifecycle(initialValue = ordersOverride ?: emptyList())

    val orders = ordersOverride ?: firestoreOrders
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var trackedOrderInline by remember { mutableStateOf<Order?>(null) }
    var orderPendingPdfSave by remember { mutableStateOf<Order?>(null) }

    val saveInvoicePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val targetOrder = orderPendingPdfSave
        if (uri != null && targetOrder != null) {
            InvoiceAndReportPdfGenerator.generatePrintableOrderInvoicePdf(
                context = context,
                order = targetOrder,
                destinationUri = uri
            ).onSuccess {
                onMessage("Saved printable PDF invoice for ${targetOrder.orderNumber}")
            }.onFailure {
                onMessage("Save PDF error: ${it.localizedMessage}")
            }
        }
        orderPendingPdfSave = null
    }

    val inlineOrder = trackedOrderInline
    if (inlineOrder != null && onTrackOrder == null) {
        OrderTrackingScreen(
            orderId = inlineOrder.id.ifBlank { inlineOrder.orderNumber },
            initialOrder = inlineOrder,
            availableOrders = orders,
            language = language,
            onBack = { trackedOrderInline = null },
            onReorder = onReorder,
            onMessage = onMessage
        )
        return
    }

    val filteredOrders = remember(orders, selectedStatusFilter) {
        if (selectedStatusFilter == "ALL") {
            orders
        } else {
            orders.filter { it.orderStatus.equals(selectedStatusFilter, ignoreCase = true) }
        }
    }

    val totalHistoryValuePkr = remember(filteredOrders) {
        filteredOrders.sumOf { it.grandTotal }
    }

    val statusFilters = remember {
        listOf(
            "ALL" to "All Orders",
            "PENDING_ADMIN_APPROVAL" to "Pending Approval",
            "CONFIRMED" to "Confirmed",
            "DISPATCHED" to "Dispatched",
            "DELIVERED" to "Delivered"
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("order_history_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SnlNavy,
                    contentColor = Color.White
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isUr) "میرے آرڈرز اور ہسٹری (Order History)" else "Order History & Dispatch Tracking",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isUr) {
                            "آپ کے تمام پچھلے آرڈرز، موجودہ اسٹیٹس اور کل مالیت (PKR)۔"
                        } else {
                            "Past orders filtered for your account with live status & locked PKR price snapshots."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isUr) "کل آرڈرز: ${filteredOrders.size}" else "Orders: ${filteredOrders.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = SnlYellow,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Text(
                            text = if (isUr) {
                                "مجموعی مالیت: ${formatPkrPrice(totalHistoryValuePkr)}"
                            } else {
                                "Total Value: ${formatPkrPrice(totalHistoryValuePkr)}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = SnlYellow,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.testTag("order_history_total_value")
                        )
                    }
                }
            }
        }

        item {
            FcmOrderPushStatusCard(
                targetOrder = filteredOrders.firstOrNull() ?: orders.firstOrNull(),
                isUr = isUr,
                onMessage = onMessage
            )
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(statusFilters, key = { it.first }) { (statusKey, statusLabel) ->
                    FilterChip(
                        selected = selectedStatusFilter == statusKey,
                        onClick = { selectedStatusFilter = statusKey },
                        label = { Text(statusLabel) },
                        modifier = Modifier.testTag("order_status_filter_$statusKey")
                    )
                }
            }
        }

        if (filteredOrders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_history_empty_state"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = "No orders",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isUr) "کوئی آرڈر ریکارڈ نہیں ملا" else "No Past Orders Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isUr) {
                                "کیٹلاگ سے مصنوعات کارٹ میں شامل کر کے اپنا پہلا آرڈر بک کریں۔"
                            } else {
                                "Orders placed under your account will appear here with real-time status and total PKR value."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredOrders, key = { it.id.ifBlank { it.orderNumber } }) { order ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_history_card_${order.orderNumber}"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = order.orderNumber,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${order.priceGroupId.replace('_', ' ')} • ${order.city}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = when (order.orderStatus) {
                                    "CONFIRMED", "DELIVERED", "DISPATCHED" -> SnlGreenAccept.copy(alpha = 0.15f)
                                    "PENDING_ADMIN_APPROVAL" -> SnlAmberWarn.copy(alpha = 0.15f)
                                    else -> SnlNavy.copy(alpha = 0.12f)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("order_status_${order.orderNumber}")
                            ) {
                                Text(
                                    text = order.orderStatus.replace('_', ' '),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (order.orderStatus) {
                                        "CONFIRMED", "DELIVERED", "DISPATCHED" -> SnlGreenAccept
                                        "PENDING_ADMIN_APPROVAL" -> SnlAmberWarn
                                        else -> SnlNavy
                                    },
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        order.parsedItems().forEach { line ->
                            Text(
                                text = "• ${line.productName} (${line.packagingSize} | ${line.grade}) x ${line.quantity} @ ${formatPkrPrice(line.unitPricePkr)} = ${formatPkrPrice(line.lineTotalPkr)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Payment: ${order.paymentMethod} (${order.paymentStatus})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Total: ${formatPkrPrice(order.grandTotal)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = SnlRed,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("order_total_${order.orderNumber}")
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (onTrackOrder != null) {
                                        onTrackOrder(order)
                                    } else {
                                        trackedOrderInline = order
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SnlRed),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("track_order_button_${order.orderNumber}")
                            ) {
                                Icon(Icons.Default.TrackChanges, contentDescription = "Track Order", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isUr) "لائیو ٹریکنگ" else "Track Live")
                            }
                            OutlinedButton(
                                onClick = { onReorder(order) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reorder_button_${order.orderNumber}")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reorder", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isUr) "دوبارہ آرڈر" else "Reorder")
                            }
                            Button(
                                onClick = {
                                    InvoiceAndReportPdfGenerator.shareOrderInvoicePdf(context, order)
                                        .onSuccess { file ->
                                            onMessage("Printable PDF Invoice ready: ${file.name}")
                                        }
                                        .onFailure {
                                            onMessage("PDF error: ${it.localizedMessage}")
                                        }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SnlNavy),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("invoice_pdf_button_${order.orderNumber}")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Share PDF Invoice", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isUr) "انوائس" else "Share PDF")
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    orderPendingPdfSave = order
                                    saveInvoicePdfLauncher.launch("${order.orderNumber}_Invoice.pdf")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_invoice_pdf_button_${order.orderNumber}")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Save PDF Invoice", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isUr) "پی ڈی ایف محفوظ کریں" else "Save PDF")
                            }
                            OutlinedButton(
                                onClick = {
                                    InvoiceAndReportPdfGenerator.printOrderInvoicePdf(context, order)
                                        .onSuccess {
                                            onMessage("Opened print dialog for ${order.orderNumber}")
                                        }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("print_invoice_pdf_button_${order.orderNumber}")
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Print PDF Invoice", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isUr) "پرنٹ انوائس" else "Print PDF")
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TrackingStageStep(
    val key: String,
    val titleEn: String,
    val titleUr: String,
    val subtitleEn: String,
    val subtitleUr: String
)

private fun mapOrderStatusToStageIndex(status: String): Int = when (status.uppercase(Locale.US)) {
    "PENDING_ADMIN_APPROVAL" -> 0
    "CONFIRMED" -> 1
    "PROCESSING", "READY_FOR_DISPATCH" -> 2
    "DISPATCHED" -> 3
    "DELIVERED" -> 4
    else -> 0
}

private fun formatStatusDisplayLabel(status: String): String = when (status.uppercase(Locale.US)) {
    "PENDING_ADMIN_APPROVAL" -> "Pending Approval"
    "CONFIRMED" -> "Confirmed"
    "PROCESSING", "READY_FOR_DISPATCH" -> "Processing"
    "DISPATCHED" -> "Dispatched"
    "DELIVERED" -> "Delivered"
    "CANCELLED" -> "Cancelled"
    "REJECTED" -> "Rejected"
    else -> status.replace('_', ' ')
}

/**
 * `OrderTrackingScreen` component that displays the real-time status of a specific order
 * (e.g., `Pending Approval`, `Confirmed`, `Processing`, `Dispatched`, `Delivered`) using
 * Firestore snapshot listeners (`SnlRepository.observeOrderRealtime`).
 */
@Composable
fun OrderTrackingScreen(
    orderId: String = "",
    initialOrder: Order? = null,
    availableOrders: List<Order> = emptyList(),
    language: UiLanguage = UiLanguage.ENGLISH,
    onBack: (() -> Unit)? = null,
    onReorder: (Order) -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }

    val context = LocalContext.current
    val isUr = language == UiLanguage.URDU
    val repository = remember(context) { SnlRepository(context) }

    val defaultOrderId = remember(orderId, initialOrder, availableOrders) {
        orderId.ifBlank {
            initialOrder?.id?.ifBlank { initialOrder.orderNumber }
                ?: availableOrders.firstOrNull()?.let { it.id.ifBlank { it.orderNumber } }
                ?: ""
        }
    }

    var activeOrderId by remember(defaultOrderId) { mutableStateOf(defaultOrderId) }
    var lookupInput by remember(defaultOrderId) {
        mutableStateOf(
            initialOrder?.orderNumber?.ifBlank { defaultOrderId } ?: defaultOrderId
        )
    }

    val realtimeOrder by remember(repository, activeOrderId) {
        if (activeOrderId.isBlank()) {
            flowOf(initialOrder)
        } else {
            repository.observeOrderRealtime(activeOrderId).catch { emit(initialOrder) }
        }
    }.collectAsStateWithLifecycle(initialValue = initialOrder)

    val displayedOrder = remember(realtimeOrder, activeOrderId, initialOrder, availableOrders) {
        realtimeOrder
            ?: initialOrder?.takeIf {
                it.id.equals(activeOrderId, ignoreCase = true) ||
                    it.orderNumber.equals(activeOrderId, ignoreCase = true)
            }
            ?: availableOrders.firstOrNull {
                it.id.equals(activeOrderId, ignoreCase = true) ||
                    it.orderNumber.equals(activeOrderId, ignoreCase = true)
            }
    }

    val stages = remember {
        listOf(
            TrackingStageStep(
                key = "PENDING_ADMIN_APPROVAL",
                titleEn = "Order Placed (Pending Approval)",
                titleUr = "آرڈر موصول (منظوری کا انتظار)",
                subtitleEn = "Order submitted with locked PKR price snapshot awaiting SNL HQ verification.",
                subtitleUr = "آرڈر جمع ہو چکا ہے اور ہیڈ آفس کی منظوری کا منتظر ہے۔"
            ),
            TrackingStageStep(
                key = "CONFIRMED",
                titleEn = "Confirmed & Stock Reserved",
                titleUr = "آرڈر منظور اور اسٹاک محفوظ",
                subtitleEn = "Approved by SNL Admin; lubricant cartons reserved in Karachi warehouse.",
                subtitleUr = "ایڈمن نے آرڈر منظور کر لیا ہے اور اسٹاک مختص کر دیا گیا ہے۔"
            ),
            TrackingStageStep(
                key = "PROCESSING",
                titleEn = "Processing",
                titleUr = "پروسیسنگ اور پیکنگ جاری",
                subtitleEn = "Cartons are being inspected, packed, and prepared for dispatch.",
                subtitleUr = "آپ کے آرڈر کی پیکنگ اور بلٹی کی تیاری جاری ہے۔"
            ),
            TrackingStageStep(
                key = "DISPATCHED",
                titleEn = "Dispatched",
                titleUr = "روانہ کر دیا گیا (Dispatched)",
                subtitleEn = "Shipment has left the SNL Karachi warehouse and is in transit to your city.",
                subtitleUr = "مال گودام سے آپ کے شہر کے لیے روانہ کر دیا گیا ہے۔"
            ),
            TrackingStageStep(
                key = "DELIVERED",
                titleEn = "Delivered",
                titleUr = "پہنچا دیا گیا (Delivered)",
                subtitleEn = "Order successfully delivered to the destination address.",
                subtitleUr = "آرڈر کامیابی کے ساتھ آپ کے پتے پر پہنچا دیا گیا ہے۔"
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("order_tracking_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SnlNavy,
                    contentColor = Color.White
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onBack != null) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier.testTag("order_tracking_back_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Column {
                                Text(
                                    text = if (isUr) "لائیو آرڈر ٹریکنگ (Real-Time Tracking)" else "Real-Time Order Tracking",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isUr) {
                                        "فائر اسٹور اسنیپ شاٹ لسنر کے ذریعے براہِ راست اسٹیٹس اپ ڈیٹس"
                                    } else {
                                        "Live Firestore snapshot listener • Instant status updates"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SnlYellow
                                )
                            }
                        }
                        Icon(
                            Icons.Default.TrackChanges,
                            contentDescription = "Live Snapshot Listener Active",
                            tint = SnlYellow,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        item {
            FcmOrderPushStatusCard(
                targetOrder = displayedOrder ?: availableOrders.firstOrNull(),
                isUr = isUr,
                onMessage = onMessage
            )
        }

        // Order ID / Order Number lookup bar + recent order quick selector chips
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = lookupInput,
                            onValueChange = { lookupInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("order_tracking_id_input"),
                            singleLine = true,
                            label = {
                                Text(if (isUr) "آرڈر نمبر درج کریں (مثلاً SNL-...)" else "Order Number or ID (e.g. SNL-...)")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Order Lookup")
                            }
                        )
                        Button(
                            onClick = {
                                if (lookupInput.isNotBlank()) {
                                    activeOrderId = lookupInput.trim()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SnlRed),
                            modifier = Modifier.testTag("track_order_lookup_button")
                        ) {
                            Text(if (isUr) "ٹریک کریں" else "Track")
                        }
                    }

                    if (availableOrders.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableOrders, key = { it.id.ifBlank { it.orderNumber } }) { candidate ->
                                val isSelected = displayedOrder?.orderNumber == candidate.orderNumber ||
                                    activeOrderId.equals(candidate.id, ignoreCase = true) ||
                                    activeOrderId.equals(candidate.orderNumber, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val nextId = candidate.id.ifBlank { candidate.orderNumber }
                                        activeOrderId = nextId
                                        lookupInput = candidate.orderNumber.ifBlank { nextId }
                                    },
                                    label = { Text(candidate.orderNumber) },
                                    modifier = Modifier.testTag("order_tracking_chip_${candidate.orderNumber}")
                                )
                            }
                        }
                    }
                }
            }
        }

        val order = displayedOrder
        if (order == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_tracking_empty_state"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = "No order selected",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isUr) "ٹریکنگ کے لیے آرڈر منتخب کریں" else "No Order Found for Live Tracking",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isUr) {
                                "اپنا آرڈر نمبر اوپر درج کریں تاکہ ریئل ٹائم اسٹیٹس دیکھا جا سکے۔"
                            } else {
                                "Enter an SNL Order Number above or select one of your orders to stream live status updates from Firestore."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            val activeStageIndex = mapOrderStatusToStageIndex(order.orderStatus)
            val statusDisplay = formatStatusDisplayLabel(order.orderStatus)

            // Current Live Status Summary Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_tracking_summary_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = order.orderNumber,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${order.businessName.ifBlank { order.customerName }} • ${order.city}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = when (order.orderStatus.uppercase(Locale.US)) {
                                    "CONFIRMED", "DELIVERED", "DISPATCHED" -> SnlGreenAccept.copy(alpha = 0.16f)
                                    "PROCESSING", "READY_FOR_DISPATCH", "PENDING_ADMIN_APPROVAL" -> SnlAmberWarn.copy(alpha = 0.18f)
                                    else -> SnlRed.copy(alpha = 0.15f)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("order_tracking_live_status")
                            ) {
                                Text(
                                    text = statusDisplay,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = when (order.orderStatus.uppercase(Locale.US)) {
                                        "CONFIRMED", "DELIVERED", "DISPATCHED" -> SnlGreenAccept
                                        "PROCESSING", "READY_FOR_DISPATCH", "PENDING_ADMIN_APPROVAL" -> SnlAmberWarn
                                        else -> SnlRed
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isUr) "ادائیگی کا طریقہ: ${order.paymentMethod}" else "Payment: ${order.paymentMethod} (${order.paymentStatus})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = if (isUr) "ڈیلیوری پتہ: ${order.deliveryAddress}" else "Delivery: ${order.deliveryAddress}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatPkrPrice(order.grandTotal),
                                style = MaterialTheme.typography.titleMedium,
                                color = SnlRed,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("order_tracking_grand_total")
                            )
                        }
                    }
                }
            }

            // Real-Time Milestone Timeline Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_tracking_timeline_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isUr) "آرڈر کے مراحل (Fulfillment Milestones)" else "Fulfillment Milestones",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))

                        stages.forEachIndexed { index, stage ->
                            val isCompleted = index < activeStageIndex || order.orderStatus.equals("DELIVERED", ignoreCase = true)
                            val isCurrent = index == activeStageIndex && !order.orderStatus.equals("DELIVERED", ignoreCase = true)

                            val stepTint = when {
                                isCompleted -> SnlGreenAccept
                                isCurrent -> SnlAmberWarn
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .testTag("order_tracking_step_${stage.key}"),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(stepTint.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isCompleted -> Icons.Default.CheckCircle
                                            isCurrent -> Icons.Default.Schedule
                                            else -> Icons.Default.RadioButtonUnchecked
                                        },
                                        contentDescription = stage.titleEn,
                                        tint = stepTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isUr) stage.titleUr else stage.titleEn,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isCurrent || isCompleted) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent || isCompleted) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        Surface(
                                            color = stepTint.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = when {
                                                    isCompleted -> if (isUr) "مکمل" else "Completed"
                                                    isCurrent -> if (isUr) "جاری ہے" else "Active Now"
                                                    else -> if (isUr) "باقی ہے" else "Pending"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = stepTint,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (isUr) stage.subtitleUr else stage.subtitleEn,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Locked Line-Item Price Snapshot Card & Actions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = "Order Items",
                                tint = SnlNavy,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isUr) "آرڈر کی تفصیلات (${order.totalCartons} کارٹن)" else "Ordered Line Items (${order.totalCartons} Cartons)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        order.parsedItems().forEach { line ->
                            Text(
                                text = "• ${line.productName} (${line.packagingSize} | ${line.grade}) x ${line.quantity} @ ${formatPkrPrice(line.unitPricePkr)} = ${formatPkrPrice(line.lineTotalPkr)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onReorder(order) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tracking_reorder_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reorder", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isUr) "دوبارہ آرڈر کریں" else "Reorder Items")
                            }
                            Button(
                                onClick = {
                                    InvoiceAndReportPdfGenerator.shareOrderInvoicePdf(context, order)
                                        .onSuccess { file ->
                                            onMessage("Printable PDF Invoice ready: ${file.name}")
                                        }
                                        .onFailure {
                                            onMessage("PDF error: ${it.localizedMessage}")
                                        }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SnlNavy),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tracking_invoice_pdf_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Share PDF Invoice", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isUr) "پی ڈی ایف انوائس" else "Share PDF")
                            }
                            OutlinedButton(
                                onClick = {
                                    InvoiceAndReportPdfGenerator.printOrderInvoicePdf(context, order)
                                        .onSuccess {
                                            onMessage("Opened print spooler for ${order.orderNumber}")
                                        }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tracking_print_invoice_pdf_button")
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Print Invoice", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isUr) "پرنٹ کریں" else "Print PDF")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FcmOrderPushStatusCard(
    targetOrder: Order?,
    isUr: Boolean,
    onMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val fcmToken by com.example.notifications.SnlFcmNotificationManager.fcmToken.collectAsStateWithLifecycle()
    val subscribedTopics by com.example.notifications.SnlFcmNotificationManager.subscribedTopics.collectAsStateWithLifecycle()
    val isFcmEnabled by com.example.notifications.SnlFcmNotificationManager.isFcmEnabled.collectAsStateWithLifecycle()
    val recentPushEvents by com.example.notifications.SnlFcmNotificationManager.recentPushEvents.collectAsStateWithLifecycle()

    var hasNotificationPermission by remember(context) {
        mutableStateOf(com.example.notifications.SnlFcmNotificationManager.hasPostNotificationsPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        onMessage(
            if (granted) {
                "FCM Push Notifications enabled for Dispatched & Delivered order updates!"
            } else {
                "Notification permission denied. In-app FCM push banners remain active."
            }
        )
    }

    androidx.compose.runtime.LaunchedEffect(context) {
        val uid = runCatching { Firebase.auth.currentUser?.uid.orEmpty() }.getOrDefault("")
        com.example.notifications.SnlFcmNotificationManager.initializeFcmForUser(context, uid, "DISTRIBUTOR")
        hasNotificationPermission = com.example.notifications.SnlFcmNotificationManager.hasPostNotificationsPermission(context)
    }

    val sampleOrder = remember(targetOrder) {
        targetOrder ?: Order(
            id = "ord_sample_fcm",
            orderNumber = "SNL-20261108-1042",
            customerId = runCatching { Firebase.auth.currentUser?.uid.orEmpty() }.getOrDefault("dist_user"),
            customerName = "Authorized SNL Distributor",
            businessName = "Karachi Auto Lubricants",
            city = "Karachi",
            deliveryAddress = "Plot 14, Korangi Industrial Area, Karachi",
            priceGroupId = "DISTRIBUTOR",
            totalCartons = 25,
            grandTotal = 262000.0,
            orderStatus = "DISPATCHED"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fcm_push_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = "FCM Order Push Notifications",
                        tint = SnlNavy,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isUr) {
                                "ایف سی ایم پش نوٹیفکیشنز (Dispatched & Delivered)"
                            } else {
                                "FCM Push Notifications (Dispatched & Delivered)"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (isUr) {
                                "آرڈر روانہ (Dispatched) یا ڈیلیور (Delivered) ہونے پر فوری الرٹ"
                            } else {
                                "Topic: ${subscribedTopics.firstOrNull() ?: "snl_distributors_orders"} • Token: ${fcmToken.take(16).ifBlank { "Active" }}..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("fcm_token_status_text")
                        )
                    }
                }
                FilterChip(
                    selected = isFcmEnabled,
                    onClick = {
                        com.example.notifications.SnlFcmNotificationManager.setFcmAlertsEnabled(
                            context,
                            !isFcmEnabled
                        )
                    },
                    label = {
                        Text(
                            text = if (isFcmEnabled) "FCM ON" else "FCM OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    modifier = Modifier.testTag("fcm_card_toggle_chip")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val event = com.example.notifications.SnlFcmNotificationManager.triggerOrderStatusPushNotification(
                            context = context,
                            order = sampleOrder.copy(orderStatus = "DISPATCHED"),
                            newStatus = "DISPATCHED",
                            forceEvenIfAlreadyNotified = true
                        )
                        if (event != null) {
                            onMessage("FCM Push Notification sent: ${event.title}")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("fcm_test_dispatched_push_button")
                ) {
                    Text(
                        text = if (isUr) "ڈسپیچ پش ٹیسٹ" else "Test 'Dispatched' Push",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        val event = com.example.notifications.SnlFcmNotificationManager.triggerOrderStatusPushNotification(
                            context = context,
                            order = sampleOrder.copy(orderStatus = "DELIVERED"),
                            newStatus = "DELIVERED",
                            forceEvenIfAlreadyNotified = true
                        )
                        if (event != null) {
                            onMessage("FCM Push Notification sent: ${event.title}")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnlGreenAccept),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("fcm_test_delivered_push_button")
                ) {
                    Text(
                        text = if (isUr) "ڈیلیورڈ پش ٹیسٹ" else "Test 'Delivered' Push",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!hasNotificationPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(
                    onClick = {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fcm_enable_notifications_button")
                ) {
                    Text(
                        text = if (isUr) "سسٹم پش نوٹیفکیشن کی اجازت دیں" else "Grant System Push Notification Permission",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (recentPushEvents.isNotEmpty()) {
                val latest = recentPushEvents.first()
                Surface(
                    color = SnlGreenAccept.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fcm_latest_push_event_box")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${latest.title} • ${latest.formattedTime}",
                            style = MaterialTheme.typography.labelMedium,
                            color = SnlGreenAccept,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = latest.body,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerOrdersScreen(
    orders: List<Order>,
    language: UiLanguage,
    onReorder: (Order) -> Unit,
    onMessage: (String) -> Unit
) {
    OrderHistoryScreen(
        ordersOverride = orders,
        language = language,
        onReorder = onReorder,
        onMessage = onMessage
    )
}



