package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.InvoiceNumberCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceNumberCounterRepository extends JpaRepository<InvoiceNumberCounter, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM InvoiceNumberCounter c WHERE c.counterYear = :year")
    Optional<InvoiceNumberCounter> findByYearForUpdate(@Param("year") int year);
}
