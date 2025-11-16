package com.jjenus.qliina_management.identity.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shops")
@Getter
@Setter
public class Shop extends BaseTenantEntity {
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Embedded
    private Address address;

    private String phone;

    private String email;

    private String timezone;

    private Boolean active = true;

    @ElementCollection
    @CollectionTable(name = "shop_operating_hours", joinColumns = @JoinColumn(name = "shop_id"))
    private List<OperatingHour> operatingHours = new ArrayList<>();
}
