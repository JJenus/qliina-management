
package com.jjenus.qliina_management.customer.repository;

import com.jjenus.qliina_management.customer.model.CustomerNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CustomerNoteRepository extends JpaRepository<CustomerNote, UUID> {
}