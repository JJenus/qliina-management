package com.jjenus.qliina_management.identity.repository;

import com.jjenus.qliina_management.identity.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByName(String name);
    
    List<Permission> findByCategory(String category);
    
    List<Permission> findByIsDefaultTrue();
    
    List<Permission> findByScope(Permission.PermissionScope scope);
    
    @Query("SELECT p FROM Permission p WHERE p.scope IN :scopes")
    List<Permission> findByScopeIn(@Param("scopes") List<Permission.PermissionScope> scopes);
    
    @Query("SELECT p FROM Permission p WHERE p.name IN :names")
    List<Permission> findByNameIn(@Param("names") List<String> names);
}
