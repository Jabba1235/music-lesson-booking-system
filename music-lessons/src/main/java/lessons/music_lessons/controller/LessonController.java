package lessons.music_lessons.controller;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lessons.music_lessons.model.Booking;
import lessons.music_lessons.model.Slot;
import lessons.music_lessons.model.Tutor;
import lessons.music_lessons.service.BookingService;

@RestController
public class LessonController {

    private final BookingService bookingService;

    public LessonController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/tutors")
    public List<Tutor> getTutors() {
        return bookingService.getTutors();
    }

    @GetMapping("/tutors/{tutorId}/slots")
    public List<Slot> getSlots(@PathVariable String tutorId,
                               @RequestParam String date,
                               @RequestParam(required = false) String instrument,
                               @RequestParam(required = false) String matric) {
        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be in the format yyyy-mm-dd");
        }
        String normalisedInstrument = instrument == null ? null : instrument.toLowerCase();
        return bookingService.getAvailableSlots(tutorId, parsedDate, normalisedInstrument, matric);
    }

    @PostMapping("/bookings")
    public ResponseEntity<Booking> createBooking(@RequestBody Booking request) {
        Booking booking = bookingService.createBooking(request);
        return ResponseEntity.created(URI.create("/bookings/" + booking.id())).body(booking);
    }

    @GetMapping("/bookings")
    public List<Booking> getBookings(@RequestParam String matric, @RequestParam String name) {
        return bookingService.getBookingsFor(matric, name);
    }

    @GetMapping("/bookings/{id}")
    public Booking getBooking(@PathVariable int id, @RequestParam String matric) {
        return bookingService.getOwnedBooking(id, matric);
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Void> cancelBooking(@PathVariable int id, @RequestParam String matric) {
        bookingService.cancelBooking(id, matric);
        return ResponseEntity.noContent().build();
    }
}