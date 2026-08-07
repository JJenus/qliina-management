package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.InvoiceLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, UUID> {

    List<InvoiceLineItem> findAllByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);
}
