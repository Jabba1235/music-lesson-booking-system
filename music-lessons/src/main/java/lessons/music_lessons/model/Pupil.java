package lessons.music_lessons.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Pupil(long matric, String name, int age, Map<String, Map<String, Integer>> instruments) {

    public Integer gradeFor(String instrument) {
        if (instruments == null) {
            return null;
        }
        return instruments.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(instrument))
                .map(entry -> entry.getValue().get("grade"))
                .filter(grade -> grade != null)
                .findFirst()
                .orElse(null);
    }
}