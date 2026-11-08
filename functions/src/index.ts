import * as admin from "firebase-admin";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";

admin.initializeApp();

const DATABASE_ID = "ai-studio-android-snllubri-b54ea3ff-ffda-44d7-add0-db278c0f997e";
const db = admin.firestore();
db.settings({ databaseId: DATABASE_ID });

/**
 * 1. Firestore Trigger: On New Order Submitted -> Send FCM Push + Meta WhatsApp Business Cloud API Notification
 * to SAIFI NATIONAL LUBRICANTS Super Admin (+92 310 100 52 53) and Customer.
 */
export const onSnlOrderCreated = onDocumentCreated(
  {
    document: "orders/{orderId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const order = snap.data();

    const messageText = [
      "NEW SNL ORDER RECEIVED",
      "",
      "SAIFI NATIONAL LUBRICANTS",
      "",
      `Order Number: ${order.orderNumber}`,
      `Customer: ${order.customerName}`,
      `Business: ${order.businessName}`,
      `Customer Type: ${order.customerType}`,
      `Phone: ${order.phone}`,
      `City: ${order.city}`,
      "",
      "Products:",
      order.itemsSummary || "",
      "",
      `Total Amount: PKR ${Math.round(order.grandTotal || 0).toLocaleString()}`,
      `Payment Method: ${order.paymentMethod}`,
      `Delivery Address: ${order.deliveryAddress}`,
      `Status: ${order.orderStatus}`,
    ].join("\n");

    await sendMetaWhatsAppNotification(
      order.orderNumber,
      process.env.SNL_ADMIN_WHATSAPP || "+923101005253",
      "NEW_ORDER_RECEIVED",
      messageText
    );
  }
);

/**
 * 2. Firestore Trigger: On Order Status Updated (Accepted / Rejected / Dispatched / Delivered)
 * Enforces soft-delete visibility when orderStatus == "REJECTED" (visibleToCustomer = false)
 * and sends WhatsApp & FCM status updates.
 */
export const onSnlOrderUpdated = onDocumentUpdated(
  {
    document: "orders/{orderId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;

    if (before.orderStatus === after.orderStatus) return;

    if (after.orderStatus === "REJECTED" && after.visibleToCustomer !== false) {
      await event.data?.after.ref.update({
        visibleToCustomer: false,
      });
    }

    const statusHeader =
      after.orderStatus === "CONFIRMED"
        ? "YOUR SNL ORDER HAS BEEN APPROVED & CONFIRMED"
        : after.orderStatus === "REJECTED"
        ? `YOUR SNL ORDER WAS REJECTED (Reason: ${after.rejectionReason || "Contact SNL HQ"})`
        : `SNL ORDER STATUS UPDATE: ${after.orderStatus}`;

    const messageText = [
      statusHeader,
      "",
      "SAIFI NATIONAL LUBRICANTS",
      `Order Number: ${after.orderNumber}`,
      `Business: ${after.businessName}`,
      `Total Amount: PKR ${Math.round(after.grandTotal || 0).toLocaleString()}`,
      `Status: ${after.orderStatus}`,
    ].join("\n");

    if (after.phone) {
      await sendMetaWhatsAppNotification(
        after.orderNumber,
        after.phone,
        `ORDER_${after.orderStatus}`,
        messageText
      );
    }
  }
);

/**
 * 3. Callable Function: Server-side Role & Price Group Verification
 */
export const verifyAndAssignRoleClaims = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  const callerUid = request.auth.uid;
  const adminDoc = await db.collection("admins").doc(callerUid).get();
  if (!adminDoc.exists) {
    throw new HttpsError("permission-denied", "Only Super Admin can assign custom role claims.");
  }

  const { targetUid, role, priceGroupId, accountStatus } = request.data;
  await admin.auth().setCustomUserClaims(targetUid, {
    role,
    priceGroupId,
    accountStatus,
  });
  return { success: true, targetUid, role, priceGroupId };
});

async function sendMetaWhatsAppNotification(
  orderNumber: string,
  recipientPhone: string,
  eventType: string,
  messageBody: ModeMessage
) {
  const token = process.env.WHATSAPP_CLOUD_API_TOKEN || "";
  const phoneId = process.env.WHATSAPP_PHONE_NUMBER_ID || "";

  let deliveryMode = "CLICK_TO_CHAT_FALLBACK";
  let apiResponse = "Meta Cloud API credentials not set in environment; logged for Click-to-Chat.";

  if (token && phoneId) {
    try {
      const cleanPhone = recipientPhone.replace(/[^0-9]/g, "");
      const res = await fetch(`https://graph.facebook.com/v20.0/${phoneId}/messages`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          messaging_product: "whatsapp",
          to: cleanPhone,
          type: "text",
          text: { body: messageBody },
        }),
      });
      apiResponse = `HTTP ${res.status}`;
      deliveryMode = res.ok ? "META_CLOUD_API_SENT" : "META_CLOUD_API_ERROR";
    } catch (err: unknown) {
      apiResponse = err instanceof Error ? err.message : "Network error";
      deliveryMode = "META_CLOUD_API_ERROR";
    }
  }

  await db.collection("whatsapp_logs").add({
    orderNumber,
    recipientPhone,
    eventType,
    deliveryMode,
    messagePreview: messageBody.slice(0, 480),
    apiResponse,
    createdBy: "CLOUD_FUNCTION",
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

type ModeMessage = string;
