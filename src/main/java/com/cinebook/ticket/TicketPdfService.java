package com.cinebook.ticket;

import com.cinebook.booking.service.BookingService.Booking;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free, one-page printable demo ticket PDF. */
@Service
public class TicketPdfService {
    public byte[] create(Booking booking, String movie, String cinema) {
        List<String> lines = new ArrayList<>();
        lines.add("CINEBOOK  |  DEMO TICKET");
        lines.add("This reservation is a demonstration and is not valid for cinema admission.");
        lines.add("Booking reference: " + safe(booking.bookingReference));
        lines.add("Status: " + safe(booking.status));
        lines.add("Movie: " + safe(movie));
        lines.add("Cinema: " + safe(cinema));
        lines.add("Screen: " + safe(booking.screen));
        lines.add("Show: " + safe(booking.showDate) + " at " + safe(booking.startTime));
        lines.add("Seats: " + safe(String.join(", ", booking.seats == null ? List.of() : booking.seats)));
        lines.add("Tickets: " + (booking.seats == null ? 0 : booking.seats.size()));
        lines.add("Amount: INR " + booking.totalAmount);
        lines.add("Ticket reference: " + safe(booking.bookingReference));

        StringBuilder content = new StringBuilder("BT\n/F1 20 Tf\n50 790 Td\n");
        content.append("0.20 0.48 0.90 rg\n(").append(escape(lines.get(0))).append(") Tj\n");
        content.append("0 0 0 rg\n/F1 10 Tf\n0 -28 Td\n");
        for (int i = 1; i < lines.size(); i++) {
            content.append('(').append(escape(lines.get(i))).append(") Tj\n0 -24 Td\n");
        }
        content.append("ET\n");

        byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);
        List<byte[]> objects = List.of(
                bytes("<< /Type /Catalog /Pages 2 0 R >>"),
                bytes("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>"),
                bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
                concat(bytes("<< /Length " + stream.length + " >>\nstream\n"), stream, bytes("endstream"))
        );
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        write(pdf, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");
        List<Integer> offsets = new ArrayList<>(); offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.size()); write(pdf, (i + 1) + " 0 obj\n"); pdf.writeBytes(objects.get(i)); write(pdf, "\nendobj\n");
        }
        int xref = pdf.size(); write(pdf, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) write(pdf, String.format("%010d 00000 n \n", offsets.get(i)));
        write(pdf, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF");
        return pdf.toByteArray();
    }

    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    private static byte[] concat(byte[]... chunks) { ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] chunk : chunks) out.writeBytes(chunk); return out.toByteArray(); }
    private static void write(ByteArrayOutputStream out, String text) { out.writeBytes(bytes(text)); }
    private static String safe(String value) { return value == null ? "-" : value.replaceAll("[^\\x20-\\x7E]", "?"); }
    private static String escape(String value) { return safe(value).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)"); }
}
