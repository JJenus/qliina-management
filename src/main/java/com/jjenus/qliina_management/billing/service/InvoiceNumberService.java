package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.InvoiceNumberCounter;
import com.jjenus.qliina_management.billing.repository.InvoiceNumberCounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Sequential, collision-free invoice numbers (INV-YYYY-000123). Rows are
 * pessimistically locked while allocating so concurrent billing sweeps can
 * never issue the same number (§2 invoices).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceNumberService {

    private final InvoiceNumberCounterRepository counterRepository;

    @Transactional
    public String nextInvoiceNumber(LocalDateTime at) {
        int year = at.getYear();
        InvoiceNumberCounter counter = counterRepository.findByYearForUpdate(year)
                .orElseGet(() -> createCounter(year));
        long n = counter.getNextValue();
        counter.setNextValue(n + 1);
        counterRepository.save(counter);
        return String.format("INV-%04d-%06d", year, n);
    }

    private InvoiceNumberCounter createCounter(int year) {
        InvoiceNumberCounter counter = new InvoiceNumberCounter(year, 1L);
        try {
            return counterRepository.saveAndFlush(counter);
        } catch (DataIntegrityViolationException e) {
            // Another transaction created the row first — pick up the lock on it.
            return counterRepository.findByYearForUpdate(year)
                    .orElseThrow(() -> new IllegalStateException("Invoice counter race for year " + year));
        }
    }
}
