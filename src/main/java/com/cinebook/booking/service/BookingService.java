package com.cinebook.booking.service;

import com.cinebook.common.json.JsonLoaderService.ShowJson;
import com.cinebook.show.service.DynamicShowtimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;

/** Small JSON-backed booking store for the existing JSON-only demo deployment. */
@Service
public class BookingService {
    private static final Pattern SEAT_ID = Pattern.compile("[A-H](?:[1-9]|1[0-8])");
    private static final int SEAT_CAPACITY = 8 * 18;
    private static final Map<String, SnackProduct> SNACK_MENU = Map.of(
            "couple-combo", new SnackProduct("Couple Combo", 515),
            "festival-combo", new SnackProduct("Festival Combo", 630),
            "salted-popcorn", new SnackProduct("Salted Popcorn", 250),
            "cheese-popcorn", new SnackProduct("Cheese Popcorn", 280),
            "coke-650ml", new SnackProduct("Coke 650 ml", 220),
            "peri-peri-fries", new SnackProduct("Peri Peri French Fries", 210),
            "nachos", new SnackProduct("Classic Nachos", 240),
            "water", new SnackProduct("Bottled Water", 60)
    );
    private final Path storePath;
    private final DynamicShowtimeService shows;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public BookingService(@Value("${cinebook.storage.directory:./data}") String directory,
                          DynamicShowtimeService shows, Clock clock) {
        this.storePath = Path.of(directory).resolve("bookings.json");
        this.shows = shows;
        this.clock = clock;
    }

    public synchronized List<String> reservedSeats(long showId) {
        return readAll().stream().filter(b -> b.showId == showId && "CONFIRMED".equals(b.status))
                .flatMap(b -> b.seats.stream()).distinct().sorted().toList();
    }

    public synchronized int availableSeats(long showId) {
        return Math.max(0, SEAT_CAPACITY - reservedSeats(showId).size());
    }

    public synchronized Booking create(long userId, long showId, List<String> requestedSeats) {
        return create(userId, showId, requestedSeats, List.of());
    }

    public synchronized Booking create(long userId, long showId, List<String> requestedSeats,
                                       List<SnackSelection> requestedSnacks) {
        ShowJson show = activeShow(showId);
        if (requestedSeats == null || requestedSeats.isEmpty() || requestedSeats.size() > 6)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select between 1 and 6 seats.");
        if (requestedSeats.stream().anyMatch(seat -> seat == null || !SEAT_ID.matcher(seat).matches())
                || requestedSeats.stream().distinct().count() != requestedSeats.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat selection is invalid.");
        Set<String> alreadyReserved = new HashSet<>(reservedSeats(showId));
        if (requestedSeats.stream().anyMatch(alreadyReserved::contains))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "One or more selected seats are already reserved.");
        List<Booking> all = readAll();
        Booking booking = new Booking();
        booking.id = all.stream().mapToLong(b -> b.id).max().orElse(0) + 1;
        booking.userId = userId;
        booking.showId = showId;
        booking.movieId = show.movieId;
        booking.movieTitle = show.movieTitle;
        booking.cinemaId = show.cinemaId;
        booking.screen = show.screen;
        booking.showDate = show.date;
        booking.startTime = show.startTime;
        booking.seats = List.copyOf(requestedSeats);
        long subtotal = requestedSeats.stream().mapToLong(seat -> seatPrice(show.price, seat)).sum();
        List<SnackLine> snackLines = new ArrayList<>();
        Set<String> snackIds = new HashSet<>();
        long snackAmount = 0;
        if (requestedSnacks != null) {
            for (SnackSelection selection : requestedSnacks) {
                if (selection == null || selection.itemId == null || !SNACK_MENU.containsKey(selection.itemId)
                        || selection.quantity < 1 || selection.quantity > 12
                        || !snackIds.add(selection.itemId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Snack selection is invalid.");
                }
                SnackProduct product = SNACK_MENU.get(selection.itemId);
                SnackLine line = new SnackLine();
                line.itemId = selection.itemId;
                line.name = product.name;
                line.quantity = selection.quantity;
                line.unitPrice = product.price;
                line.subtotal = (long) product.price * selection.quantity;
                snackLines.add(line);
                snackAmount += line.subtotal;
            }
        }
        booking.snacks = List.copyOf(snackLines);
        booking.snackAmount = snackAmount;
        booking.totalAmount = subtotal + Math.round(subtotal * 0.09) + snackAmount;
        booking.status = "CONFIRMED";
        booking.bookingReference = "CB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        booking.bookingDate = Instant.now(clock).toString();
        all.add(booking);
        writeAll(all);
        return booking;
    }

    public synchronized Booking get(long id) {
        return readAll().stream().filter(b -> b.id == id).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found."));
    }

    public synchronized List<Booking> forUser(long userId) {
        return readAll().stream().filter(b -> b.userId == userId).toList();
    }

    public synchronized void cancel(long id, long userId) {
        List<Booking> all = readAll();
        Booking booking = all.stream().filter(b -> b.id == id && b.userId == userId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found."));
        if (!"CANCELLED".equals(booking.status)) {
            booking.status = "CANCELLED";
            writeAll(all);
        }
    }

    private ShowJson activeShow(long showId) {
        ShowJson show = shows.getShow(showId);
        if (show == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Show is not available for booking.");
        ZonedDateTime start = java.time.LocalDate.parse(show.date).atTime(java.time.LocalTime.parse(show.startTime))
                .atZone(DynamicShowtimeService.SCHEDULE_ZONE);
        if (!start.isAfter(ZonedDateTime.now(clock.withZone(DynamicShowtimeService.SCHEDULE_ZONE))))
            throw new ResponseStatusException(HttpStatus.GONE, "This show has already started.");
        return show;
    }

    private long seatPrice(int basePrice, String seatId) {
        int row = seatId.charAt(0) - 'A';
        double multiplier = row < 3 ? 1.0 : row < 6 ? 1.15 : 1.4;
        return Math.round(Math.round(basePrice * multiplier / 10.0) * 10.0);
    }

    private List<Booking> readAll() {
        if (!Files.exists(storePath)) return new ArrayList<>();
        try { return new ArrayList<>(Arrays.asList(mapper.readValue(storePath.toFile(), Booking[].class))); }
        catch (IOException error) { throw new IllegalStateException("Could not read booking store.", error); }
    }

    private void writeAll(List<Booking> bookings) {
        try {
            Files.createDirectories(storePath.toAbsolutePath().getParent());
            Path temp = storePath.resolveSibling(storePath.getFileName() + ".tmp");
            mapper.writeValue(temp.toFile(), bookings);
            try { Files.move(temp, storePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException unsupportedAtomicMove) { Files.move(temp, storePath, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) { throw new IllegalStateException("Could not persist booking.", error); }
    }

    public static class Booking {
        public long id;
        public long userId;
        public long showId;
        /** Show metadata snapshot keeps booking history readable after its rolling schedule expires. */
        public int movieId;
        /** Optional snapshot keeps CineTickets bookings readable without changing legacy IDs. */
        public String movieTitle;
        public int cinemaId;
        public String screen;
        public String showDate;
        public String startTime;
        public List<String> seats = List.of();
        public List<SnackLine> snacks = List.of();
        public long snackAmount;
        public long totalAmount;
        public String status;
        public String bookingReference;
        public String bookingDate;
    }

    public static class SnackSelection {
        public String itemId;
        public int quantity;
    }

    public static class SnackLine {
        public String itemId;
        public String name;
        public int quantity;
        public int unitPrice;
        public long subtotal;
    }

    private record SnackProduct(String name, int price) { }
}
