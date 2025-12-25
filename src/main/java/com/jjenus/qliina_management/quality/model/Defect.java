package com.jjenus.qliina_management.quality.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "defects")
@Getter
@Setter
public class Defect extends BaseEntity {

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "quality_check_id", nullable = false)
private QualityCheck qualityCheck;

@Column(name = "type", nullable = false)
private String type;

@Column(name = "severity", nullable = false)
private String severity;

@Column(name = "description")
private String description;

@Column(name = "location")
private String location;

@Column(name = "reported_by")
private UUID reportedBy;

@Column(name = "assigned_to")  
private UUID assignedTo;

@Column(name = "reported_at")
private LocalDateTime reportedAt;

@Column(name = "status")
private String status;

@Column(name = "resolution")
private String resolution;

@Column(name = "compensation", precision = 10, scale = 2)
private BigDecimal compensation;

@Column(name = "compensation_type")
private String compensationType;

@ElementCollection
@CollectionTable(name = "defect_images", joinColumns = @JoinColumn(name = "defect_id"))
@Column(name = "image_url")
private List < String > images = new ArrayList<>();
}