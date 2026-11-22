package lessons.music_lessons.model;

import java.util.Map;

public record Tutor(String id, String name, Map<String, int[]> gradeRanges) {

    public boolean teaches(String instrument) {
        return gradeRanges.containsKey(instrument);
    }

    public boolean acceptsGrade(String instrument, int grade) {
        int[] range = gradeRanges.get(instrument);
        return range != null && grade >= range[0] && grade <= range[1];
    }
}