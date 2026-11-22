package lessons.music_lessons.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lessons.music_lessons.model.Pupil;

@Component
public class PupilTrackerClient {

    private static final String TRACKER_URL = "https://pmaier.eu.pythonanywhere.com/plt";
    private static final Duration CACHE_LIFETIME = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final Map<String, Pupil> testPupils = new HashMap<>();
    private final Map<String, Pupil> cachedPupils = new ConcurrentHashMap<>();
    private final Map<String, Instant> cachedAt = new ConcurrentHashMap<>();

    public PupilTrackerClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        restClient = RestClient.builder()
                .baseUrl(TRACKER_URL)
                .requestFactory(requestFactory)
                .build();
        loadTestPupils();
    }

    public Pupil findPupil(String matric) {
        Pupil testPupil = testPupils.get(matric);
        if (testPupil != null) {
            return testPupil;
        }
        Pupil cached = cachedPupils.get(matric);
        Instant storedAt = cachedAt.get(matric);
        if (cached != null && storedAt != null && Instant.now().isBefore(storedAt.plus(CACHE_LIFETIME))) {
            return cached;
        }
        try {
            Pupil pupil = fetchFromTracker(matric);
            cachedAt.put(matric, Instant.now());
            cachedPupils.put(matric, pupil);
            return pupil;
        } catch (HttpClientErrorException.NotFound exception) {
            cachedPupils.remove(matric);
            cachedAt.remove(matric);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No pupil record found for this matriculation number");
        } catch (RestClientException exception) {
            if (cached != null) {
                return cached;
            }
            throw failureFor(exception);
        }
    }

    private Pupil fetchFromTracker(String matric) {
        TrackerResponse response = restClient.get()
                .uri("/pupil/{matric}", matric)
                .retrieve()
                .body(TrackerResponse.class);
        if (response == null || response.pupil() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Pupil level tracker sent no pupil record");
        }
        return response.pupil();
    }

    private ResponseStatusException failureFor(RestClientException exception) {
        if (exception instanceof HttpClientErrorException.TooManyRequests) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Pupil level tracker is limiting requests, please try again shortly");
        }
        Throwable cause = exception.getMostSpecificCause();
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Pupil level tracker unavailable: " + cause.getClass().getSimpleName() + " " + cause.getMessage());
    }

    private void loadTestPupils() {
        ClassPathResource resource = new ClassPathResource("test-pupils.txt");
        if (!resource.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    Pupil pupil = parseTestPupil(line);
                    testPupils.put(String.valueOf(pupil.matric()), pupil);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read test-pupils.txt", exception);
        }
    }

    private Pupil parseTestPupil(String line) {
        String[] fields = line.split(",");
        Map<String, Map<String, Integer>> instruments = new HashMap<>();
        for (String entry : fields[3].split(";")) {
            String[] parts = entry.split(":");
            Integer grade = parts.length > 1 && !parts[1].isBlank() ? Integer.valueOf(parts[1].trim()) : null;
            Map<String, Integer> level = new HashMap<>();
            level.put("grade", grade);
            instruments.put(parts[0].trim().toLowerCase(), level);
        }
        return new Pupil(Long.parseLong(fields[0].trim()), fields[1].trim(),
                Integer.parseInt(fields[2].trim()), instruments);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TrackerResponse(Pupil pupil) {
    }
}