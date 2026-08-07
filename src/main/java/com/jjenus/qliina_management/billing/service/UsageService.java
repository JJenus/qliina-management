package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.model.UsageRecord;
import com.jjenus.qliina_management.billing.repository.UsageRecordRepository;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Metered usage recording and aggregation (MD §2 usage_records).
 * Used for metered billing features (api_calls, storage_gb, …).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    private final UsageRecordRepository usageRecordRepository;

    @Transactional
    public UsageRecord record(UUID subscriptionId, String featureKey, BigDecimal quantity) {
        if (quantity == null || quantity.signum() < 0) {
            throw new BusinessException("Usage quantity must be non-negative", "INVALID_USAGE_QUANTITY");
        }
        UsageRecord record = UsageRecord.builder()
                .subscriptionId(subscriptionId)
                .featureKey(featureKey)
                .quantity(quantity)
                .recordedAt(LocalDateTime.now())
                .build();
        return usageRecordRepository.save(record);
    }

    /** Total usage of a metered feature within a billing period. */
    @Transactional(readOnly = true)
    public BigDecimal usageInPeriod(UUID subscriptionId, String featureKey,
                                    LocalDateTime from, LocalDateTime to) {
        return usageRecordRepository.sumUsage(subscriptionId, featureKey, from, to);
    }
}
