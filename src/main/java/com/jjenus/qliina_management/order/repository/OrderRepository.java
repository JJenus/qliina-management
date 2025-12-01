package com.jjenus.qliina_management.order.repository;

import com.jjenus.qliina_management.order.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByTrackingNumber(String trackingNumber);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdBy = :employeeId AND FUNCTION('DATE', o.createdAt) = :date")
    Integer countByEmployeeIdAndDate(@Param("employeeId") UUID employeeId,
                                     @Param("date") LocalDate date);

    @Query("SELECT SUM(oi.quantity) FROM OrderItem oi WHERE oi.order.createdBy = :employeeId AND FUNCTION('DATE', oi.order.createdAt) = :date")
    Integer countItemsByEmployeeIdAndDate(@Param("employeeId") UUID employeeId,
                                          @Param("date") LocalDate date);

    @Query("SELECT SUM(oi.quantity) FROM OrderItem oi WHERE oi.order.createdBy = :employeeId AND oi.order.createdAt BETWEEN :startDate AND :endDate")
    Integer countItemsByEmployeeIdAndDateRange(@Param("employeeId") UUID employeeId,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.createdBy = :employeeId AND o.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumRevenueByEmployeeIdAndDateRange(@Param("employeeId") UUID employeeId,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);

    @Query("SELECT o FROM Order o WHERE o.businessId = :businessId AND o.status NOT IN ('COMPLETED', 'ARCHIVED')")
    List<Order> findUnpaidOrders(@Param("businessId") UUID businessId);

    @Query("SELECT SUM(o.balanceDue) FROM Order o WHERE o.businessId = :businessId AND (:shopId IS NULL OR o.shopId = :shopId)")
    BigDecimal sumOutstandingBalance(@Param("businessId") UUID businessId,
                                     @Param("shopId") UUID shopId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.businessId = :businessId AND (:shopId IS NULL OR o.shopId = :shopId) AND o.status NOT IN ('COMPLETED', 'ARCHIVED')")
    Long countPendingOrders(@Param("businessId") UUID businessId,
                            @Param("shopId") UUID shopId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.businessId = :businessId AND (:shopId IS NULL OR o.shopId = :shopId) AND o.createdAt BETWEEN :startDate AND :endDate")
    Long countOrdersByDateRange(@Param("businessId") UUID businessId,
                                @Param("shopId") UUID shopId,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);

    @Query("SELECT o FROM Order o WHERE o.businessId = :businessId AND o.shopId = :shopId")
    Page<Order> findByBusinessIdAndShopId(@Param("businessId") UUID businessId,
                                          @Param("shopId") UUID shopId,
                                          Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.businessId = :businessId")
    Page<Order> findByBusinessId(@Param("businessId") UUID businessId,
                                 Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.businessId = :businessId AND o.status = :status")
    Page<Order> findByStatus(@Param("businessId") UUID businessId,
                             @Param("status") Order.OrderStatus status,
                             Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.businessId = :businessId AND o.createdAt BETWEEN :startDate AND :endDate")
    Page<Order> findByDateRange(@Param("businessId") UUID businessId,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate,
                                Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId AND o.invoiceId IS NULL AND o.createdAt BETWEEN :startDate AND :endDate")
    List<Order> findUnpaidOrdersByCustomerAndDateRange(@Param("customerId") UUID customerId,
                                                       @Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.businessId = :businessId AND o.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumRevenueByDateRange(@Param("businessId") UUID businessId,
                                     @Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.businessId = :businessId AND FUNCTION('DATE', o.createdAt) = CURRENT_DATE")
    Long countTodayOrders(@Param("businessId") UUID businessId);

    @Query("SELECT o.customerId, SUM(o.totalAmount) as total, COUNT(o) as count, AVG(o.totalAmount) as avg " +
            "FROM Order o WHERE o.businessId = :businessId AND o.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY o.customerId ORDER BY total DESC")
    Page<Object[]> findTopCustomersBySpend(@Param("businessId") UUID businessId,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate,
                                           Pageable pageable);

    @Query("SELECT o.customerId, COUNT(o) as count, SUM(o.totalAmount) as total " +
            "FROM Order o WHERE o.businessId = :businessId AND o.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY o.customerId ORDER BY count DESC")
    Page<Object[]> findTopCustomersByFrequency(@Param("businessId") UUID businessId,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate,
                                               Pageable pageable);

    @Query("SELECT o.customerId, AVG(o.totalAmount) as avg, COUNT(o) as count, SUM(o.totalAmount) as total " +
            "FROM Order o WHERE o.businessId = :businessId AND o.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY o.customerId ORDER BY avg DESC")
    Page<Object[]> findTopCustomersByAOV(@Param("businessId") UUID businessId,
                                         @Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate,
                                         Pageable pageable);

    @Query("SELECT FUNCTION('DATE', o.createdAt), COUNT(o), SUM(o.totalAmount) " +
            "FROM Order o WHERE o.businessId = :businessId AND o.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY FUNCTION('DATE', o.createdAt) ORDER BY FUNCTION('DATE', o.createdAt)")
    List<Object[]> getDailyOrderSummary(@Param("businessId") UUID businessId,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT FUNCTION('HOUR', o.createdAt), COUNT(o) " +
            "FROM Order o WHERE o.businessId = :businessId AND FUNCTION('DATE', o.createdAt) = :date " +
            "GROUP BY FUNCTION('HOUR', o.createdAt) ORDER BY FUNCTION('HOUR', o.createdAt)")
    List<Object[]> getHourlyDistribution(@Param("businessId") UUID businessId,
                                         @Param("date") LocalDateTime date);

    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId")
    List<Order> findByCustomerId(@Param("customerId") UUID customerId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdBy = :employeeId AND o.createdAt BETWEEN :startDate AND :endDate")
    Integer countByEmployeeIdAndDateRange(@Param("employeeId") UUID employeeId,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);
}