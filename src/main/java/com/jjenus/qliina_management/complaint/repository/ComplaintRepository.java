package com.jjenus.qliina_management.complaint.repository;

import com.jjenus.qliina_management.complaint.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID>, JpaSpecificationExecutor<Complaint> {

    long countByBusinessId(UUID businessId);

    Optional<Complaint> findByIdAndBusinessId(UUID id, UUID businessId);
}