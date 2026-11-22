package lessons.music_lessons.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record Slot(LocalDate date, LocalTime time) {
}