// src/main/java/com/jjenus/qliina_management/customer/repository/CustomerAddressRepository.java
package com.jjenus.qliina_management.customer.repository;

import com.jjenus.qliina_management.customer.model.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
    
    @Modifying
    @Query("UPDATE CustomerAddress a SET a.isDefault = false WHERE a.customer.id = :customerId")
    void resetDefaultAddress(@Param("customerId") UUID customerId);
}