package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CartItem
import com.example.data.seed.AttachedPriceListCatalog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnlRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun superAdminBootstrap_59ProductImport_dealerOrder_acceptAndSoftDeleteReject() = runBlocking {
        val repo = SnlRepository(db)

        // 1. Unauthenticated read must fail with PERMISSION_DENIED
        auth.signOut()
        try {
            withTimeout(10_000L) {
                db.collection("products").get().await()
            }
            assertTrue("Expected PERMISSION_DENIED when unauthenticated", false)
        } catch (e: Exception) {
            assertNotNull(e.unwrapFirestoreException() ?: e)
        }

        // 2. Sign in as Super Admin & bootstrap settings + 59 SNL products from attached price list
        val adminUid = signInTestUser("muhammad.dh52@gmail.com")
        val bootRes = withTimeout(15_000L) {
            repo.bootstrapSuperAdminAndSettings("Saifi National Lubricants HQ", "muhammad.dh52@gmail.com")
        }
        assertTrue("Bootstrap should succeed: ${bootRes.exceptionOrNull()?.message}", bootRes.isSuccess)

        val importRes = withTimeout(30_000L) {
            repo.commitPriceListImport(
                fileName = "Attached_SNL_Sole_Distributor_Price_List_03-11-26.jpg",
                sourceType = "AI_IMAGE_OCR",
                rows = AttachedPriceListCatalog.attachedImageExtractedRows,
                targetSingleColumnGroup = "DISTRIBUTOR",
                alsoCopyToStandardDealerIfBlank = true,
                allowStockUpdate = false
            )
        }
        assertTrue("59-item import should succeed: ${importRes.exceptionOrNull()?.message}", importRes.isSuccess)

        val activeProducts = withTimeout(15_000L) {
            repo.observeActiveProducts().first { it.size == 59 }
        }
        assertEquals(59, activeProducts.size)

        // 3. Sign in as a Dealer, submit self-registration (Method B -> PENDING_APPROVAL)
        val dealerUid = signInTestUser("dealer.karachi@example.com")
        val regRes = withTimeout(15_000L) {
            repo.submitSelfRegistration(
                fullName = "Tariq Mahmood",
                businessName = "Tariq Auto Lubes",
                phone = "+923001112233",
                email = "dealer.karachi@example.com",
                city = "Karachi",
                address = "Plaza Quarters, M.A. Jinnah Road, Karachi",
                role = "DEALER"
            )
        }
        assertTrue("Dealer registration should succeed: ${regRes.exceptionOrNull()?.message}", regRes.isSuccess)
        val pendingProfile = withTimeout(15_000L) {
            repo.observeUserProfile(dealerUid).first { it != null }
        }
        assertNotNull(pendingProfile)
        assertEquals("PENDING_APPROVAL", pendingProfile!!.accountStatus)

        // 4. Super Admin approves Dealer account and assigns DISTRIBUTOR price group
        signInTestUser("muhammad.dh52@gmail.com")
        val approveRes = withTimeout(15_000L) {
            repo.adminCreateOrUpdateAccount(
                targetUid = dealerUid,
                fullName = pendingProfile.fullName,
                businessName = pendingProfile.businessName,
                phone = pendingProfile.phone,
                email = pendingProfile.email,
                role = "DISTRIBUTOR",
                customerCode = pendingProfile.customerCode,
                city = pendingProfile.city,
                address = pendingProfile.address,
                priceGroupId = "DISTRIBUTOR",
                accountStatus = "ACTIVE",
                staffPermissions = "",
                creditAllowed = true
            )
        }
        assertTrue("Admin account approval should succeed: ${approveRes.exceptionOrNull()?.message}", approveRes.isSuccess)

        // 5. Dealer signs back in and places two orders with immutable price snapshots
        signInTestUser("dealer.karachi@example.com")
        val activeDealerProfile = withTimeout(15_000L) {
            repo.observeUserProfile(dealerUid).first { it?.accountStatus == "ACTIVE" }!!
        }
        assertEquals("ACTIVE", activeDealerProfile.accountStatus)

        val productReborn = activeProducts.first { it.sku == "SNL-001" } // Reborn 700ml x 12 = PKR 5,105
        assertEquals("Reborn", productReborn.name)
        assertEquals("MIX Brands", productReborn.category)
        assertEquals(5105.0, productReborn.priceTiers["DISTRIBUTOR"] ?: 0.0, 0.01)
        assertEquals("IN_STOCK", productReborn.stockAvailability)
        assertTrue(productReborn.inStock)
        val cart = listOf(
            CartItem(
                product = productReborn,
                unitPricePkr = 5105.0,
                priceGroupCode = "DISTRIBUTOR",
                quantity = 4
            )
        )

        val order1Res = withTimeout(15_000L) {
            repo.placeCustomerOrder(
                userProfile = activeDealerProfile,
                cartItems = cart,
                deliveryAddress = activeDealerProfile.address,
                city = activeDealerProfile.city,
                phone = activeDealerProfile.phone,
                orderInstructions = "Urgent morning dispatch",
                paymentMethod = "COD",
                deliveryChargesPkr = 0.0,
                discountPkr = 0.0
            )
        }
        assertTrue("Order 1 placement should succeed: ${order1Res.exceptionOrNull()?.message}", order1Res.isSuccess)
        val order1 = order1Res.getOrThrow()

        val order2Res = withTimeout(15_000L) {
            repo.placeCustomerOrder(
                userProfile = activeDealerProfile,
                cartItems = cart,
                deliveryAddress = activeDealerProfile.address,
                city = activeDealerProfile.city,
                phone = activeDealerProfile.phone,
                orderInstructions = "Second test order to reject",
                paymentMethod = "COD",
                deliveryChargesPkr = 0.0,
                discountPkr = 0.0
            )
        }
        assertTrue("Order 2 placement should succeed: ${order2Res.exceptionOrNull()?.message}", order2Res.isSuccess)
        val order2 = order2Res.getOrThrow()

        // Both orders are visible to customer initially
        val customerOrdersBefore = withTimeout(15_000L) {
            repo.observeCustomerOrders(dealerUid).first { it.size == 2 }
        }
        assertEquals(2, customerOrdersBefore.size)

        // 6. Super Admin accepts Order 1 and rejects Order 2 (soft-delete: visibleToCustomer = false)
        signInTestUser("muhammad.dh52@gmail.com")
        assertEquals(adminUid, auth.currentUser?.uid)

        val acceptOrder1 = withTimeout(15_000L) {
            repo.adminAcceptOrder(order1)
        }
        assertTrue("Admin accept order 1 should succeed: ${acceptOrder1.exceptionOrNull()?.message}", acceptOrder1.isSuccess)

        val rejectOrder2 = withTimeout(15_000L) {
            repo.adminRejectOrder(order2, "Out of stock for current batch")
        }
        assertTrue("Admin reject order 2 should succeed: ${rejectOrder2.exceptionOrNull()?.message}", rejectOrder2.isSuccess)

        // Verify Admin sees both in allOrdersForAdmin, with Order 2 in Rejected Archive
        val adminAllOrders = withTimeout(15_000L) {
            repo.observeAllOrdersForAdmin().first { list ->
                list.any { it.id == order2.id && it.orderStatus == "REJECTED" }
            }
        }
        val archivedOrder2 = adminAllOrders.first { it.id == order2.id }
        assertEquals("REJECTED", archivedOrder2.orderStatus)
        assertFalse("Rejected order must have visibleToCustomer = false", archivedOrder2.visibleToCustomer)

        // 7. Dealer queries their orders: Order 2 MUST be completely hidden from customer screens!
        signInTestUser("dealer.karachi@example.com")
        val customerOrdersAfter = withTimeout(15_000L) {
            repo.observeCustomerOrders(dealerUid).first { list -> list.size == 1 }
        }
        assertEquals(1, customerOrdersAfter.size)
        assertEquals(order1.id, customerOrdersAfter.first().id)
        assertEquals("CONFIRMED", customerOrdersAfter.first().orderStatus)
    }
}
