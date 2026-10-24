package com.shah.ecommerce.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order,Integer> {

    @Query(value = "select nextval('order_reference_seq')", nativeQuery = true)
    Long nextReferenceNumber();
}
