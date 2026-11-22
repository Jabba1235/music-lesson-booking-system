package lessons.music_lessons.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lessons.music_lessons.model.Booking;
import lessons.music_lessons.model.Pupil;
import lessons.music_lessons.model.Slot;
import lessons.music_lessons.model.Tutor;

@Service
public class BookingService {

    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Europe/London");
    private static final int BOOKING_WINDOW_DAYS = 7;
    private static final LocalTime CLOSING_TIME = LocalTime.of(20, 0);
    private static final LocalTime UNDER_16_LATEST_START = LocalTime.of(18, 0);
    private static final int LESSON_MINUTES = 30;
    private static final int ADULT_AGE = 16;
    private static final String MATRIC_PATTERN = "\\d{6,}";
    private static final int[] ALL_LEVELS = {0, 99};
    private static final int[] BEGINNER_LEVELS = {0, 4};
    private static final int[] ADVANCED_LEVELS = {5, 10};


    private final List<Tutor> tutors = List.of(
            new Tutor("a", "Tutor A", Map.of("guitar", ALL_LEVELS,"oboe", BEGINNER_LEVELS, "piano", BEGINNER_LEVELS, "drums", ALL_LEVELS)),
            new Tutor("b", "Tutor B", Map.of("piano", ADVANCED_LEVELS, "violin", ALL_LEVELS, "trumpet", ALL_LEVELS, "oboe", ADVANCED_LEVELS, "bass", ALL_LEVELS)));

    private final PupilTrackerClient trackerClient;
    private final List<Booking> bookings = new ArrayList<>();
    private int nextBookingId = 1;

    public BookingService(PupilTrackerClient trackerClient) {
        this.trackerClient = trackerClient;
    }

    public List<Tutor> getTutors() {
        return tutors;
    }

    public List<Slot> getAvailableSlots(String tutorId, LocalDate date, String instrument, String matric) {
        Tutor tutor = findTutor(tutorId);
        if (instrument != null && !tutor.teaches(instrument)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, tutor.name() + " does not teach " + instrument);
        }
        checkBookableDate(date);
        LocalTime latestStart = LocalTime.MAX;
        if (matric != null && !matric.isBlank()) {
            checkMatricFormat(matric);
            Pupil pupil = trackerClient.findPupil(matric.trim());
            if (pupil.age() < ADULT_AGE) {
                latestStart = UNDER_16_LATEST_START;
            }
        }
        return freeSlots(tutor, date, latestStart);
    }

    public Booking createBooking(Booking request) {
        validateRequest(request);
        Tutor tutor = findTutor(request.tutorId().trim());
        String instrument = request.instrument().trim().toLowerCase();
        if (!tutor.teaches(instrument)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, tutor.name() + " does not teach " + instrument);
        }
        checkBookableSlot(request.date(), request.time());
        Pupil pupil = trackerClient.findPupil(request.matric().trim());
        checkPupilRules(pupil, request, tutor, instrument);
        return storeBooking(pupil, tutor, instrument, request.date(), request.time());
    }

    public List<Booking> getBookingsFor(String matric, String name) {
        checkMatricFormat(matric);
        Pupil pupil = trackerClient.findPupil(matric.trim());
        if (!pupil.name().equalsIgnoreCase(name.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Name does not match the pupil record");
        }
        String pupilMatric = String.valueOf(pupil.matric());
        Map<String, Pupil> knownPupils = new HashMap<>(Map.of(pupilMatric, pupil));
        return withGrades(selectBookings(booking -> booking.matric().equals(pupilMatric)), knownPupils);
    }

    public List<Booking> getBookingsOn(String tutorId, LocalDate date) {
        Tutor tutor = findTutor(tutorId);
        List<Booking> selected = selectBookings(
                booking -> booking.tutorId().equals(tutor.id()) && booking.date().equals(date));
        return withGrades(selected, new HashMap<>());
    }

    public synchronized Booking getOwnedBooking(int id, String matric) {
        Booking booking = bookings.stream()
                .filter(candidate -> candidate.id() == id)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such booking"));
        if (!booking.matric().equals(matric)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking belongs to another pupil");
        }
        return booking;
    }

    public synchronized void cancelBooking(int id, String matric) {
        bookings.remove(getOwnedBooking(id, matric));
    }

    private Tutor findTutor(String tutorId) {
        return tutors.stream()
                .filter(tutor -> tutor.id().equalsIgnoreCase(tutorId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such tutor"));
    }

    private synchronized List<Slot> freeSlots(Tutor tutor, LocalDate date, LocalTime latestStart) {
        List<Slot> slots = new ArrayList<>();
        LocalTime time = openingTime(date);
        while (time.isBefore(CLOSING_TIME) && !time.isAfter(latestStart)) {
            if (!hasStarted(date, time) && !isBooked(tutor.id(), date, time)) {
                slots.add(new Slot(date, time));
            }
            time = time.plusMinutes(LESSON_MINUTES);
        }
        return slots;
    }

    private synchronized List<Booking> selectBookings(Predicate<Booking> filter) {
        return bookings.stream()
                .filter(filter)
                .sorted(Comparator.comparing(Booking::date).thenComparing(Booking::time))
                .toList();
    }

    private List<Booking> withGrades(List<Booking> selected, Map<String, Pupil> knownPupils) {
        List<Booking> graded = new ArrayList<>();
        for (Booking booking : selected) {
            Integer grade = null;
            try {
                Pupil pupil = knownPupils.computeIfAbsent(booking.matric(), trackerClient::findPupil);
                grade = pupil.gradeFor(booking.instrument());
            } catch (ResponseStatusException exception) {
                grade = null;
            }
            graded.add(new Booking(booking.id(), booking.matric(), booking.name(), booking.age(), booking.tutorId(),
                    booking.instrument(), booking.date(), booking.time(), grade));
        }
        return graded;
    }

    private synchronized Booking storeBooking(Pupil pupil, Tutor tutor, String instrument, LocalDate date, LocalTime time) {
        String matric = String.valueOf(pupil.matric());
        if (isBooked(tutor.id(), date, time)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot already booked with " + tutor.name());
        }
        if (hasBookingOnDay(matric, date)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pupil already has a lesson that day");
        }
        Booking booking = new Booking(nextBookingId++, matric, pupil.name(), pupil.age(), tutor.id(), instrument,
                date, time, null);
        bookings.add(booking);
        return booking;
    }

    private void checkPupilRules(Pupil pupil, Booking request, Tutor tutor, String instrument) {
        if (!pupil.name().equalsIgnoreCase(request.name().trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Name does not match the pupil record");
        }
        Integer grade = pupil.gradeFor(instrument);
        if (grade == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pupil does not play that instrument");
        }
        if (!tutor.acceptsGrade(instrument, grade)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, tutor.name() + " does not teach that instrument at the pupil's grade");
        }
        if (pupil.age() < ADULT_AGE && request.time().isAfter(UNDER_16_LATEST_START)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pupils under 16 cannot book lessons after 6 PM");
        }
    }

    private void validateRequest(Booking request) {
        if (request == null || isBlank(request.matric()) || isBlank(request.name()) || isBlank(request.tutorId())
                || isBlank(request.instrument())
                || request.date() == null || request.time() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing booking details");
        }
        checkMatricFormat(request.matric());
    }

    private void checkMatricFormat(String matric) {
        if (matric == null || !matric.trim().matches(MATRIC_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Matriculation number must be at least 6 digits and contain no letters");
        }
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private void checkBookableDate(LocalDate date) {
        LocalDate today = LocalDate.now(SCHOOL_ZONE);
        if (date.isBefore(today) || date.isAfter(today.plusDays(BOOKING_WINDOW_DAYS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lessons can only be booked from today up to " + BOOKING_WINDOW_DAYS + " days ahead");
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No lessons on Sundays");
        }
    }

    private void checkBookableSlot(LocalDate date, LocalTime time) {
        checkBookableDate(date);
        boolean onSlotBoundary = time.getSecond() == 0 && time.getNano() == 0
                && time.getMinute() % LESSON_MINUTES == 0;
        boolean insideOpeningHours = !time.isBefore(openingTime(date)) && time.isBefore(CLOSING_TIME);
        if (!onSlotBoundary || !insideOpeningHours) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a lesson slot");
        }
        if (hasStarted(date, time)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That slot has already started");
        }
    }

    private boolean hasStarted(LocalDate date, LocalTime time) {
        return !LocalDateTime.of(date, time).isAfter(LocalDateTime.now(SCHOOL_ZONE));
    }

    private LocalTime openingTime(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return LocalTime.of(9, 0);
        }
        return LocalTime.of(16, 0);
    }

    private boolean isBooked(String tutorId, LocalDate date, LocalTime time) {
        return bookings.stream()
                .anyMatch(booking -> booking.tutorId().equals(tutorId)
                        && booking.date().equals(date) && booking.time().equals(time));
    }

    private boolean hasBookingOnDay(String matric, LocalDate date) {
        return bookings.stream()
                .anyMatch(booking -> booking.matric().equals(matric) && booking.date().equals(date));
    }
}