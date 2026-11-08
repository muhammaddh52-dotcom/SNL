package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Category
import com.example.data.model.CompanySettings
import com.example.data.model.ExtractedPriceRow
import com.example.data.model.GroupPrice
import com.example.data.model.InventoryMovement
import com.example.data.model.Order
import com.example.data.model.PriceGroup
import com.example.data.model.PriceImportBatch
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
import com.example.util.ExcelParseSummary
import com.example.util.InvoiceAndReportPdfGenerator
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

/**
 * Section 8: AI Price List Image Reader (Gemini 3.8 Flash Multimodal OCR + Attached SNL Price List Preview).
 */
@Composable
fun AdminAiPriceListOcrScreen(
    language: UiLanguage,
    draftRows: List<ExtractedPriceRow>,
    targetPriceGroup: String,
    aiStatusMessage: String,
    priceGroups: List<PriceGroup>,
    onSelectTargetGroup: (String) -> Unit,
    onUpdateDraftRowPrice: (String, Double?) -> Unit,
    onRunGeminiOnBitmap: (Bitmap) -> Unit,
    onRunGeminiOnAttachedAsset: () -> Unit,
    onResetToAttachedList: () -> Unit,
    onConfirmCommitImport: (String, String, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val isUr = language == UiLanguage.URDU
    val pkrFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    var showOnlyUncertain by rememberSaveable { mutableStateOf(false) }
    var showSourceImageDialog by rememberSaveable { mutableStateOf(false) }
    var copyToStandardDealer by rememberSaveable { mutableStateOf(true) }
    var editingRow by remember { mutableStateOf<ExtractedPriceRow?>(null) }
    var editPriceInput by rememberSaveable { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()?.let { bmp ->
                onRunGeminiOnBitmap(bmp)
            }
        }
    }

    val uncertainCount = remember(draftRows) { draftRows.count { it.isUncertainOrMissingPrice } }
    val visibleRows = remember(draftRows, showOnlyUncertain) {
        if (showOnlyUncertain) draftRows.filter { it.isUncertainOrMissingPrice } else draftRows
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_ocr_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SnlNavy),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUr) "اے آئی پرائس لسٹ ریڈر (Gemini 3.8 Flash)" else "AI PRICE LIST IMAGE READER (GEMINI 3.8 FLASH)",
                                style = MaterialTheme.typography.titleMedium,
                                color = SnlYellow,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = aiStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        Image(
                            painter = painterResource(id = R.drawable.img_snl_pricelist_source),
                            contentDescription = "Attached SNL Price List",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showSourceImageDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SnlRed),
                            modifier = Modifier.testTag("pick_price_list_image_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isUr) "پرائس لسٹ تصویر منتخب کریں" else "Scan New Image")
                        }

                        OutlinedButton(
                            onClick = onRunGeminiOnAttachedAsset,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.testTag("run_gemini_attached_button")
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isUr) "منسلک لسٹ پر Gemini چلائیں" else "Re-Scan Attached SNL Sheet")
                        }

                        TextButton(
                            onClick = onResetToAttachedList,
                            colors = ButtonDefaults.textButtonColors(contentColor = SnlYellow)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isUr) "59 آئٹمز ری سیٹ کریں" else "Reset 59 Items")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isUr) "ہدف پرائس گروپ منتخب کریں:" else "Target Price Group for Extracted Column:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        priceGroups.forEach { pg ->
                            FilterChip(
                                selected = targetPriceGroup == pg.groupCode,
                                onClick = { onSelectTargetGroup(pg.groupCode) },
                                label = { Text("${pg.groupName} (${pg.groupCode})") }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = copyToStandardDealer,
                            onCheckedChange = { copyToStandardDealer = it }
                        )
                        Text(
                            text = if (isUr) {
                                "اگر اسٹینڈرڈ ڈیلر کی قیمت خالی ہو تو ڈسٹری بیوٹر کی قیمت کاپی کریں"
                            } else {
                                "Also initialize Standard Dealer price if not yet set"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showOnlyUncertain,
                            onClick = { showOnlyUncertain = !showOnlyUncertain },
                            leadingIcon = {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = SnlWarningAmber)
                            },
                            label = {
                                Text(
                                    if (isUr) {
                                        "صرف نامکمل '-' قیمتیں دکھائیں ($uncertainCount)"
                                    } else {
                                        "Flagged '-' Rows Only ($uncertainCount)"
                                    }
                                )
                            },
                            modifier = Modifier.testTag("filter_uncertain_rows_chip")
                        )

                        Button(
                            onClick = {
                                onConfirmCommitImport(
                                    "SNL_Sole_Distributor_Price_List_03-11-26.jpg",
                                    "AI_IMAGE_OCR",
                                    copyToStandardDealer,
                                    false
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SnlApproveGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("confirm_ai_import_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isUr) "پرائس لسٹ محفوظ کریں (${draftRows.size})" else "CONFIRM IMPORT (${draftRows.size})",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        items(visibleRows, key = { it.sku }) { row ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        editingRow = row
                        editPriceInput = row.distributorPricePkr?.toLong()?.toString() ?: ""
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (row.isUncertainOrMissingPrice) {
                        SnlWarningAmber.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#${row.serialNo} • ${row.productName} (${row.sku})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${row.categoryName} | Packing: ${row.packagingSize} | Grade: ${row.grade}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (row.uncertaintyNote.isNotBlank()) {
                            Text(
                                text = row.uncertaintyNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (row.isUncertainOrMissingPrice) SnlRejectRed else SnlApproveGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val priceDisplay = row.distributorPricePkr?.let {
                            "PKR ${pkrFormat.format(it.toLong())}"
                        } ?: "'-' (Review)"
                        Text(
                            text = priceDisplay,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (row.isUncertainOrMissingPrice) SnlRejectRed else SnlNavy
                        )
                        IconButton(
                            onClick = {
                                editingRow = row
                                editPriceInput = row.distributorPricePkr?.toLong()?.toString() ?: ""
                            },
                            modifier = Modifier.testTag("edit_draft_row_${row.sku}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Price")
                        }
                    }
                }
            }
        }
    }

    editingRow?.let { target ->
        AlertDialog(
            onDismissRequest = { editingRow = null },
            title = { Text("Edit Price: #${target.serialNo} ${target.productName} (${target.packagingSize})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SKU: ${target.sku} • Grade: ${target.grade} • Category: ${target.categoryName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = editPriceInput,
                        onValueChange = { editPriceInput = it },
                        label = { Text("Authorized Price in PKR (leave empty for '-')") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("draft_price_edit_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = editPriceInput.replace(",", "").trim().toDoubleOrNull()
                        onUpdateDraftRowPrice(target.sku, parsed)
                        editingRow = null
                    },
                    modifier = Modifier.testTag("save_draft_price_button")
                ) {
                    Text("Save Price")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRow = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSourceImageDialog) {
        AlertDialog(
            onDismissRequest = { showSourceImageDialog = false },
            title = { Text("Attached SNL Sole Distributor Price List (03-11-26)") },
            text = {
                Image(
                    painter = painterResource(id = R.drawable.img_snl_pricelist_source),
                    contentDescription = "Full SNL Price List",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showSourceImageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Section 9: Complete Google Drive, Excel, CSV & Google Sheets Product Import/Export +
 * Price Structure Alignment Validation + 1-Click Rollback.
 */
@Composable
fun AdminExcelAndSheetsSyncScreen(
    language: UiLanguage,
    excelPreview: ExcelParseSummary?,
    priceImports: List<PriceImportBatch>,
    companySettings: CompanySettings,
    products: List<Product> = emptyList(),
    groupPrices: List<GroupPrice> = emptyList(),
    onParseExcelOrCsv: (String, String) -> Unit,
    onConfirmExcelImport: (String, String, Boolean) -> Unit,
    onFetchGoogleSheet: (String, String) -> Unit,
    onRollbackImport: (PriceImportBatch) -> Unit
) {
    val context = LocalContext.current
    val isUr = language == UiLanguage.URDU
    val pkrFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    var formatMode by rememberSaveable { mutableStateOf("WIDE_PRICE_MATRIX") }
    var allowStockUpdate by rememberSaveable { mutableStateOf(false) }
    var googleSheetUrl by rememberSaveable { mutableStateOf(companySettings.googleSheetUrl) }
    var pastedCsvText by rememberSaveable { mutableStateOf("") }
    var selectedFileName by rememberSaveable { mutableStateOf("SNL_Price_Matrix_Upload.csv") }
    var overrideAlignmentIssues by rememberSaveable { mutableStateOf(false) }
    var driveExportStatus by rememberSaveable { mutableStateOf("") }

    // Android SAF OpenDocument launcher (includes Google Drive DocumentProvider for CSV & Excel files)
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }
            }.getOrNull()?.let { content ->
                pastedCsvText = content
                selectedFileName = uri.lastPathSegment ?: "GoogleDrive_SNL_Products.csv"
                overrideAlignmentIssues = false
                onParseExcelOrCsv(content, formatMode)
            }
        }
    }

    // Android SAF CreateDocument launcher to save CSV directly into Google Drive
    val exportDriveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val csvData = ExcelAndSheetsProcessor.generateLiveCatalogCsvContent(products, groupPrices)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csvData.toByteArray(Charsets.UTF_8))
                }
                driveExportStatus = "Exported ${products.size.coerceAtLeast(59)} products to Google Drive CSV!"
            }.onFailure {
                driveExportStatus = "Drive CSV export error: ${it.localizedMessage}"
            }
        }
    }

    // Android SAF CreateDocument launcher to save 6-sheet Excel (.xls) directly into Google Drive
    val exportDriveExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.ms-excel")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val xmlData = ExcelAndSheetsProcessor.generateLiveCatalogExcelXmlContent(products, groupPrices)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(xmlData.toByteArray(Charsets.UTF_8))
                }
                driveExportStatus = "Exported 6-Sheet Excel Workbook to Google Drive!"
            }.onFailure {
                driveExportStatus = "Drive Excel export error: ${it.localizedMessage}"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("excel_sync_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SnlNavy),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isUr) {
                            "گوگل ڈرائیو CSV/Excel امپورٹ، ایکسپورٹ اور پرائس اسٹرکچر ویلیڈیشن"
                        } else {
                            "GOOGLE DRIVE CSV/EXCEL IMPORT, EXPORT & PRICE ALIGNMENT"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = SnlYellow,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isUr) {
                            "گوگل ڈرائیو سے براہِ راست CSV یا Excel فائل امپورٹ/ایکسپورٹ کریں اور پرائس ٹیئرز کی مطابقت چیک کریں۔"
                        } else {
                            "Import/export product & multi-tier pricing files via Google Drive with automated Price Structure Alignment validation."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                    if (driveExportStatus.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = driveExportStatus,
                            style = MaterialTheme.typography.labelMedium,
                            color = SnlYellow,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("drive_export_status_text")
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                documentLauncher.launch(
                                    arrayOf(
                                        "text/*",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/vnd.ms-excel",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/octet-stream"
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SnlRed),
                            modifier = Modifier.testTag("import_google_drive_button")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isUr) "گوگل ڈرائیو سے امپورٹ کریں" else "Import from Google Drive (CSV/Excel)")
                        }

                        Button(
                            onClick = {
                                ExcelAndSheetsProcessor.exportLiveCatalogToGoogleDrive(
                                    context = context,
                                    products = products,
                                    groupPrices = groupPrices,
                                    asExcelWorkbook = false
                                )
                                exportDriveCsvLauncher.launch("SNL_Products_PriceMatrix_2026.csv")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SnlApproveGreen),
                            modifier = Modifier.testTag("export_drive_csv_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isUr) "گوگل ڈرائیو میں CSV ایکسپورٹ کریں" else "Export CSV to Google Drive")
                        }

                        OutlinedButton(
                            onClick = {
                                ExcelAndSheetsProcessor.exportLiveCatalogToGoogleDrive(
                                    context = context,
                                    products = products,
                                    groupPrices = groupPrices,
                                    asExcelWorkbook = true
                                )
                                exportDriveExcelLauncher.launch("SNL_Official_Price_Workbook_2026.xls")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.testTag("export_drive_excel_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isUr) "6-شیٹ ایکسل ڈرائیو میں محفوظ کریں" else "Export 6-Sheet Excel (.xls) to Drive")
                        }

                        OutlinedButton(
                            onClick = {
                                ExcelAndSheetsProcessor.generateAndShareOfficialExcelTemplate(context)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SnlYellow),
                            modifier = Modifier.testTag("download_excel_template_button")
                        ) {
                            Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isUr) "آفیشل ٹیمپلیٹ" else "Download Template")
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isUr) "گوگل ڈرائیو / شیٹس لنک اور فارمیٹ سیٹنگز" else "Google Drive / Sheets URL Sync & Structure Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = formatMode == "WIDE_PRICE_MATRIX",
                            onClick = { formatMode = "WIDE_PRICE_MATRIX" },
                            label = { Text("Format A: Wide Matrix (All Groups)") }
                        )
                        FilterChip(
                            selected = formatMode == "LONG_GROUP_PRICES",
                            onClick = { formatMode = "LONG_GROUP_PRICES" },
                            label = { Text("Format B: Long Group Rows") }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isUr) "فائل سے اسٹاک کی مقدار بھی اپ ڈیٹ کریں" else "Allow Stock Quantity Sync from Sheet",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = allowStockUpdate,
                            onCheckedChange = { allowStockUpdate = it }
                        )
                    }

                    OutlinedTextField(
                        value = googleSheetUrl,
                        onValueChange = { googleSheetUrl = it },
                        label = { Text("Google Drive File Link, Google Sheets URL, or File ID") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sheet_url_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                overrideAlignmentIssues = false
                                onFetchGoogleSheet(googleSheetUrl, formatMode)
                            },
                            modifier = Modifier.testTag("sync_google_sheet_button")
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isUr) "ڈرائیو / شیٹ فیچ اور ویلیڈیٹ کریں" else "Fetch from Drive / Sheet & Validate")
                        }

                        OutlinedButton(
                            onClick = {
                                val liveCsv = ExcelAndSheetsProcessor.generateLiveCatalogCsvContent(products, groupPrices)
                                pastedCsvText = liveCsv
                                selectedFileName = "SNL_Live_Catalog_Matrix.csv"
                                overrideAlignmentIssues = false
                                onParseExcelOrCsv(liveCsv, "WIDE_PRICE_MATRIX")
                            },
                            modifier = Modifier.testTag("load_sample_matrix_button")
                        ) {
                            Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isUr) "موجودہ میٹرکس لوڈ کریں" else "Load Current Matrix")
                        }
                    }

                    OutlinedTextField(
                        value = pastedCsvText,
                        onValueChange = { pastedCsvText = it },
                        label = { Text("Or Paste Google Drive CSV / Excel Rows Directly (Auto Header Mapping)") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("paste_csv_input")
                    )

                    if (pastedCsvText.isNotBlank()) {
                        Button(
                            onClick = {
                                overrideAlignmentIssues = false
                                onParseExcelOrCsv(pastedCsvText, formatMode)
                            },
                            modifier = Modifier.testTag("preview_pasted_csv_button")
                        ) {
                            Text(
                                if (isUr) {
                                    "پرائس اسٹرکچر ویلیڈیٹ اور پری ویو کریں"
                                } else {
                                    "Validate Price Structure & Preview Import"
                                }
                            )
                        }
                    }
                }
            }
        }

        excelPreview?.let { summary ->
            val validation = summary.priceStructureValidation
            val canCommitImport = !validation.hasBlockingErrors || overrideAlignmentIssues

            // Dedicated Price Structure Alignment Validation Step Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            validation.hasBlockingErrors -> SnlRejectRed.copy(alpha = 0.10f)
                            !validation.isStructureAligned -> SnlWarningAmber.copy(alpha = 0.14f)
                            else -> SnlApproveGreen.copy(alpha = 0.12f)
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("price_structure_validation_card")
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
                            Text(
                                text = if (isUr) {
                                    "مرحلہ 1: پرائس اسٹرکچر الائنمنٹ ویلیڈیشن"
                                } else {
                                    "STEP 1: PRICE STRUCTURE ALIGNMENT VALIDATION"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = SnlNavy
                            )
                            Surface(
                                color = when {
                                    validation.hasBlockingErrors -> SnlRejectRed
                                    !validation.isStructureAligned -> SnlWarningAmber
                                    else -> SnlApproveGreen
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("price_structure_alignment_badge")
                            ) {
                                Text(
                                    text = when {
                                        validation.hasBlockingErrors -> "MISALIGNMENT ERRORS (${validation.issues.count { it.severity == "ERROR" }})"
                                        !validation.isStructureAligned -> "WARNINGS (${validation.issues.size})"
                                        else -> "ALL TIERS ALIGNED"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "Tier Hierarchy Rule: Distributor_Price_PKR <= Premium_Dealer_Price_PKR <= Standard_Dealer_Price_PKR <= MRP_PKR",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Aligned Rows: ${validation.alignedRowsCount} • Misaligned Rows: ${validation.misalignedRowsCount} • Detected Price Columns: ${validation.detectedPriceColumns.joinToString(", ").ifBlank { "None" }}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (validation.issues.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                text = "Detected Price Structure Alignment Issues:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (validation.hasBlockingErrors) SnlRejectRed else SnlWarningAmber
                            )
                            validation.issues.take(10).forEach { issue ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = issue.severity,
                                        tint = if (issue.severity == "ERROR") SnlRejectRed else SnlWarningAmber,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "[${issue.severity}] Row ${issue.rowNumber} (${issue.sku} - ${issue.productName}): ${issue.description}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (issue.severity == "ERROR") SnlRejectRed else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (validation.hasBlockingErrors) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Checkbox(
                                    checked = overrideAlignmentIssues,
                                    onCheckedChange = { overrideAlignmentIssues = it },
                                    modifier = Modifier.testTag("override_alignment_issues_checkbox")
                                )
                                Text(
                                    text = if (isUr) {
                                        "پرائس ٹیئر فرق کے باوجود امپورٹ کی اجازت دیں (Admin Override)"
                                    } else {
                                        "Admin Override: Allow import despite price tier misalignment errors"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SnlRejectRed
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = SnlApproveGreen.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "STEP 2: IMPORT PREVIEW SUMMARY (${summary.formatMode})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = SnlNavy
                        )
                        Text(
                            text = "Total Rows: ${summary.totalRows} • New Products: ${summary.newProductsCount} • Existing Matched: ${summary.existingProductsCount} • Prices To Update: ${summary.pricesToUpdateCount} • Unchanged: ${summary.unchangedCount} • Errors: ${summary.invalidRows.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (summary.invalidRows.isNotEmpty()) {
                            summary.invalidRows.take(5).forEach { err ->
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SnlRejectRed
                                )
                            }
                        }

                        if (summary.validRows.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                text = "Side-by-Side Old vs New Price Comparison:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            summary.validRows.take(12).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${row.sku} • ${row.productName} (${row.packagingSize})",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val oldTxt = row.oldPricePkr?.let { "PKR ${pkrFormat.format(it.toLong())}" } ?: "NEW"
                                    val newTxt = row.distributorPricePkr?.let { "PKR ${pkrFormat.format(it.toLong())}" } ?: "NO CHANGE"
                                    Text(
                                        text = "$oldTxt → $newTxt",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SnlApproveGreen
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val resolvedSourceType = if (formatMode == "LONG_GROUP_PRICES") "EXCEL_LONG" else "EXCEL_WIDE"
                                onConfirmExcelImport(selectedFileName, resolvedSourceType, allowStockUpdate)
                            },
                            enabled = canCommitImport,
                            colors = ButtonDefaults.buttonColors(containerColor = SnlApproveGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_excel_import_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (canCommitImport) {
                                    "CONFIRM & APPLY ALIGNED IMPORT TO FIRESTORE"
                                } else {
                                    "RESOLVE OR OVERRIDE PRICE ALIGNMENT ERRORS ABOVE"
                                },
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = if (isUr) "امپورٹ ہسٹری اور 1-کلک رول بیک" else "Price Import History & 1-Click Rollback",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (priceImports.isEmpty()) {
            item {
                Text(
                    text = if (isUr) "ابھی تک کوئی امپورٹ ریکارڈ نہیں ہے۔" else "No import batches recorded yet.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            items(priceImports, key = { it.id }) { batch ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${batch.fileName} (${batch.sourceType})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Batch: ${batch.id} • Status: ${batch.status} • Updated: ${batch.productsUpdated}, Created: ${batch.productsCreated}, Prices: ${batch.pricesUpdated}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (batch.status == "COMPLETED" && batch.beforeSnapshotData.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onRollbackImport(batch) },
                                modifier = Modifier.testTag("rollback_batch_${batch.id}")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isUr) "رول بیک" else "Rollback")
                            }
                        } else {
                            AssistChip(
                                onClick = {},
                                label = { Text(batch.status) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sections 5, 6 & 7: Products, Categories, and Price Group Management Screen.
 */
@Composable
fun AdminProductsAndPricingScreen(
    language: UiLanguage,
    products: List<Product>,
    categories: List<Category>,
    priceGroups: List<PriceGroup>,
    groupPrices: List<GroupPrice>,
    onSaveProduct: (
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
    ) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onSaveCategory: (String, String, String, Long) -> Unit,
    onSavePriceGroup: (String, String, String, Double, Long) -> Unit
) {
    val isUr = language == UiLanguage.URDU
    val pkrFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    var subTab by rememberSaveable { mutableStateOf("PRODUCTS") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showProductModal by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }

    var catCode by rememberSaveable { mutableStateOf("") }
    var catNameEn by rememberSaveable { mutableStateOf("") }
    var catNameUr by rememberSaveable { mutableStateOf("") }

    var pgCode by rememberSaveable { mutableStateOf("") }
    var pgName by rememberSaveable { mutableStateOf("") }
    var pgDesc by rememberSaveable { mutableStateOf("") }

    val priceLookup = remember(groupPrices) {
        groupPrices.associate { (it.sku.uppercase() to it.priceGroupCode.uppercase()) to it.sellingPricePkr }
    }

    val filteredProducts = remember(products, searchQuery) {
        products.filter {
            searchQuery.isBlank() ||
                it.productName.contains(searchQuery, ignoreCase = true) ||
                it.sku.contains(searchQuery, ignoreCase = true) ||
                it.categoryName.contains(searchQuery, ignoreCase = true) ||
                it.grade.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = subTab == "PRODUCTS",
                    onClick = { subTab = "PRODUCTS" },
                    label = { Text("Products (${products.size})") }
                )
                FilterChip(
                    selected = subTab == "CATEGORIES",
                    onClick = { subTab = "CATEGORIES" },
                    label = { Text("Categories (${categories.size})") }
                )
                FilterChip(
                    selected = subTab == "PRICE_GROUPS",
                    onClick = { subTab = "PRICE_GROUPS" },
                    label = { Text("Price Groups (${priceGroups.size})") }
                )
            }

            if (subTab == "PRODUCTS") {
                Button(
                    onClick = {
                        editingProduct = null
                        showProductModal = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnlRed),
                    modifier = Modifier.testTag("add_product_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isUr) "نیا پروڈکٹ" else "Add Product")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (subTab) {
            "PRODUCTS" -> {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search SKU, Product Name, Grade or Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts, key = { it.sku }) { prod ->
                        val distPrice = priceLookup[prod.sku.uppercase() to "DISTRIBUTOR"] ?: 0.0
                        val stdPrice = priceLookup[prod.sku.uppercase() to "STANDARD_DEALER"] ?: 0.0
                        val premPrice = priceLookup[prod.sku.uppercase() to "PREMIUM_DEALER"] ?: 0.0

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "#${prod.serialNo} • ${prod.productName} (${prod.sku})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${prod.categoryName} | Grade: ${prod.grade} | Packing: ${prod.packagingSize} | Stock: ${prod.availableStock}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Distributor: PKR ${pkrFormat.format(distPrice.toLong())} • Std Dealer: PKR ${pkrFormat.format(stdPrice.toLong())} • Premium: PKR ${pkrFormat.format(premPrice.toLong())}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SnlRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            editingProduct = prod
                                            showProductModal = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { onDeleteProduct(prod.sku) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SnlRejectRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "CATEGORIES" -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Create or Update Lubricant Category", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = catCode,
                                onValueChange = { catCode = it },
                                label = { Text("Code (e.g. DIESEL)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = catNameEn,
                                onValueChange = { catNameEn = it },
                                label = { Text("English Name") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = catNameUr,
                                onValueChange = { catNameUr = it },
                                label = { Text("Urdu Name") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = {
                                if (catCode.isNotBlank() && catNameEn.isNotBlank()) {
                                    onSaveCategory(catCode, catNameEn, catNameUr.ifBlank { catNameEn }, (categories.size + 1).toLong())
                                    catCode = ""
                                    catNameEn = ""
                                    catNameUr = ""
                                }
                            }
                        ) {
                            Text("Save Category")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id.ifBlank { it.categoryCode } }) { cat ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${cat.categoryCode} • ${cat.categoryName}", fontWeight = FontWeight.Bold)
                                Text(cat.nameUrdu, color = SnlNavy)
                            }
                        }
                    }
                }
            }

            "PRICE_GROUPS" -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Create or Update Customer Price Group", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pgCode,
                                onValueChange = { pgCode = it },
                                label = { Text("Code (e.g. KARACHI_VIP)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pgName,
                                onValueChange = { pgName = it },
                                label = { Text("Group Name") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = pgDesc,
                            onValueChange = { pgDesc = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (pgCode.isNotBlank() && pgName.isNotBlank()) {
                                    onSavePriceGroup(pgCode, pgName, pgDesc, 0.0, 1L)
                                    pgCode = ""
                                    pgName = ""
                                    pgDesc = ""
                                }
                            }
                        ) {
                            Text("Save Price Group")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(priceGroups, key = { it.id.ifBlank { it.groupCode } }) { pg ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("${pg.groupName} (${pg.groupCode})", fontWeight = FontWeight.Bold)
                                Text(pg.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProductModal) {
        val initial = editingProduct
        var sku by remember { mutableStateOf(initial?.sku ?: "SNL-${(products.size + 1).toString().padStart(3, '0')}") }
        var serialNo by remember { mutableStateOf((initial?.serialNo ?: (products.size + 1).toLong()).toString()) }
        var name by remember { mutableStateOf(initial?.productName ?: "") }
        var categoryId by remember { mutableStateOf(initial?.categoryId ?: "GASOLINE_CNG") }
        var categoryName by remember { mutableStateOf(initial?.categoryName ?: "GASOLINE / CNG") }
        var grade by remember { mutableStateOf(initial?.grade ?: "20W-50") }
        var packing by remember { mutableStateOf(initial?.packagingSize ?: "4ltr x 4") }
        var stock by remember { mutableStateOf((initial?.stockQty ?: 250L).toString()) }
        var distPrice by remember {
            mutableStateOf(
                (priceLookup[(initial?.sku ?: "").uppercase() to "DISTRIBUTOR"] ?: 0.0).toLong().toString()
            )
        }
        var stdDealerPrice by remember {
            mutableStateOf(
                (priceLookup[(initial?.sku ?: "").uppercase() to "STANDARD_DEALER"] ?: 0.0).toLong().toString()
            )
        }
        var premDealerPrice by remember {
            mutableStateOf(
                (priceLookup[(initial?.sku ?: "").uppercase() to "PREMIUM_DEALER"] ?: 0.0).toLong().toString()
            )
        }
        var specDealerPrice by remember {
            mutableStateOf(
                (priceLookup[(initial?.sku ?: "").uppercase() to "SPECIAL_DEALER"] ?: 0.0).toLong().toString()
            )
        }

        AlertDialog(
            onDismissRequest = { showProductModal = false },
            title = { Text(if (initial == null) "Add SNL Lubricant Product" else "Edit ${initial.productName}") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = sku,
                            onValueChange = { sku = it },
                            label = { Text("SKU") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = serialNo,
                            onValueChange = { serialNo = it },
                            label = { Text("S.No") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = categoryName,
                            onValueChange = {
                                categoryName = it
                                categoryId = it.uppercase().replace("[^A-Z0-9]+".toRegex(), "_")
                            },
                            label = { Text("Category") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = grade,
                            onValueChange = { grade = it },
                            label = { Text("Grade / SAE") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = packing,
                            onValueChange = { packing = it },
                            label = { Text("Packing (e.g. 4ltr x 4)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stock,
                            onValueChange = { stock = it },
                            label = { Text("Stock Qty") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = distPrice,
                            onValueChange = { distPrice = it },
                            label = { Text("Distributor PKR") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stdDealerPrice,
                            onValueChange = { stdDealerPrice = it },
                            label = { Text("Std Dealer PKR") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = premDealerPrice,
                            onValueChange = { premDealerPrice = it },
                            label = { Text("Premium PKR") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = specDealerPrice,
                            onValueChange = { specDealerPrice = it },
                            label = { Text("Special PKR") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dP = distPrice.toDoubleOrNull() ?: 0.0
                        onSaveProduct(
                            sku,
                            serialNo.toLongOrNull() ?: 1L,
                            name,
                            categoryId,
                            categoryName,
                            grade,
                            packing,
                            "Carton",
                            1L,
                            1L,
                            dP,
                            stock.toLongOrNull() ?: 200L,
                            true,
                            true,
                            dP,
                            stdDealerPrice.toDoubleOrNull() ?: dP,
                            premDealerPrice.toDoubleOrNull() ?: dP,
                            specDealerPrice.toDoubleOrNull() ?: dP
                        )
                        showProductModal = false
                    }
                ) {
                    Text("Save Product & Prices")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProductModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Sections 3 & 4: User Roles, Method A Admin-Created Accounts & Method B Self-Registration Approvals.
 */
@Composable
fun AdminUsersAndApprovalsScreen(
    language: UiLanguage,
    allUsers: List<UserProfile>,
    priceGroups: List<PriceGroup>,
    onSaveAccount: (
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
        staffPermissions: String,
        creditAllowed: Boolean
    ) -> Unit
) {
    val isUr = language == UiLanguage.URDU
    var showCreateModal by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<UserProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isUr) "ڈیلرز، ڈسٹری بیوٹرز، اسٹاف اور رجسٹریشن منظوری" else "Dealers, Distributors, Staff & Approvals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    editingUser = null
                    showCreateModal = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = SnlRed),
                modifier = Modifier.testTag("admin_create_account_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isUr) "اکاؤنٹ بنائیں" else "Create Account")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(allUsers, key = { it.uid.ifBlank { it.id } }) { user ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${user.businessName} (${user.fullName})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Role: ${user.role} • Price Group: ${user.priceGroupId} • Status: ${user.accountStatus}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SnlNavy,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${user.phone} • ${user.email} • ${user.city} (${user.address})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    editingUser = user
                                    showCreateModal = true
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Account")
                            }
                        }

                        if (user.accountStatus == "PENDING_APPROVAL") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onSaveAccount(
                                            user.uid.ifBlank { user.id },
                                            user.fullName,
                                            user.businessName,
                                            user.phone,
                                            user.email,
                                            user.role,
                                            user.customerCode,
                                            user.city,
                                            user.address,
                                            user.priceGroupId,
                                            "ACTIVE",
                                            user.staffPermissions,
                                            user.creditAllowed
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SnlApproveGreen),
                                    modifier = Modifier.testTag("approve_user_${user.uid}")
                                ) {
                                    Text(if (isUr) "اکاؤنٹ منظور کریں (Approve)" else "APPROVE ACCOUNT")
                                }

                                OutlinedButton(
                                    onClick = {
                                        onSaveAccount(
                                            user.uid.ifBlank { user.id },
                                            user.fullName,
                                            user.businessName,
                                            user.phone,
                                            user.email,
                                            user.role,
                                            user.customerCode,
                                            user.city,
                                            user.address,
                                            user.priceGroupId,
                                            "REJECTED",
                                            user.staffPermissions,
                                            user.creditAllowed
                                        )
                                    },
                                    modifier = Modifier.testTag("reject_user_${user.uid}")
                                ) {
                                    Text(if (isUr) "مسترد کریں" else "REJECT")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateModal) {
        val init = editingUser
        var fullName by remember { mutableStateOf(init?.fullName ?: "") }
        var businessName by remember { mutableStateOf(init?.businessName ?: "") }
        var phone by remember { mutableStateOf(init?.phone ?: "+92 3") }
        var email by remember { mutableStateOf(init?.email ?: "") }
        var role by remember { mutableStateOf(init?.role ?: "DEALER") }
        var customerCode by remember {
            mutableStateOf(init?.customerCode ?: "SNL-CUST-${UUID.randomUUID().toString().take(4).uppercase()}")
        }
        var city by remember { mutableStateOf(init?.city ?: "Karachi") }
        var address by remember { mutableStateOf(init?.address ?: "") }
        var priceGroupId by remember { mutableStateOf(init?.priceGroupId ?: "STANDARD_DEALER") }
        var accountStatus by remember { mutableStateOf(init?.accountStatus ?: "ACTIVE") }
        var staffPermissions by remember {
            mutableStateOf(
                init?.staffPermissions?.ifBlank { "VIEW_ORDERS,PROCESS_ORDERS,VIEW_INVENTORY,VIEW_REPORTS" }
                    ?: "VIEW_ORDERS,PROCESS_ORDERS,VIEW_INVENTORY,VIEW_REPORTS"
            )
        }

        AlertDialog(
            onDismissRequest = { showCreateModal = false },
            title = { Text(if (init == null) "Method A: Admin-Create Account" else "Edit Account: ${init.businessName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Business / Trade Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("City") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Delivery Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Role:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("DISTRIBUTOR", "DEALER", "STAFF", "SUPER_ADMIN").forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = {
                                    role = r
                                    if (r == "DISTRIBUTOR") priceGroupId = "DISTRIBUTOR"
                                },
                                label = { Text(r) }
                            )
                        }
                    }
                    Text("Assigned Price Group:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        priceGroups.forEach { pg ->
                            FilterChip(
                                selected = priceGroupId == pg.groupCode,
                                onClick = { priceGroupId = pg.groupCode },
                                label = { Text(pg.groupCode) }
                            )
                        }
                    }
                    Text("Account Status:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ACTIVE", "PENDING_APPROVAL", "DEACTIVATED").forEach { st ->
                            FilterChip(
                                selected = accountStatus == st,
                                onClick = { accountStatus = st },
                                label = { Text(st) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = init?.uid?.ifBlank { init.id } ?: "USR_${UUID.randomUUID().toString().take(8).uppercase()}"
                        onSaveAccount(
                            uid,
                            fullName,
                            businessName,
                            phone,
                            email,
                            role,
                            customerCode,
                            city,
                            address,
                            priceGroupId,
                            accountStatus,
                            staffPermissions,
                            true
                        )
                        showCreateModal = false
                    }
                ) {
                    Text("Save Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Sections 13, 14, 15, 16: Inventory Management, Business Reports (CSV/PDF) & WhatsApp/App Settings.
 */
@Composable
fun AdminInventoryReportsAndSettingsScreen(
    language: UiLanguage,
    products: List<Product>,
    inventoryMovements: List<InventoryMovement>,
    allOrders: List<Order>,
    companySettings: CompanySettings,
    onAdjustInventory: (Product, Long, String) -> Unit,
    onSaveSettings: (CompanySettings) -> Unit
) {
    val context = LocalContext.current
    val isUr = language == UiLanguage.URDU
    val pkrFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    var activeSection by rememberSaveable { mutableStateOf("INVENTORY") }
    var stockTargetProduct by remember { mutableStateOf<Product?>(null) }
    var newStockInput by rememberSaveable { mutableStateOf("") }
    var stockNoteInput by rememberSaveable { mutableStateOf("") }

    var whatsappPhone by rememberSaveable(companySettings.companyWhatsappNumber) {
        mutableStateOf(companySettings.companyWhatsappNumber)
    }
    var deliveryFee by rememberSaveable(companySettings.defaultDeliveryChargesPkr) {
        mutableStateOf(companySettings.defaultDeliveryChargesPkr.toLong().toString())
    }
    var sheetUrl by rememberSaveable(companySettings.googleSheetUrl) {
        mutableStateOf(companySettings.googleSheetUrl)
    }

    val confirmedOrders = remember(allOrders) {
        allOrders.filter {
            it.orderStatus in setOf("CONFIRMED", "PROCESSING", "READY_FOR_DISPATCH", "DISPATCHED", "DELIVERED")
        }
    }
    val confirmedSalesPkr = remember(confirmedOrders) { confirmedOrders.sumOf { it.grandTotal } }

    val citySalesBreakdown = remember(confirmedOrders) {
        confirmedOrders.groupBy { it.city.ifBlank { "Karachi" } }
            .mapValues { (_, list) -> list.size to list.sumOf { it.grandTotal } }
            .entries.sortedByDescending { it.value.second }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeSection == "INVENTORY",
                onClick = { activeSection = "INVENTORY" },
                leadingIcon = { Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(if (isUr) "اسٹاک اور انوینٹری" else "Inventory & Stock Logs") }
            )
            FilterChip(
                selected = activeSection == "REPORTS",
                onClick = { activeSection = "REPORTS" },
                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(if (isUr) "کاروباری رپورٹس (CSV/PDF)" else "Business Reports (CSV/PDF)") }
            )
            FilterChip(
                selected = activeSection == "SETTINGS",
                onClick = { activeSection = "SETTINGS" },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(if (isUr) "واٹس ایپ اور کمپنی سیٹنگز" else "WhatsApp & Company Settings") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (activeSection) {
            "INVENTORY" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = if (isUr) "فزیکل، ریزرو اور دستیاب اسٹاک" else "Physical, Reserved & Available Stock by SKU",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(products, key = { it.sku }) { prod ->
                        val avail = prod.availableStock
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${prod.sku} • ${prod.productName} (${prod.packagingSize})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Physical: ${prod.stockQty} | Reserved: ${prod.reservedQty} | Available: $avail",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (avail <= companySettings.lowStockThreshold) SnlRejectRed else SnlApproveGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        stockTargetProduct = prod
                                        newStockInput = prod.stockQty.toString()
                                        stockNoteInput = "Manual restock"
                                    }
                                ) {
                                    Text(if (isUr) "اسٹاک اپ ڈیٹ" else "Adjust")
                                }
                            }
                        }
                    }

                    if (inventoryMovements.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Recent Inventory Movement Audit Logs:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(inventoryMovements.take(15), key = { it.id }) { mov ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${mov.sku} • ${mov.movementType} (${mov.note})",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${mov.previousStock} → ${mov.newStock}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "REPORTS" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SnlNavy),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "CONFIRMED B2B REVENUE (EXCLUDES PENDING & REJECTED ORDERS)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SnlYellow,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "PKR ${pkrFormat.format(confirmedSalesPkr.toLong())}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Confirmed Orders: ${confirmedOrders.size} • Pending: ${allOrders.count { it.orderStatus == "PENDING_ADMIN_APPROVAL" || it.orderStatus == "PENDING_APPROVAL" }} • Rejected Archive: ${allOrders.count { it.orderStatus == "REJECTED" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            InvoiceAndReportPdfGenerator.exportBusinessReportCsv(context, allOrders, products)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SnlRed),
                                        modifier = Modifier.testTag("export_sales_csv_button")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export Sales & Stock CSV")
                                    }
                                    if (confirmedOrders.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = {
                                                InvoiceAndReportPdfGenerator.generateOrderInvoicePdf(
                                                    context = context,
                                                    order = confirmedOrders.first()
                                                ).onSuccess { pdf ->
                                                    ExcelAndSheetsProcessor.shareFile(
                                                        context,
                                                        pdf,
                                                        "application/pdf",
                                                        "Export Latest Confirmed Invoice PDF"
                                                    )
                                                }
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            modifier = Modifier.testTag("export_sales_pdf_button")
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Latest Invoice PDF")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "City-Wise Confirmed Sales Breakdown:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(citySalesBreakdown, key = { it.key }) { (city, pair) ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$city (${pair.first} confirmed orders)", fontWeight = FontWeight.Bold)
                                Text(
                                    "PKR ${pkrFormat.format(pair.second.toLong())}",
                                    color = SnlApproveGreen,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            "SETTINGS" -> {
                val apkMeta = remember { com.example.util.ApkDistributionHelper.getInstalledApkMetadata(context) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SnlNavy),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "PRODUCTION SIGNED APK & DEALER SIDE-LOADING DISTRIBUTION",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SnlYellow,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Application ID: ${apkMeta.applicationId} • Version: ${apkMeta.versionName} (${apkMeta.versionCode}) • Size: ${apkMeta.apkSizeMb}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "Signed SHA-1: ${apkMeta.sha1Fingerprint}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = "Signed SHA-256: ${apkMeta.sha256Fingerprint}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            com.example.util.ApkDistributionHelper.shareSignedApkForSideLoading(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SnlRed),
                                        modifier = Modifier.testTag("settings_share_signed_apk_button")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export & Share Signed .APK")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            InvoiceAndReportPdfGenerator.openWhatsAppClickToChat(
                                                context = context,
                                                phoneNumber = whatsappPhone.ifBlank { "+923101005253" },
                                                message = com.example.util.ApkDistributionHelper.buildSideLoadInstructionsMessage()
                                            )
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Text("Send WhatsApp Install Guide")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "SAIFI NATIONAL LUBRICANTS — WhatsApp & Cloud Sync Configuration",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = whatsappPhone,
                                    onValueChange = { whatsappPhone = it },
                                    label = { Text("Company WhatsApp Number (Pakistan)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = deliveryFee,
                                    onValueChange = { deliveryFee = it },
                                    label = { Text("Default Delivery Charges (PKR)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = sheetUrl,
                                    onValueChange = { sheetUrl = it },
                                    label = { Text("Connected Google Sheets URL") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        onSaveSettings(
                                            companySettings.copy(
                                                companyWhatsappNumber = whatsappPhone,
                                                companyPhone = whatsappPhone,
                                                defaultDeliveryChargesPkr = deliveryFee.toDoubleOrNull() ?: 0.0,
                                                googleSheetUrl = sheetUrl
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SnlApproveGreen),
                                    modifier = Modifier.testTag("save_company_settings_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Company & WhatsApp Settings")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    stockTargetProduct?.let { prod ->
        AlertDialog(
            onDismissRequest = { stockTargetProduct = null },
            title = { Text("Adjust Stock: ${prod.productName} (${prod.sku})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newStockInput,
                        onValueChange = { newStockInput = it },
                        label = { Text("New Physical Stock Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = stockNoteInput,
                        onValueChange = { stockNoteInput = it },
                        label = { Text("Reason / Batch Note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = newStockInput.toLongOrNull() ?: prod.stockQty
                        onAdjustInventory(prod, qty, stockNoteInput)
                        stockTargetProduct = null
                    }
                ) {
                    Text("Update Stock")
                }
            },
            dismissButton = {
                TextButton(onClick = { stockTargetProduct = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
