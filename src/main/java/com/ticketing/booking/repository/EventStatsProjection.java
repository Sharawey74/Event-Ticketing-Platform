package com.ticketing.booking.repository;

import java.math.BigDecimal;

public interface EventStatsProjection {
    Long getEventId();
    Long getTicketsSold();
    BigDecimal getGrossRevenue();
}
