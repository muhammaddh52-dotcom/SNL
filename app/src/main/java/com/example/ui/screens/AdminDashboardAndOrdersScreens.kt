package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CompanySettings
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.model.UiLanguage
import com.example.data.model.UserProfile
import com.example.ui.theme.SnlApproveGreen
import com.example.ui.theme.SnlNavy
import com.example.ui.theme.SnlRed
import com.example.ui.theme.SnlRejectRed
import com.example.ui.theme.SnlWarningAmber
import com.example.ui.theme.SnlYellow
import com.example.util.ExcelAndSheetsProcessor
import com.example.util.InvoiceAndReportPdfGenerator
import java.text.NumberFormat
import java.util.Locale

private fun isOrderPendingApproval(status: String): Boolean =
    status == "PENDING_ADMIN_APPROVAL" || status == "PENDING_APPROVAL"

@Composable
fun AdminKpiDashboardScreen(
    language: UiLanguage,
    products: List<Product>,
    allOrders: List<Order>,
    allUsers: List<UserProfile>,
    companySettings: CompanySettings,
    onNavigateSection: (String) -> Unit
) {
    val context = LocalContext.current
    val isUr = language == UiLanguage.URDU
    val pkrFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    val apkMetadata = remember { com.example.util.ApkDistributionHelper.getInstalledApkMetadata(context) }

    val pendingOrders = remember(allOrders) { allOrders.filter { isOrderPendingApproval(it.orderStatus) } }
    val confirmedOrders = remember(allOrders) {
        allOrders.filter {
            it.orderStatus in setOf("CONFIRMED", "PROCESSING", "READY_FOR_DISPATCH", "DISPATCHED", "DELIVERED")
        }
    }
    val rejectedOrders = remember(allOrders) { allOrders.filter { it.orderStatus == "REJECTED" } }
    val confirmedSalesValuePkr = remember(confirmedOrders) { confirmedOrders.sumOf { it.grandTotal } }

    val dealersCount = remember(allUsers) { allUsers.count { it.role == "DEALER" } }
    val distributorsCount = remember(allUsers) { allUsers.count { it.role == "DISTRIBUTOR" } }
    val staffCount = remember(allUsers) { allUsers.count { it.role == "STAFF" } }
    val pendingUsers = remember(allUsers) { allUsers.filter { it.accountStatus == "PENDING_APPROVAL" } }
    val lowStockProducts = remember(products, companySettings.lowStockThreshold) {
        products.filter { it.availableStock <= companySettings.lowStockThreshold }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SnlNavy),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUr) "ایس این ایل لبریکنٹس — سپر ایڈمن کمانڈ سینٹر" else "SNL LUBRICANTS — SUPER ADMIN COMMAND CENTER",
                                style = MaterialTheme.typography.titleMedium,
                                color = SnlYellow,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isUr) {
                                    "سیفی نیشنل لبریکنٹس، کراچی پاکستان • تصدیق شدہ فروخت (PKR)"
                                } else {
                                    "SAIFI NATIONAL LUBRICANTS, KARACHI PAKISTAN • Confirmed B2B Revenue"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Surface(
                            color = SnlRed,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "PKR ${pkrFormat.format(confirmedSalesValuePkr.toLong())}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickCommandButton(
                            label = if (isUr) "زیر التواء آرڈرز (${pendingOrders.size})" else "Pending Orders (${pendingOrders.size})",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            containerColor = if (pendingOrders.isNotEmpty()) SnlApproveGreen else Color.White.copy(alpha = 0.16f),
                            onClick = { onNavigateSection("ADMIN_ORDERS") },
                            tag = "quick_nav_orders"
                        )
                        QuickCommandButton(
                            label = if (isUr) "اے آئی پرائس لسٹ ریڈر" else "AI Price List OCR",
                            icon = Icons.Default.DocumentScanner,
                            containerColor = Color.White.copy(alpha = 0.16f),
                            onClick = { onNavigateSection("ADMIN_AI_OCR") },
                            tag = "quick_nav_ai_ocr"
                        )
                        QuickCommandButton(
                            label = if (isUr) "ایکسل / شیٹس سنک" else "Excel & Sheets Sync",
                            icon = Icons.Default.TableView,
                            containerColor = Color.White.copy(alpha = 0.16f),
                            onClick = { onNavigateSection("ADMIN_EXCEL_SYNC") },
                            tag = "quick_nav_excel"
                        )
                        QuickCommandButton(
                            label = if (isUr) "ڈیلرز اور رجسٹریشن (${pendingUsers.size})" else "Accounts (${pendingUsers.size})",
                            icon = Icons.Default.People,
                            containerColor = Color.White.copy(alpha = 0.16f),
                            onClick = { onNavigateSection("ADMIN_USERS") },
                            tag = "quick_nav_users"
                        )
                        QuickCommandButton(
                            label = if (isUr) "سائن شدہ APK شیئر کریں" else "Share Signed APK",
                            icon = Icons.Default.Share,
                            containerColor = SnlRed,
                            onClick = {
                                com.example.util.ApkDistributionHelper.shareSignedApkForSideLoading(context)
                            },
                            tag = "quick_share_signed_apk"
                        )
                    }
                }
            }
        }

        // Signed APK Distribution Card for Dealers & Distributors Side-Loading
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SnlApproveGreen.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isUr) {
                            "ڈیلرز اور ڈسٹری بیوٹرز کے لیے سائن شدہ APK انسٹالر (Side-Load Distribution)"
                        } else {
                            "Signed APK Distribution for Dealers & Distributors (Side-Load Ready)"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = SnlNavy
                    )
                    Text(
                        text = "Package: ${apkMetadata.applicationId} • v${apkMetadata.versionName} (${apkMetadata.apkSizeMb}) • Signed SHA-1: ${apkMetadata.sha1Fingerprint.take(23)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                com.example.util.ApkDistributionHelper.shareSignedApkForSideLoading(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SnlApproveGreen, contentColor = Color.White),
                            modifier = Modifier.testTag("dashboard_share_signed_apk_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isUr) "SNL_Lubricants.apk شیئر / سیو کریں" else "Export & Share Signed .APK File",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                InvoiceAndReportPdfGenerator.openWhatsAppClickToChat(
                                    context = context,
                                    phoneNumber = companySettings.companyWhatsappNumber.ifBlank { "+923101005253" },
                                    message = com.example.util.ApkDistributionHelper.buildSideLoadInstructionsMessage()
                                )
                            },
                            modifier = Modifier.testTag("dashboard_whatsapp_apk_guide_button")
                        ) {
                            Text(if (isUr) "واٹس ایپ انسٹالیشن گائیڈ" else "WhatsApp Install Guide")
                        }
                    }
                }
            }
        }

        if (pendingOrders.isNotEmpty() || pendingUsers.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SnlWarningAmber.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pendingOrders.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateSection("ADMIN_ORDERS") },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = SnlRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isUr) {
                                            "${pendingOrders.size} نئے آرڈرز منظوری کے منتظر ہیں (ACCEPT / REJECT)"
                                        } else {
                                            "${pendingOrders.size} Incoming Order(s) awaiting Admin ACCEPT / REJECT"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(onClick = { onNavigateSection("ADMIN_ORDERS") }) {
                                    Text(if (isUr) "جائزہ لیں" else "Review Now")
                                }
                            }
                        }
                        if (pendingUsers.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateSection("ADMIN_USERS") },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.People, contentDescription = null, tint = SnlNavy)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isUr) {
                                            "${pendingUsers.size} نئے ڈیلر/ڈسٹری بیوٹر اکاؤنٹس منظوری کے منتظر ہیں"
                                        } else {
                                            "${pendingUsers.size} New Dealer/Distributor Registration(s) pending approval"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(onClick = { onNavigateSection("ADMIN_USERS") }) {
                                    Text(if (isUr) "منظور کریں" else "Approve")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = if (isUr) "آرڈرز اور آپریشنل خلاصہ" else "Orders & Operations Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricCard(
                    title = if (isUr) "زیر التواء آرڈرز" else "Pending Orders",
                    value = pendingOrders.size.toString(),
                    subtitle = if (isUr) "منظوری درکار ہے" else "Awaiting Approval",
                    accentColor = SnlWarningAmber,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateSection("ADMIN_ORDERS") }
                )
                KpiMetricCard(
                    title = if (isUr) "تصدیق شدہ آرڈرز" else "Confirmed Orders",
                    value = confirmedOrders.size.toString(),
                    subtitle = "PKR ${pkrFormat.format(confirmedSalesValuePkr.toLong())}",
                    accentColor = SnlApproveGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateSection("ADMIN_ORDERS") }
                )
                KpiMetricCard(
                    title = if (isUr) "مسترد شدہ آرکائیو" else "Rejected Archive",
                    value = rejectedOrders.size.toString(),
                    subtitle = if (isUr) "کسٹمر سے پوشیدہ" else "Hidden from Customer",
                    accentColor = SnlRejectRed,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateSection("ADMIN_ORDERS") }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricCard(
                    title = if (isUr) "کل مصنوعات" else "Total Products",
                    value = products.size.toString(),
                    subtitle = if (isUr) "ایس این ایل کیٹلاگ" else "Active SKUs",
                    accentColor = SnlNavy,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateSection("ADMIN_PRODUCTS") }
                )
                KpiMetricCard(
                    title = if (isUr) "ڈسٹری بیوٹرز / ڈیلرز" else "Distributors / Dealers",
                    value = "$distributorsCount / $dealersCount",
                    subtitle = if (isUr) "اسٹاف: $staffCount" else "Staff: $staffCount",
                    accentColor = SnlRed,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateSection("ADMIN_USERS") }
                )
                KpiMetricCard(
                    title = if (isUr) "کم اسٹاک الرٹس" else "Low Stock Alerts",
                    value = lowStockProducts.size.toString(),
                    subtitle = if (isUr) "اسٹاک چیک کریں" else "Needs Restock",
                    accentColor = if (lowStockProducts.isNotEmpty()) SnlRejectRed else SnlApproveGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateSection("ADMIN_INVENTORY") }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUr) "حالیہ آرڈرز" else "Recent Incoming Orders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onNavigateSection("ADMIN_ORDERS") }) {
                    Text(if (isUr) "تمام آرڈرز دیکھیں" else "View All Orders")
                }
            }
        }

        val recentOrders = allOrders.take(5)
        if (recentOrders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = if (isUr) "ابھی تک کوئی آرڈر موصول نہیں ہوا۔" else "No orders received yet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(recentOrders, key = { it.id }) { order ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateSection("ADMIN_ORDERS") },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${order.orderNumber} • ${order.businessName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${order.city} • ${order.priceGroupId} • ${order.itemsCount} SKUs (${order.totalCartons} cartons)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "PKR ${pkrFormat.format(order.grandTotal.toLong())}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = SnlRed
                            )
                            OrderStatusChip(orderStatus = order.orderStatus)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCommandButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag(tag)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .background(accentColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun OrderStatusChip(orderStatus: String) {
    val (bg, fg) = when {
        isOrderPendingApproval(orderStatus) -> SnlWarningAmber.copy(alpha = 0.2f) to Color(0xFFB45309)
        orderStatus == "CONFIRMED" || orderStatus == "DELIVERED" -> SnlApproveGreen.copy(alpha = 0.18f) to SnlApproveGreen
        orderStatus == "REJECTED" -> SnlRejectRed.copy(alpha = 0.18f) to SnlRejectRed
        else -> SnlNavy.copy(alpha = 0.15f) to SnlNavy
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = orderStatus,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AdminOrdersManagementScreen(
    language: UiLanguage,
    allOrders: List<Order>,
    onAcceptOrder: (Order) -> Unit,
    onRejectOrder: (Order, String) -> Unit,
    onUpdateOrderStatus: (Order, String, String) -> Unit
) {
    val context = LocalContext.current
    val isUr = language == UiLanguage.URDU
    val pkrFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    var activeTab by rememberSaveable { mutableStateOf("PENDING") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var orderPendingRejection by remember { mutableStateOf<Order?>(null) }
    var rejectionReasonInput by rememberSaveable { mutableStateOf("") }

    val pendingCount = remember(allOrders) { allOrders.count { isOrderPendingApproval(it.orderStatus) } }
    val activeCount = remember(allOrders) {
        allOrders.count {
            it.orderStatus in setOf("CONFIRMED", "PROCESSING", "READY_FOR_DISPATCH", "DISPATCHED", "DELIVERED")
        }
    }
    val rejectedArchiveCount = remember(allOrders) { allOrders.count { it.orderStatus == "REJECTED" } }

    val filteredOrders = remember(allOrders, activeTab, searchQuery) {
        allOrders.filter { order ->
            val tabMatch = when (activeTab) {
                "PENDING" -> isOrderPendingApproval(order.orderStatus)
                "CONFIRMED" -> order.orderStatus in setOf(
                    "CONFIRMED",
                    "PROCESSING",
                    "READY_FOR_DISPATCH",
                    "DISPATCHED",
                    "DELIVERED"
                )
                "REJECTED_ARCHIVE" -> order.orderStatus == "REJECTED"
                else -> true
            }
            val queryMatch = searchQuery.isBlank() ||
                order.orderNumber.contains(searchQuery, ignoreCase = true) ||
                order.businessName.contains(searchQuery, ignoreCase = true) ||
                order.customerName.contains(searchQuery, ignoreCase = true) ||
                order.city.contains(searchQuery, ignoreCase = true) ||
                order.phone.contains(searchQuery, ignoreCase = true)
            tabMatch && queryMatch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (isUr) "آرڈر منظوری، ڈسپیچ اور مسترد شدہ آرکائیو" else "Order Approval, Dispatch & Rejected Archive",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeTab == "PENDING",
                onClick = { activeTab = "PENDING" },
                label = {
                    Text(
                        if (isUr) "زیر التواء منظوری ($pendingCount)" else "Pending Approval ($pendingCount)"
                    )
                },
                modifier = Modifier.testTag("tab_pending_orders")
            )
            FilterChip(
                selected = activeTab == "CONFIRMED",
                onClick = { activeTab = "CONFIRMED" },
                label = {
                    Text(
                        if (isUr) "تصدیق شدہ اور ڈسپیچ ($activeCount)" else "Confirmed & Dispatch ($activeCount)"
                    )
                },
                modifier = Modifier.testTag("tab_confirmed_orders")
            )
            FilterChip(
                selected = activeTab == "REJECTED_ARCHIVE",
                onClick = { activeTab = "REJECTED_ARCHIVE" },
                leadingIcon = {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text(
                        if (isUr) "مسترد شدہ آرکائیو ($rejectedArchiveCount)" else "Rejected Archive ($rejectedArchiveCount)"
                    )
                },
                modifier = Modifier.testTag("tab_rejected_archive")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = {
                Text(
                    if (isUr) "آرڈر نمبر، کاروبار کا نام، شہر یا فون تلاش کریں" else "Search Order #, Business Name, City or Phone"
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_order_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredOrders.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Text(
                    text = when (activeTab) {
                        "PENDING" -> if (isUr) "کوئی زیر التواء آرڈر موجود نہیں ہے۔" else "No incoming orders awaiting approval."
                        "REJECTED_ARCHIVE" -> if (isUr) "مسترد شدہ آرڈرز کے آرکائیو میں کوئی ریکارڈ نہیں ہے۔" else "No rejected orders in Admin Archive."
                        else -> if (isUr) "کوئی تصدیق شدہ آرڈر موجود نہیں ہے۔" else "No confirmed orders match your filter."
                    },
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredOrders, key = { it.id }) { order ->
                    AdminOrderCard(
                        order = order,
                        isUr = isUr,
                        pkrFormat = pkrFormat,
                        onAcceptClick = { onAcceptOrder(order) },
                        onRejectClick = {
                            rejectionReasonInput = ""
                            orderPendingRejection = order
                        },
                        onStatusChange = { nextStatus, nextPayStatus ->
                            onUpdateOrderStatus(order, nextStatus, nextPayStatus)
                        },
                        onGenerateInvoicePdf = {
                            InvoiceAndReportPdfGenerator.generateOrderInvoicePdf(context, order).onSuccess { pdfFile ->
                                ExcelAndSheetsProcessor.shareFile(
                                    context,
                                    pdfFile,
                                    "application/pdf",
                                    "Share SNL Invoice ${order.orderNumber}"
                                )
                            }
                        },
                        onSendWhatsAppCustomer = {
                            val msg = InvoiceAndReportPdfGenerator.buildWhatsAppOrderMessage(
                                order = order,
                                eventHeader = "SNL ORDER UPDATE (${order.orderStatus})"
                            )
                            InvoiceAndReportPdfGenerator.openWhatsAppClickToChat(
                                context = context,
                                phoneNumber = order.phone,
                                message = msg
                            )
                        }
                    )
                }
            }
        }
    }

    orderPendingRejection?.let { targetOrder ->
        AlertDialog(
            onDismissRequest = { orderPendingRejection = null },
            title = {
                Text(
                    text = if (isUr) "آرڈر مسترد کرنے کی تصدیق کریں" else "Confirm Reject Order: ${targetOrder.orderNumber}",
                    color = SnlRejectRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isUr) {
                            "کیا آپ واقعی '${targetOrder.businessName}' کا آرڈر مسترد کرنا چاہتے ہیں؟ یہ آرڈر کسٹمر کی ایپ سے ہٹا دیا جائے گا (visibleToCustomer = false) اور صرف ایڈمن مسترد شدہ آرکائیو میں محفوظ رہے گا۔"
                        } else {
                            "Rejecting this order will mark it as REJECTED, set visibleToCustomer = false so it immediately disappears from all customer screens, release any held stock, and move it permanently to the Admin Rejected Orders Archive."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = rejectionReasonInput,
                        onValueChange = { rejectionReasonInput = it },
                        label = {
                            Text(if (isUr) "مسترد کرنے کی وجہ درج کریں" else "Rejection Reason (e.g., Out of stock / Credit limit)")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reject_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reason = rejectionReasonInput.ifBlank { "Rejected by Administrator" }
                        onRejectOrder(targetOrder, reason)
                        orderPendingRejection = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnlRejectRed, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_reject_order_button")
                ) {
                    Text(if (isUr) "مسترد کریں (REJECT ORDER)" else "CONFIRM REJECT ORDER")
                }
            },
            dismissButton = {
                TextButton(onClick = { orderPendingRejection = null }) {
                    Text(if (isUr) "منسوخ کریں" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminOrderCard(
    order: Order,
    isUr: Boolean,
    pkrFormat: NumberFormat,
    onAcceptClick: () -> Unit,
    onRejectClick: () -> Unit,
    onStatusChange: (String, String) -> Unit,
    onGenerateInvoicePdf: () -> Unit,
    onSendWhatsAppCustomer: () -> Unit
) {
    val lines = remember(order.priceSnapshotText) { order.parsedItems() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_order_card_${order.id}"),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.orderNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = SnlNavy
                    )
                    Text(
                        text = "${order.businessName} (${order.customerName})",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Phone: ${order.phone} • City: ${order.city}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Address: ${order.deliveryAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Price Group: ${order.priceGroupId} • Payment: ${order.paymentMethod} (${order.paymentStatus})",
                        style = MaterialTheme.typography.labelMedium,
                        color = SnlRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    OrderStatusChip(orderStatus = order.orderStatus)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "PKR ${pkrFormat.format(order.grandTotal.toLong())}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = SnlRed
                    )
                }
            }

            if (order.orderInstructions.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Notes: ${order.orderInstructions}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SnlNavy
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isUr) "آرڈر آئٹمز اور محفوظ شدہ قیمت کا سنیپ شاٹ:" else "Immutable Order Price Snapshot (${lines.size} SKUs):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            lines.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.sku} • ${item.productName} (${item.grade} | ${item.packagingSize})",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${item.quantity} x PKR ${pkrFormat.format(item.unitPricePkr.toLong())} = PKR ${pkrFormat.format(item.lineTotalPkr.toLong())}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isOrderPendingApproval(order.orderStatus) -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // GREEN BUTTON: ACCEPT ORDER
                        Button(
                            onClick = onAcceptClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SnlApproveGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("accept_order_${order.id}")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isUr) "آرڈر منظور کریں (ACCEPT)" else "ACCEPT ORDER",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // RED BUTTON: REJECT ORDER
                        Button(
                            onClick = onRejectClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SnlRejectRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("reject_order_${order.id}")
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isUr) "مسترد کریں (REJECT)" else "REJECT ORDER",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                order.orderStatus == "REJECTED" -> {
                    Surface(
                        color = SnlRejectRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isUr) {
                                    "مسترد شدہ آرڈر — کسٹمر کی اسکرین سے ہٹا دیا گیا ہے (visibleToCustomer = ${order.visibleToCustomer})"
                                } else {
                                    "REJECTED ARCHIVE — Hidden from Customer Screens (visibleToCustomer = ${order.visibleToCustomer})"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = SnlRejectRed,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Reason: ${order.rejectionReason.ifBlank { "N/A" }} • Rejected By: ${order.rejectedBy}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (isUr) "ڈسپیچ اسٹیٹس اپ ڈیٹ کریں:" else "Update Dispatch & Delivery Stage:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "CONFIRMED",
                                "PROCESSING",
                                "READY_FOR_DISPATCH",
                                "DISPATCHED",
                                "DELIVERED"
                            ).forEach { stage ->
                                FilterChip(
                                    selected = order.orderStatus == stage,
                                    onClick = { onStatusChange(stage, order.paymentStatus) },
                                    label = { Text(stage) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isUr) "ادائیگی:" else "Payment:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            listOf("UNPAID", "PARTIALLY_PAID", "PAID", "CREDIT").forEach { payState ->
                                AssistChip(
                                    onClick = { onStatusChange(order.orderStatus, payState) },
                                    label = { Text(payState) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onGenerateInvoicePdf,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_pdf_invoice_${order.id}")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isUr) "پی ڈی ایف انوائس" else "PDF Invoice")
                            }
                            Button(
                                onClick = onSendWhatsAppCustomer,
                                colors = ButtonDefaults.buttonColors(containerColor = SnlApproveGreen),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_whatsapp_order_${order.id}")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isUr) "واٹس ایپ الرٹ" else "WhatsApp Customer")
                            }
                        }
                    }
                }
            }
        }
    }
}
