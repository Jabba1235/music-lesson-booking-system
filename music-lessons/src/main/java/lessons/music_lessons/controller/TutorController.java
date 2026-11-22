package lessons.music_lessons.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lessons.music_lessons.model.Booking;
import lessons.music_lessons.service.BookingService;

@RestController
public class TutorController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Map<String, String> TUTOR_API_KEYS = Map.of(
            "a", "4holXkzsIMZ3YDdvNCA5ay2tWGb0H6PFfgBEVxKU8Tj7SuOecn",
            "b", "D5lAn1NK8FOmXp6gfIjaUhRvrcEdJk4ixtZYGuV2BW7zewoCT0");

    private final BookingService bookingService;

    public TutorController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/tutors/{tutorId}/bookings")
    public List<Booking> getBookingsForDay(
            @PathVariable String tutorId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String date) {
        String keyOwner = tutorIdForKey(authorization);
        if (!keyOwner.equalsIgnoreCase(tutorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This key belongs to a different tutor");
        }
        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be in the format yyyy-mm-dd");
        }
        return bookingService.getBookingsOn(tutorId, parsedDate);
    }

    private String tutorIdForKey(String authorization) {
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String presentedKey = authorization.substring(BEARER_PREFIX.length());
            for (Map.Entry<String, String> entry : TUTOR_API_KEYS.entrySet()) {
                if (entry.getValue().equals(presentedKey)) {
                    return entry.getKey();
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid tutor API key required");
    }
}
