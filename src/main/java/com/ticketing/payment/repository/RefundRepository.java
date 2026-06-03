package com.ticketing.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketing.payment.model.Refund;
import com.ticketing.payment.model.RefundStatus;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    /** Find all refunds linked to a payment record. */
    List<Refund> findByPaymentId(Long paymentId);

    /** Check if a refund in a given status already exists for a payment. */
    boolean existsByPaymentIdAndStatus(Long paymentId, RefundStatus status);
}
