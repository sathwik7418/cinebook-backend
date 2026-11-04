package com.cinebook.ticket;

import com.cinebook.booking.service.BookingService.Booking;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TicketPdfServiceTest {
    @Test void createsPdfWithDemoDisclosureAndOpaqueReference() {
        Booking booking = new Booking(); booking.bookingReference = "CB-A1B2C3D4";
        booking.status = "CONFIRMED"; booking.showDate = "2026-11-03"; booking.startTime = "19:30";
        booking.screen = "Screen 2"; booking.seats = List.of("B4", "B5"); booking.totalAmount = 500;
        byte[] pdf = new TicketPdfService().create(booking, "Example Film", "Example Cinema");
        String text = new String(pdf, StandardCharsets.US_ASCII);
        assertTrue(text.startsWith("%PDF-1.4"));
        assertTrue(text.contains("DEMO TICKET"));
        assertTrue(text.contains("CB-A1B2C3D4"));
        assertTrue(text.contains("B4, B5"));
        assertTrue(text.contains("%%EOF"));
    }
}
