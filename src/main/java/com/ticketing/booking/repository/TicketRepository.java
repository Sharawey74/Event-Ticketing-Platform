package com.ticketing.booking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketing.booking.model.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByBookingId(Long bookingId);

    @Modifying
    @Query("UPDATE Ticket t SET t.qrCode = :qrCode WHERE t.id = :ticketId")
    int updateQrCode(@Param("ticketId") Long ticketId, @Param("qrCode") String qrCode);
}
