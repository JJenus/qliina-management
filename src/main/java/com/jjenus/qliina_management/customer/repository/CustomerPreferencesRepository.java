
package com.jjenus.qliina_management.customer.repository;

import com.jjenus.qliina_management.customer.model.CustomerPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CustomerPreferencesRepository extends JpaRepository<CustomerPreferences, UUID> {
}