                                com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                    val bill = Bill(
                                        id = billId,
                                        accountId = accountId,
                                        billNumber = doc.getString("billNumber") ?: "",
                                        billType = doc.getString("billType") ?: "SALE",
                                        isGstBill = doc.getBoolean("isGstBill") ?: false,
                                        partyName = doc.getString("partyName") ?: "",
                                        partyMobile = doc.getString("partyMobile") ?: "",
                                        partyAddress = doc.getString("partyAddress") ?: "",
                                        partyAadharNumber = doc.getString("partyAadharNumber") ?: "",
                                        partyPanNumber = doc.getString("partyPanNumber") ?: "",
                                        partyGstNumber = doc.getString("partyGstNumber") ?: "",
                                        dateTimestamp = doc.getLong("dateTimestamp") ?: System.currentTimeMillis(),
                                        itemsJson = doc.getString("itemsJson") ?: "[]",
                                        subtotal = doc.getDouble("subtotal") ?: 0.0,
                                        gstPercent = doc.getDouble("gstPercent") ?: 3.0,
                                        gstAmount = doc.getDouble("gstAmount") ?: 0.0,
                                        discount = doc.getDouble("discount") ?: 0.0,
                                        grandTotal = doc.getDouble("grandTotal") ?: 0.0,
                                        cashReceivedOrPaid = doc.getDouble("cashReceivedOrPaid") ?: 0.0,
                                        oldMetalExchangeAmount = doc.getDouble("oldMetalExchangeAmount") ?: 0.0,
                                        otherCharges = doc.getDouble("otherCharges") ?: 0.0,
                                        otherChargesRemark = doc.getString("otherChargesRemark") ?: "",
                                        netBalanceDue = doc.getDouble("netBalanceDue") ?: 0.0,
                                        notes = doc.getString("notes") ?: "",
                                        paymentsJson = doc.getString("paymentsJson") ?: "[]",
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                    )
                                    // Realtime listener must never erase locally saved Other Charges
                                    // when an older/partial cloud document does not contain them.
                                    val existingLocal = db.billDao().getBillById(billId)
                                    val billToSave = if (bill.otherChargesRemark.isBlank() && existingLocal?.otherChargesRemark?.isNotBlank() == true) {
                                        bill.copy(
                                            otherCharges = if (bill.otherCharges > 0) bill.otherCharges else existingLocal.otherCharges,
                                            otherChargesRemark = existingLocal.otherChargesRemark
                                        )
                                    } else {
                                        bill
                                    }
                                    db.billDao().insertBill(billToSave)
                                }
                                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                    db.billDao().deleteBillById(billId, accountId)
                                }
                            }
                        }
                    }
                }

            // Listen for stock changes from another phone
            stockListener = firestore.collection("jeweller_accounts")
                .document(accountId)
                .collection("stock_transactions")
                .addSnapshotListener { snapshot, err ->
                    if (err != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        for (change in snapshot.documentChanges) {
                            val doc = change.document
                            val txId = doc.id
                            when (change.type) {
                                com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                    val tx = StockTransaction(