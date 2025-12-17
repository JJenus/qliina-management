package com.jjenus.qliina_management.payment.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cash_drawer_sessions")
@Getter
@Setter
public class CashDrawerSession extends BaseTenantEntity {
    
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;
    
    @Column(name = "opened_by", nullable = false)
    private UUID openedBy;
    
    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;
    
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    
    @Column(name = "closed_by")
    private UUID closedBy;
    
    @Column(name = "starting_cash", nullable = false, precision = 10, scale = 2)
    private BigDecimal startingCash;
    
    @Column(name = "expected_closing_cash", precision = 10, scale = 2)
    private BigDecimal expectedClosingCash;
    
    @Column(name = "actual_closing_cash", precision = 10, scale = 2)
    private BigDecimal actualClosingCash;
    
    @Column(name = "difference", precision = 10, scale = 2)
    private BigDecimal difference;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "notes")
    private String notes;
}