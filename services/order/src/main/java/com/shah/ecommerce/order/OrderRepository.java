package com.shah.ecommerce.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o.reference FROM Order o WHERE o.reference LIKE 'ORD-%'")
    List<String> findAllOrderReferencesForUpdate();
}
