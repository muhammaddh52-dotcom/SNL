package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.StackedBarChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Order
import com.example.data.model.UiLanguage
import com.example.data.model.UserProfile
import com.example.data.repository.SnlRepository
import com.example.ui.theme.SnlApproveGreen
import com.example.ui.theme.SnlNavy
import com.example.ui.theme.SnlRed
import com.example.ui.theme.SnlYellow
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private fun formatPkr(amount: Double): String =
    "PKR ${"%,.0f".format(Locale.US, amount)}"

/**
 * Aggregated monthly purchase volume and spending metrics for the logged-in distributor/dealer.
 */
data class MonthlyDistributorMetric(
    val yearMonthKey: String,       // e.g. "2026-11"
    val shortMonthLabel: String,    // e.g. "Nov"
    val fullMonthLabel: String,     // e.g. "Nov 2026"
    val totalSpendPkr: Double,
    val totalCartonsVolume: Long,
    val totalLineItems: Long,
    val orderCount: Int,
    val deliveredSpendPkr: Double,
    val topProductLabel: String
)

/**
 * Aggregated SKU/Product volume & spend summary for the logged-in user.
 */
data class ProductPurchaseBreakdown(
    val sku: String,
    val productName: String,
    val grade: String,
    val packagingSize: String,
    val totalCartons: Int,
    val totalSpendPkr: Double,
    val shareOfSpend: Float
)

enum class DistributorChartMode {
    COMPOSED_VOLUME_AND_SPEND,
    SPENDING_AREA_TREND,
    PURCHASE_VOLUME_BARS
}

enum class DistributorTimeRange(val monthsCount: Int, val labelEn: String, val labelUr: String) {
    SIX_MONTHS(6, "Last 6 Months", "آخری 6 ماہ"),
    TWELVE_MONTHS(12, "Last 12 Months", "آخری 12 ماہ")
}

/**
 * Computes monthly purchase volume (cartons) and spending trends (PKR) across the selected
 * number of months for the logged-in user's orders.
 */
fun buildMonthlyDistributorMetrics(
    orders: List<Order>,
    monthsCount: Int = 6,
    referenceDate: Date = Date()
): List<MonthlyDistributorMetric> {
    val activeOrders = orders.filter {
        it.visibleToCustomer && it.orderStatus !in setOf("CANCELLED", "REJECTED")
    }

    val keyFormatter = SimpleDateFormat("yyyy-MM", Locale.US)
    val shortFormatter = SimpleDateFormat("MMM", Locale.US)
    val fullFormatter = SimpleDateFormat("MMM yyyy", Locale.US)

    val cal = Calendar.getInstance().apply {
        time = referenceDate
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val monthBuckets = mutableListOf<Triple<String, String, String>>()
    val tempCal = cal.clone() as Calendar
    tempCal.add(Calendar.MONTH, -(monthsCount - 1))
    repeat(monthsCount) {
        val d = tempCal.time
        monthBuckets.add(
            Triple(
                keyFormatter.format(d),
                shortFormatter.format(d),
                fullFormatter.format(d)
            )
        )
        tempCal.add(Calendar.MONTH, 1)
    }

    val currentKey = keyFormatter.format(referenceDate)
    val ordersByMonth = activeOrders.groupBy { order ->
        val dt = order.createdAt?.toDate()
        if (dt != null) keyFormatter.format(dt) else currentKey
    }

    return monthBuckets.map { (ymKey, shortLbl, fullLbl) ->
        val monthOrders = ordersByMonth[ymKey].orEmpty()
        val totalSpend = monthOrders.sumOf { it.grandTotal }
        val deliveredSpend = monthOrders
            .filter { it.orderStatus in setOf("DELIVERED", "DISPATCHED", "CONFIRMED") }
            .sumOf { it.grandTotal }
        val totalCartons = monthOrders.sumOf { order ->
            val parsedQty = order.parsedItems().sumOf { it.quantity.toLong() }
            if (order.totalCartons > 0L) order.totalCartons else max(parsedQty, order.itemsCount)
        }
        val totalLineItems = monthOrders.sumOf { max(it.itemsCount, 1L) }

        val topSkuEntry = monthOrders
            .flatMap { it.parsedItems() }
            .groupBy { it.productName.ifBlank { it.sku } }
            .maxByOrNull { (_, rows) -> rows.sumOf { it.quantity } }

        val topLabel = topSkuEntry?.let { (name, rows) ->
            "$name (${rows.sumOf { it.quantity }} ctns)"
        } ?: monthOrders.firstOrNull()?.itemsSummary?.take(36).orEmpty()

        MonthlyDistributorMetric(
            yearMonthKey = ymKey,
            shortMonthLabel = shortLbl,
            fullMonthLabel = fullLbl,
            totalSpendPkr = totalSpend,
            totalCartonsVolume = totalCartons,
            totalLineItems = totalLineItems,
            orderCount = monthOrders.size,
            deliveredSpendPkr = deliveredSpend,
            topProductLabel = topLabel
        )
    }
}

/**
 * Aggregates top purchased products and grades for the logged-in distributor/dealer.
 */
fun buildProductPurchaseBreakdown(orders: List<Order>): List<ProductPurchaseBreakdown> {
    val activeOrders = orders.filter {
        it.visibleToCustomer && it.orderStatus !in setOf("CANCELLED", "REJECTED")
    }
    val allItems = activeOrders.flatMap { it.parsedItems() }
    if (allItems.isEmpty()) return emptyList()

    val totalAllSpend = allItems.sumOf { it.lineTotalPkr }.coerceAtLeast(1.0)
    return allItems
        .groupBy { it.sku.ifBlank { it.productName } }
        .map { (skuKey, items) ->
            val first = items.first()
            val spend = items.sumOf { it.lineTotalPkr }
            val cartons = items.sumOf { it.quantity }
            ProductPurchaseBreakdown(
                sku = skuKey,
                productName = first.productName.ifBlank { skuKey },
                grade = first.grade,
                packagingSize = first.packagingSize,
                totalCartons = cartons,
                totalSpendPkr = spend,
                shareOfSpend = (spend / totalAllSpend).toFloat().coerceIn(0f, 1f)
            )
        }
        .sortedByDescending { it.totalSpendPkr }
        .take(6)
}

/**
 * Distributor Analytics & Purchase Trends Dashboard (`DistributorDashboardScreen`)
 * visualizing monthly purchase volume (cartons) and spending trends (PKR) for the logged-in user.
 */
@Composable
fun DistributorDashboardScreen(
    userProfile: UserProfile,
    ordersOverride: List<Order>? = null,
    language: UiLanguage = UiLanguage.ENGLISH,
    onNavigateToCatalogue: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {}
) {
    val isUr = language == UiLanguage.URDU
    val dbId = stringResource(R.string.firestore_database_id)
    val resolvedUserId = remember(userProfile.id, userProfile.uid) {
        userProfile.uid.ifBlank {
            userProfile.id.ifBlank { Firebase.auth.currentUser?.uid.orEmpty() }
        }
    }

    var firestoreOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    LaunchedEffect(resolvedUserId, dbId) {
        if (resolvedUserId.isNotBlank()) {
            val repo = SnlRepository(Firebase.firestore(dbId))
            repo.observeCustomerOrders(resolvedUserId).collectLatest { list ->
                firestoreOrders = list
            }
        }
    }

    val activeUserOrders = remember(ordersOverride, firestoreOrders) {
        if (!ordersOverride.isNullOrEmpty()) ordersOverride else firestoreOrders
    }

    var selectedTimeRange by rememberSaveable { mutableStateOf(DistributorTimeRange.SIX_MONTHS) }
    var selectedChartMode by rememberSaveable {
        mutableStateOf(DistributorChartMode.COMPOSED_VOLUME_AND_SPEND)
    }

    val monthlyMetrics = remember(activeUserOrders, selectedTimeRange) {
        buildMonthlyDistributorMetrics(
            orders = activeUserOrders,
            monthsCount = selectedTimeRange.monthsCount
        )
    }

    var selectedMonthIndex by rememberSaveable(monthlyMetrics.size) {
        mutableIntStateOf((monthlyMetrics.size - 1).coerceAtLeast(0))
    }
    val safeSelectedIndex = selectedMonthIndex.coerceIn(0, (monthlyMetrics.size - 1).coerceAtLeast(0))
    val selectedMonthMetric = monthlyMetrics.getOrNull(safeSelectedIndex)

    val productBreakdowns = remember(activeUserOrders) {
        buildProductPurchaseBreakdown(activeUserOrders)
    }

    // Overall summary metrics
    val totalPeriodSpendPkr = remember(monthlyMetrics) { monthlyMetrics.sumOf { it.totalSpendPkr } }
    val totalPeriodCartons = remember(monthlyMetrics) { monthlyMetrics.sumOf { it.totalCartonsVolume } }
    val totalPeriodOrders = remember(monthlyMetrics) { monthlyMetrics.sumOf { it.orderCount } }
    val avgMonthlySpendPkr = remember(monthlyMetrics, totalPeriodSpendPkr) {
        val activeMonths = monthlyMetrics.count { it.totalSpendPkr > 0.0 }.coerceAtLeast(1)
        totalPeriodSpendPkr / activeMonths
    }

    // Month-over-Month delta comparison between latest two months with activity (or last 2 calendar months)
    val currentMonthMetric = monthlyMetrics.lastOrNull()
    val previousMonthMetric = if (monthlyMetrics.size >= 2) monthlyMetrics[monthlyMetrics.size - 2] else null
    val spendMoMPercent = remember(currentMonthMetric, previousMonthMetric) {
        val prev = previousMonthMetric?.totalSpendPkr ?: 0.0
        val curr = currentMonthMetric?.totalSpendPkr ?: 0.0
        if (prev > 0.0) ((curr - prev) / prev) * 100.0 else if (curr > 0.0) 100.0 else 0.0
    }
    val volumeMoMPercent = remember(currentMonthMetric, previousMonthMetric) {
        val prev = (previousMonthMetric?.totalCartonsVolume ?: 0L).toDouble()
        val curr = (currentMonthMetric?.totalCartonsVolume ?: 0L).toDouble()
        if (prev > 0.0) ((curr - prev) / prev) * 100.0 else if (curr > 0.0) 100.0 else 0.0
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("distributor_dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Header Card with Partner Tier & Period Filter
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SnlNavy),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("distributor_dashboard_header_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            Surface(
                                shape = CircleShape,
                                color = SnlYellow.copy(alpha = 0.2f),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = "Distributor Analytics",
                                        tint = SnlYellow,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isUr) {
                                        "ڈسٹری بیوٹر خریداری اور اخراجات کا ڈیش بورڈ"
                                    } else {
                                        "Distributor Volume & Spend Dashboard"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SnlYellow,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${userProfile.businessName.ifBlank { userProfile.fullName }} • Tier: ${userProfile.priceGroupId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Time Window Filter Chips (6M / 12M)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = SnlYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        DistributorTimeRange.entries.forEach { range ->
                            val isSelected = selectedTimeRange == range
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) SnlRed else Color.White.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { selectedTimeRange = range }
                                    .testTag("dashboard_range_chip_${range.monthsCount}")
                            ) {
                                Text(
                                    text = if (isUr) range.labelUr else range.labelEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. KPI Summary Cards Row (Total Spend, Purchase Volume Cartons, Active Orders, Avg Monthly Spend)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DistributorKpiCard(
                        title = if (isUr) "مجموعی خریداری (Spend)" else "Total Spend (${selectedTimeRange.monthsCount}M)",
                        primaryValue = formatPkr(totalPeriodSpendPkr),
                        subtitle = if (isUr) {
                            "ماہانہ اوسط: ${formatPkr(avgMonthlySpendPkr)}"
                        } else {
                            "Avg/Active Mo: ${formatPkr(avgMonthlySpendPkr)}"
                        },
                        trendPercent = spendMoMPercent,
                        icon = Icons.Default.Payments,
                        accentColor = SnlNavy,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("kpi_total_spend_card")
                    )

                    DistributorKpiCard(
                        title = if (isUr) "کل والیوم (کارٹن)" else "Purchase Volume",
                        primaryValue = "$totalPeriodCartons Ctns",
                        subtitle = if (isUr) {
                            "کل آرڈرز: $totalPeriodOrders"
                        } else {
                            "$totalPeriodOrders Orders Placed"
                        },
                        trendPercent = volumeMoMPercent,
                        icon = Icons.Default.Inventory2,
                        accentColor = SnlRed,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("kpi_total_volume_card")
                    )
                }
            }
        }

        // 3. Interactive Monthly Volume & Spending Visualization Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_trends_chart_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isUr) {
                            "ماہانہ خریداری کا حجم (Volume) اور اخراجات (Spending) کے رجحانات"
                        } else {
                            "Monthly Purchase Volume & Spending Trends"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (isUr) {
                            "کسی بھی مہینے کے بار یا پوائنٹ پر ٹیپ کریں تاکہ تفصیلی کارٹن والیوم اور PKR اخراجات دیکھ سکیں۔"
                        } else {
                            "Tap any month column or data node on the chart to inspect detailed PKR spend and carton volume."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Chart Visualization Mode Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedChartMode == DistributorChartMode.COMPOSED_VOLUME_AND_SPEND,
                            onClick = { selectedChartMode = DistributorChartMode.COMPOSED_VOLUME_AND_SPEND },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.StackedBarChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(if (isUr) "مشترکہ چارٹ (Spend + Volume)" else "Combined (Spend + Volume)")
                            },
                            modifier = Modifier.testTag("chart_mode_composed")
                        )

                        FilterChip(
                            selected = selectedChartMode == DistributorChartMode.SPENDING_AREA_TREND,
                            onClick = { selectedChartMode = DistributorChartMode.SPENDING_AREA_TREND },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ShowChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(if (isUr) "اخراجات کا رجحان (PKR)" else "Spending Trend (PKR)")
                            },
                            modifier = Modifier.testTag("chart_mode_spending_area")
                        )

                        FilterChip(
                            selected = selectedChartMode == DistributorChartMode.PURCHASE_VOLUME_BARS,
                            onClick = { selectedChartMode = DistributorChartMode.PURCHASE_VOLUME_BARS },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.BarChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(if (isUr) "ماہانہ کارٹن والیوم" else "Purchase Volume (Cartons)")
                            },
                            modifier = Modifier.testTag("chart_mode_volume_bars")
                        )
                    }

                    // Legend Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedChartMode != DistributorChartMode.SPENDING_AREA_TREND) {
                            ChartLegendItem(
                                color = SnlNavy,
                                label = if (isUr) "کارٹن والیوم (Cartons)" else "Purchase Volume (Cartons)"
                            )
                        }
                        if (selectedChartMode != DistributorChartMode.PURCHASE_VOLUME_BARS) {
                            ChartLegendItem(
                                color = SnlRed,
                                label = if (isUr) "ماہانہ اخراجات (PKR Spend)" else "Monthly Spend (PKR)"
                            )
                        }
                    }

                    // Interactive Compose Canvas Chart
                    InteractiveMonthlyDistributorChart(
                        metrics = monthlyMetrics,
                        chartMode = selectedChartMode,
                        selectedIndex = safeSelectedIndex,
                        onSelectMonth = { idx -> selectedMonthIndex = idx },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .testTag("distributor_interactive_canvas_chart")
                    )

                    // Month Selector Strip for quick accessibility & TalkBack support
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        monthlyMetrics.forEachIndexed { idx, item ->
                            val isSelected = idx == safeSelectedIndex
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) {
                                    SnlNavy
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedMonthIndex = idx }
                                    .testTag("month_selector_chip_${item.yearMonthKey}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = item.shortMonthLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) SnlYellow else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${item.totalCartonsVolume} ctn",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Selected Month Tooltip / Inspection Card
                    if (selectedMonthMetric != null) {
                        SelectedMonthTooltipCard(
                            metric = selectedMonthMetric,
                            isUr = isUr
                        )
                    }
                }
            }
        }

        // 4. Top Purchased Products & Viscosity Grade Mix Breakdown
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("top_products_breakdown_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isUr) {
                            "سب سے زیادہ خریدی گئی مصنوعات اور گریڈز (Product Volume Mix)"
                        } else {
                            "Top Purchased Products & Volume Mix"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (productBreakdowns.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCartCheckout,
                                    contentDescription = null,
                                    tint = SnlNavy,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = if (isUr) {
                                        "ابھی تک کوئی آرڈر ریکارڈ نہیں ہوا۔ کیٹلاگ سے آرڈر بک کریں تاکہ ماہانہ خریداری کے گراف اپ ڈیٹ ہوں۔"
                                    } else {
                                        "No orders recorded yet for this account. Place an order from the catalogue to populate product volume mix and monthly trends."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onNavigateToCatalogue,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SnlRed,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("dashboard_empty_browse_catalogue_button")
                                ) {
                                    Text(
                                        text = if (isUr) "کیٹلاگ کھولیں" else "Browse Product Catalogue",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        productBreakdowns.forEach { prod ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prod.productName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "SKU: ${prod.sku} • ${prod.grade.ifBlank { "Standard" }} (${prod.packagingSize.ifBlank { "Carton" }})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatPkr(prod.totalSpendPkr),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = SnlNavy
                                        )
                                        Text(
                                            text = "${prod.totalCartons} Cartons (${(prod.shareOfSpend * 100).roundToInt()}%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SnlRed,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { prod.shareOfSpend.coerceIn(0.04f, 1f) },
                                    color = SnlNavy,
                                    trackColor = SnlNavy.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Monthly Ledger Table for Audit & Export Reference
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_ledger_table_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isUr) "ماہانہ خریداری کا خلاصہ (Monthly Statement)" else "Monthly Volume & Spend Statement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isUr) "تمام آرڈرز دیکھیں" else "View Orders",
                            style = MaterialTheme.typography.labelLarge,
                            color = SnlRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onNavigateToOrders() }
                                .padding(4.dp)
                                .testTag("dashboard_view_all_orders_link")
                        )
                    }

                    HorizontalDivider()

                    monthlyMetrics.reversed().forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = m.fullMonthLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${m.orderCount} Orders • ${m.totalCartonsVolume} Cartons",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatPkr(m.totalSpendPkr),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (m.totalSpendPkr > 0) SnlNavy else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (m.deliveredSpendPkr > 0) {
                                    Text(
                                        text = "Fulfilled: ${formatPkr(m.deliveredSpendPkr)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SnlApproveGreen
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributorKpiCard(
    title: String,
    primaryValue: String,
    subtitle: String,
    trendPercent: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val isPositive = trendPercent >= 0.0
    val formattedTrend = String.format(Locale.US, "%+.1f%% MoM", trendPercent)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isPositive) {
                        SnlApproveGreen.copy(alpha = 0.14f)
                    } else {
                        SnlRed.copy(alpha = 0.14f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPositive) {
                                Icons.AutoMirrored.Filled.TrendingUp
                            } else {
                                Icons.AutoMirrored.Filled.TrendingDown
                            },
                            contentDescription = null,
                            tint = if (isPositive) SnlApproveGreen else SnlRed,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = formattedTrend,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPositive) SnlApproveGreen else SnlRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChartLegendItem(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Interactive Compose Canvas Chart rendering Monthly Purchase Volume (Bars) and/or
 * Monthly Spending Trends (Smooth Cubic Bezier Line + Gradient Area) with touch-to-select support.
 */
@Composable
private fun InteractiveMonthlyDistributorChart(
    metrics: List<MonthlyDistributorMetric>,
    chartMode: DistributorChartMode,
    selectedIndex: Int,
    onSelectMonth: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "chartAnimation"
    )

    val maxSpend = remember(metrics) {
        metrics.maxOfOrNull { it.totalSpendPkr }?.takeIf { it > 0.0 } ?: 100_000.0
    }
    val maxVolume = remember(metrics) {
        metrics.maxOfOrNull { it.totalCartonsVolume }?.takeIf { it > 0L }?.toDouble() ?: 50.0
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val selectedHighlightColor = SnlYellow.copy(alpha = 0.22f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(metrics.size) {
                    detectTapGestures { tapOffset ->
                        if (metrics.isNotEmpty() && size.width > 0) {
                            val slotWidth = size.width.toFloat() / metrics.size
                            val tappedIndex = (tapOffset.x / slotWidth).toInt().coerceIn(0, metrics.size - 1)
                            onSelectMonth(tappedIndex)
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val topPadding = 18.dp.toPx()
            val bottomPadding = 16.dp.toPx()
            val usableHeight = (canvasHeight - topPadding - bottomPadding).coerceAtLeast(1f)
            val count = metrics.size.coerceAtLeast(1)
            val slotWidth = canvasWidth / count

            // 1. Horizontal reference gridlines (4 levels)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            for (i in 0..3) {
                val y = topPadding + (usableHeight * i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (i < 3) dashEffect else null
                )
            }

            // 2. Highlight band for the currently selected month
            if (selectedIndex in metrics.indices) {
                val bandLeft = selectedIndex * slotWidth
                drawRoundRect(
                    color = selectedHighlightColor,
                    topLeft = Offset(bandLeft + 4.dp.toPx(), 0f),
                    size = Size((slotWidth - 8.dp.toPx()).coerceAtLeast(4f), canvasHeight),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )
            }

            // 3. Draw Purchase Volume Bars when in COMPOSED or PURCHASE_VOLUME_BARS mode
            if (chartMode == DistributorChartMode.COMPOSED_VOLUME_AND_SPEND ||
                chartMode == DistributorChartMode.PURCHASE_VOLUME_BARS
            ) {
                val barWidth = (slotWidth * 0.46f).coerceIn(10.dp.toPx(), 34.dp.toPx())
                metrics.forEachIndexed { idx, m ->
                    val normalizedVol = (m.totalCartonsVolume.toDouble() / maxVolume).toFloat().coerceIn(0f, 1f)
                    val barHeight = max(normalizedVol * usableHeight * animProgress, 4.dp.toPx())
                    val centerX = (idx + 0.5f) * slotWidth
                    val topY = topPadding + usableHeight - barHeight
                    val isSelected = idx == selectedIndex

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = if (isSelected) {
                                listOf(SnlNavy, SnlNavy.copy(alpha = 0.85f))
                            } else {
                                listOf(SnlNavy.copy(alpha = 0.78f), SnlNavy.copy(alpha = 0.45f))
                            },
                            startY = topY,
                            endY = topPadding + usableHeight
                        ),
                        topLeft = Offset(centerX - barWidth / 2f, topY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }

            // 4. Draw Spending Trend Smooth Bezier Area + Line when in COMPOSED or SPENDING_AREA_TREND mode
            if (chartMode == DistributorChartMode.COMPOSED_VOLUME_AND_SPEND ||
                chartMode == DistributorChartMode.SPENDING_AREA_TREND
            ) {
                val points = metrics.mapIndexed { idx, m ->
                    val normalizedSpend = (m.totalSpendPkr / maxSpend).toFloat().coerceIn(0f, 1f)
                    val x = (idx + 0.5f) * slotWidth
                    val y = topPadding + usableHeight - (normalizedSpend * usableHeight * animProgress)
                    Offset(x, y)
                }

                if (points.size >= 2) {
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val controlX1 = (p0.x + p1.x) / 2f
                            cubicTo(controlX1, p0.y, controlX1, p1.y, p1.x, p1.y)
                        }
                    }

                    val areaPath = Path().apply {
                        addPath(linePath)
                        lineTo(points.last().x, topPadding + usableHeight)
                        lineTo(points.first().x, topPadding + usableHeight)
                        close()
                    }

                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SnlRed.copy(alpha = if (chartMode == DistributorChartMode.SPENDING_AREA_TREND) 0.36f else 0.20f),
                                SnlRed.copy(alpha = 0.02f)
                            ),
                            startY = topPadding,
                            endY = topPadding + usableHeight
                        )
                    )

                    drawPath(
                        path = linePath,
                        color = SnlRed,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Draw data nodes along the Spending curve
                points.forEachIndexed { idx, pt ->
                    val isSelected = idx == selectedIndex
                    drawCircle(
                        color = Color.White,
                        radius = if (isSelected) 7.dp.toPx() else 5.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = if (isSelected) SnlYellow else SnlRed,
                        radius = if (isSelected) 5.dp.toPx() else 3.5.dp.toPx(),
                        center = pt
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedMonthTooltipCard(
    metric: MonthlyDistributorMetric,
    isUr: Boolean
) {
    val avgOrderValue = if (metric.orderCount > 0) metric.totalSpendPkr / metric.orderCount else 0.0
    val avgCartonCost = if (metric.totalCartonsVolume > 0L) {
        metric.totalSpendPkr / metric.totalCartonsVolume
    } else 0.0

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SnlNavy.copy(alpha = 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selected_month_detail_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUr) {
                        "منتخب مہینہ: ${metric.fullMonthLabel}"
                    } else {
                        "Selected Month: ${metric.fullMonthLabel}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = SnlNavy
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SnlRed.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "${metric.orderCount} Orders",
                        style = MaterialTheme.typography.labelSmall,
                        color = SnlRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isUr) "ماہانہ اخراجات (Spend)" else "Monthly Spend",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPkr(metric.totalSpendPkr),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = SnlRed
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isUr) "خریداری کا حجم (Volume)" else "Purchase Volume",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${metric.totalCartonsVolume} Cartons",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = SnlNavy
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isUr) "اوسط فی کارٹن/آرڈر" else "Avg / Carton",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (avgCartonCost > 0) formatPkr(avgCartonCost) else formatPkr(avgOrderValue),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (metric.topProductLabel.isNotBlank()) {
                Text(
                    text = if (isUr) {
                        "سب سے زیادہ آرڈر کردہ آئٹم: ${metric.topProductLabel}"
                    } else {
                        "Top Ordered Item: ${metric.topProductLabel}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
