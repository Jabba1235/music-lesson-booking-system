package lessons.music_lessons.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record Booking(Integer id, String matric, String name, Integer age, String tutorId, String instrument,
                      LocalDate date, LocalTime time, Integer grade) {
}