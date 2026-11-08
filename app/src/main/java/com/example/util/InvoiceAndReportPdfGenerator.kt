package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.R
import com.example.data.model.Order
import com.example.data.model.Product
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Top-level helper function to generate a printable A4 PDF invoice from an [Order],
 * optionally saving it to a user-selected [destinationUri] or returning the generated [File]
 * for sharing and printing.
 */
fun generatePrintableOrderInvoicePdf(
    context: Context,
    order: Order,
    destinationUri: Uri? = null
): Result<File> = InvoiceAndReportPdfGenerator.generatePrintableOrderInvoicePdf(
    context = context,
    order = order,
    destinationUri = destinationUri
)

object InvoiceAndReportPdfGenerator {

    private val currencyFormat = NumberFormat.getNumberInstance(Locale.US)

    fun formatPkr(amount: Double): String = "PKR ${currencyFormat.format(amount.toLong())}"

    /**
     * Generates a printable A4 SNL-branded PDF Invoice from a completed/submitted [Order],
     * embedding the original SNL logo, customer details, and locked PKR price snapshots.
     * If [destinationUri] is provided (e.g., from `CreateDocument("application/pdf")`), writes
     * the PDF directly to that URI as well.
     */
    fun generatePrintableOrderInvoicePdf(
        context: Context,
        order: Order,
        destinationUri: Uri? = null
    ): Result<File> {
        val result = generateOrderInvoicePdf(context, order)
        if (destinationUri != null) {
            result.onSuccess { pdfFile ->
                runCatching {
                    context.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                        FileInputStream(pdfFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                }
            }
        }
        return result
    }

    /**
     * Generates a professional SNL-branded PDF Invoice embedding the original SNL logo
     * and the order's immutable price snapshot.
     */
    fun generateOrderInvoicePdf(context: Context, order: Order): Result<File> {
        return try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size (595 x 842 pt)
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val navyPaint = Paint().apply {
                color = Color.rgb(12, 35, 64)
                style = Paint.Style.FILL
            }
            val redPaint = Paint().apply {
                color = Color.rgb(209, 25, 32)
                style = Paint.Style.FILL
            }
            val titlePaint = Paint().apply {
                color = Color.rgb(12, 35, 64)
                textSize = 18f
                isFakeBoldText = true
            }
            val subPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 10f
            }
            val boldPaint = Paint().apply {
                color = Color.BLACK
                textSize = 11f
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10f
            }
            val whiteBoldPaint = Paint().apply {
                color = Color.WHITE
                textSize = 10f
                isFakeBoldText = true
            }

            // Draw top brand bar
            canvas.drawRect(0f, 0f, 595f, 10f, redPaint)
            canvas.drawRect(0f, 10f, 595f, 16f, navyPaint)

            // Draw original SNL Logo from drawable
            val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_snl_logo)
            if (logoBitmap != null) {
                val destRect = Rect(36, 26, 116, 106)
                canvas.drawBitmap(logoBitmap, null, destRect, null)
            }

            // Company Header
            canvas.drawText("SAIFI NATIONAL LUBRICANTS (SNL)", 130f, 48f, titlePaint)
            canvas.drawText("Power. Protection. Performance. | Official B2B Tax & Commercial Invoice", 130f, 65f, subPaint)
            canvas.drawText("Head Office: Karachi, Pakistan | Tel / WhatsApp: +92 310 100 52 53", 130f, 80f, subPaint)

            val dateStr = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.US).format(
                order.createdAt?.toDate() ?: Date()
            )
            val invoiceNo = "INV-${order.orderNumber.removePrefix("SNL-")}"

            // Invoice & Customer Box
            canvas.drawRect(36f, 118f, 559f, 210f, Paint().apply {
                color = Color.rgb(245, 247, 250)
                style = Paint.Style.FILL
            })
            canvas.drawText("INVOICE NO: $invoiceNo", 48f, 136f, boldPaint)
            canvas.drawText("ORDER NO: ${order.orderNumber}", 48f, 152f, boldPaint)
            canvas.drawText("DATE: $dateStr", 48f, 168f, bodyPaint)
            canvas.drawText("ORDER STATUS: ${order.orderStatus}", 48f, 184f, bodyPaint)
            canvas.drawText("PAYMENT: ${order.paymentMethod} (${order.paymentStatus})", 48f, 200f, bodyPaint)

            canvas.drawText("BILL TO / DELIVER TO:", 310f, 136f, boldPaint)
            canvas.drawText("${order.businessName} (${order.customerName})", 310f, 152f, bodyPaint)
            canvas.drawText("Role: ${order.customerType} | ID: ${order.customerCode}", 310f, 168f, bodyPaint)
            canvas.drawText("Phone: ${order.phone} | City: ${order.city}", 310f, 184f, bodyPaint)
            canvas.drawText("Address: ${order.deliveryAddress.take(42)}", 310f, 200f, bodyPaint)

            // Table Header
            var y = 232f
            canvas.drawRect(36f, y, 559f, y + 24f, navyPaint)
            canvas.drawText("#", 44f, y + 16f, whiteBoldPaint)
            canvas.drawText("SKU", 66f, y + 16f, whiteBoldPaint)
            canvas.drawText("Product Description", 130f, y + 16f, whiteBoldPaint)
            canvas.drawText("Packing", 290f, y + 16f, whiteBoldPaint)
            canvas.drawText("Grade", 365f, y + 16f, whiteBoldPaint)
            canvas.drawText("Qty", 425f, y + 16f, whiteBoldPaint)
            canvas.drawText("Unit PKR", 455f, y + 16f, whiteBoldPaint)
            canvas.drawText("Total PKR", 510f, y + 16f, whiteBoldPaint)

            y += 40f
            val items = order.parsedItems()
            items.forEachIndexed { idx, item ->
                if (y > 680f) return@forEachIndexed
                canvas.drawText("${idx + 1}", 44f, y, bodyPaint)
                canvas.drawText(item.sku, 66f, y, bodyPaint)
                canvas.drawText(item.productName.take(26), 130f, y, boldPaint)
                canvas.drawText(item.packagingSize.take(14), 290f, y, bodyPaint)
                canvas.drawText(item.grade.take(12), 365f, y, bodyPaint)
                canvas.drawText("${item.quantity}", 425f, y, bodyPaint)
                canvas.drawText(currencyFormat.format(item.unitPricePkr.toLong()), 455f, y, bodyPaint)
                canvas.drawText(currencyFormat.format(item.lineTotalPkr.toLong()), 510f, y, boldPaint)
                y += 20f
            }

            // Totals section
            y += 16f
            canvas.drawLine(36f, y, 559f, y, subPaint)
            y += 20f
            canvas.drawText("Subtotal:", 380f, y, bodyPaint)
            canvas.drawText(formatPkr(order.subtotal), 475f, y, boldPaint)
            y += 18f
            canvas.drawText("Discount:", 380f, y, bodyPaint)
            canvas.drawText(formatPkr(order.discount), 475f, y, bodyPaint)
            y += 18f
            canvas.drawText("Delivery Charges:", 380f, y, bodyPaint)
            canvas.drawText(formatPkr(order.deliveryCharges), 475f, y, bodyPaint)
            y += 22f
            canvas.drawRect(370f, y - 14f, 559f, y + 10f, redPaint)
            canvas.drawText("GRAND TOTAL:", 380f, y + 2f, whiteBoldPaint)
            canvas.drawText(formatPkr(order.grandTotal), 465f, y + 2f, whiteBoldPaint)

            y += 36f
            canvas.drawLine(36f, y, 200f, y, subPaint)
            canvas.drawLine(395f, y, 559f, y, subPaint)
            y += 14f
            canvas.drawText("Authorized SNL Signatory", 36f, y, subPaint)
            canvas.drawText("Dealer / Receiver Stamp", 395f, y, subPaint)

            y += 22f
            canvas.drawText(
                "Note: Generated from immutable order price snapshot (${order.priceGroupId}). Authorized by SAIFI NATIONAL LUBRICANTS.",
                36f,
                y,
                subPaint
            )

            pdfDocument.finishPage(page)

            val exportDir = File(context.cacheDir, "invoices").apply { mkdirs() }
            val safeOrderNo = order.orderNumber.ifBlank { order.id.ifBlank { "SNL_ORDER" } }
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val pdfFile = File(exportDir, "${safeOrderNo}_Invoice.pdf")
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            Result.success(pdfFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates the printable PDF invoice for [order] and opens the Android Share sheet
     * so the user can share via WhatsApp, Email, or save to Google Drive / device storage.
     */
    fun shareOrderInvoicePdf(context: Context, order: Order): Result<File> {
        val result = generatePrintableOrderInvoicePdf(context, order)
        result.onSuccess { pdfFile ->
            ExcelAndSheetsProcessor.shareFile(
                context = context,
                file = pdfFile,
                mimeType = "application/pdf",
                chooserTitle = "Share or Save SNL PDF Invoice (${order.orderNumber})"
            )
        }
        return result
    }

    /**
     * Generates the printable PDF invoice for [order] and launches the Android system
     * Print / Save-as-PDF spooler via [PrintManager].
     */
    fun printOrderInvoicePdf(context: Context, order: Order): Result<File> {
        val result = generatePrintableOrderInvoicePdf(context, order)
        result.onSuccess { pdfFile ->
            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val jobName = "SNL_Invoice_${order.orderNumber.ifBlank { "Order" }}"
                printManager?.print(
                    jobName,
                    object : PrintDocumentAdapter() {
                        override fun onLayout(
                            oldAttributes: PrintAttributes?,
                            newAttributes: PrintAttributes?,
                            cancellationSignal: CancellationSignal?,
                            callback: LayoutResultCallback?,
                            extras: Bundle?
                        ) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback?.onLayoutCancelled()
                                return
                            }
                            val info = PrintDocumentInfo.Builder("${order.orderNumber}_Invoice.pdf")
                                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                .setPageCount(1)
                                .build()
                            callback?.onLayoutFinished(info, true)
                        }

                        override fun onWrite(
                            pages: Array<out PageRange>?,
                            destination: ParcelFileDescriptor?,
                            cancellationSignal: CancellationSignal?,
                            callback: WriteResultCallback?
                        ) {
                            if (destination == null) {
                                callback?.onWriteFailed("Destination descriptor is null")
                                return
                            }
                            try {
                                FileInputStream(pdfFile).use { input ->
                                    FileOutputStream(destination.fileDescriptor).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                            } catch (e: Exception) {
                                callback?.onWriteFailed(e.localizedMessage)
                            }
                        }
                    },
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .build()
                )
            } catch (_: Exception) {
                // Fallback to share chooser if PrintManager is unavailable (e.g., non-Activity context)
                ExcelAndSheetsProcessor.shareFile(
                    context = context,
                    file = pdfFile,
                    mimeType = "application/pdf",
                    chooserTitle = "Print / Save SNL PDF Invoice (${order.orderNumber})"
                )
            }
        }
        return result
    }

    /**
     * Generates and exports a CSV Business Report of confirmed orders and inventory.
     */
    fun exportBusinessReportCsv(
        context: Context,
        orders: List<Order>,
        products: List<Product>
    ): Result<File> {
        return try {
            val confirmedOrders = orders.filter {
                it.orderStatus in setOf("CONFIRMED", "PROCESSING", "READY_FOR_DISPATCH", "DISPATCHED", "DELIVERED")
            }
            val csv = buildString {
                appendLine("SAIFI NATIONAL LUBRICANTS (SNL) — BUSINESS ANALYTICS & SALES REPORT")
                appendLine("Note: Rejected and Pending orders are strictly excluded from confirmed sales totals.")
                appendLine()
                appendLine("OrderNumber,CustomerName,BusinessName,Role,City,PriceGroup,TotalCartons,GrandTotalPKR,PaymentMethod,Status")
                for (o in confirmedOrders) {
                    appendLine("${o.orderNumber},\"${o.customerName}\",\"${o.businessName}\",${o.customerType},\"${o.city}\",${o.priceGroupId},${o.totalCartons},${o.grandTotal.toLong()},${o.paymentMethod},${o.orderStatus}")
                }
                appendLine()
                appendLine("INVENTORY SUMMARY")
                appendLine("SKU,ProductName,Category,Grade,Packing,PhysicalStock,ReservedStock,AvailableStock")
                for (p in products) {
                    appendLine("${p.sku},\"${p.productName}\",\"${p.categoryName}\",\"${p.grade}\",\"${p.packagingSize}\",${p.stockQty},${p.reservedQty},${p.availableStock}")
                }
            }
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "SNL_Sales_Inventory_Report.csv")
            file.writeText(csv)
            ExcelAndSheetsProcessor.shareFile(context, file, "text/csv", "Export SNL CSV Report")
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Formats the exact WhatsApp message required by Section 15 of the Master Prompt.
     */
    fun buildWhatsAppOrderMessage(order: Order, eventHeader: String = "NEW SNL ORDER RECEIVED"): String {
        val itemLines = order.parsedItems().joinToString("\n") { item ->
            "- ${item.productName} (${item.packagingSize} | ${item.grade}) x ${item.quantity} = PKR ${currencyFormat.format(item.lineTotalPkr.toLong())}"
        }
        return buildString {
            appendLine(eventHeader)
            appendLine()
            appendLine("SAIFI NATIONAL LUBRICANTS")
            appendLine()
            appendLine("Order Number: ${order.orderNumber}")
            appendLine("Customer: ${order.customerName}")
            appendLine("Business: ${order.businessName}")
            appendLine("Customer Type: ${order.customerType}")
            appendLine("Phone: ${order.phone}")
            appendLine("City: ${order.city}")
            appendLine()
            appendLine("Products:")
            appendLine(itemLines.ifBlank { order.itemsSummary })
            appendLine()
            appendLine("Total Amount: PKR ${currencyFormat.format(order.grandTotal.toLong())}")
            appendLine("Payment Method: ${order.paymentMethod}")
            appendLine("Delivery Address: ${order.deliveryAddress}")
            appendLine("Status: ${order.orderStatus.replace('_', ' ')}")
            if (order.rejectionReason.isNotBlank()) {
                appendLine("Reason: ${order.rejectionReason}")
            }
        }
    }

    fun openWhatsAppClickToChat(context: Context, phoneNumber: String, message: String) {
        try {
            val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
            val uri = Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignored if browser/WhatsApp unavailable in headless environment
        }
    }
}
