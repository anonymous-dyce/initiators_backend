package com.open.spring.mvc.grades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.client.RestTemplate;

import com.open.spring.mvc.person.PersonJpaRepository;

@RestController
@RequestMapping("/api/grades")
public class GradeController {

    @Autowired
    private PersonJpaRepository personRepository;
    @Value("${gist.token:}")
    private String gistToken;

    @GetMapping
    public List<Grade> getAllGrades() {
        List<Grade> out = new ArrayList<>();
        for (com.open.spring.mvc.person.Person p : personRepository.findAll()) {
            List<Map<String, Object>> gjs = p.getGradesJson();
            if (gjs == null) continue;
            for (Map<String, Object> m : gjs) {
                Grade g = mapToGrade(m);
                if (g.getUid() == null) g.setUid(p.getUid());
                out.add(g);
            }
        }
        return out;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Grade> getGradeById(@PathVariable Long id) {
        for (com.open.spring.mvc.person.Person p : personRepository.findAll()) {
            List<Map<String, Object>> gjs = p.getGradesJson();
            if (gjs == null) continue;
            for (Map<String, Object> m : gjs) {
                Object mid = m.get("id");
                if (mid != null && Long.valueOf(String.valueOf(mid)).equals(id)) {
                    Grade g = mapToGrade(m);
                    if (g.getUid() == null) g.setUid(p.getUid());
                    return ResponseEntity.ok(g);
                }
            }
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/student/{uid}")
    public List<Grade> getGradesByStudent(@PathVariable String uid) {
        com.open.spring.mvc.person.Person p = personRepository.findByUid(uid);
        List<Grade> out = new ArrayList<>();
        if (p == null) return out;
        List<Map<String, Object>> gjs = p.getGradesJson();
        if (gjs == null) return out;
        for (Map<String, Object> m : gjs) {
            Grade g = mapToGrade(m);
            if (g.getUid() == null) g.setUid(uid);
            out.add(g);
        }
        return out;
    }

    @GetMapping("/assignment/{assignment}")
    public List<Grade> getGradesByAssignment(@PathVariable String assignment) {
        List<Grade> out = new ArrayList<>();
        for (com.open.spring.mvc.person.Person p : personRepository.findAll()) {
            List<Map<String, Object>> gjs = p.getGradesJson();
            if (gjs == null) continue;
            for (Map<String, Object> m : gjs) {
                Object asg = m.get("assignment");
                if (assignment.equals(String.valueOf(asg))) {
                    Grade g = mapToGrade(m);
                    if (g.getUid() == null) g.setUid(p.getUid());
                    out.add(g);
                }
            }
        }
        return out;
    }

    @PostMapping
    public Grade createGrade(@RequestBody Grade grade) {
        com.open.spring.mvc.person.Person p = personRepository.findByUid(grade.getUid());
        if (p == null) return null;
        Map<String, Object> m = new HashMap<>();

        Long newId = generateNextGradeId();
        m.put("id", newId);
        grade.setId(newId);

        m.put("uid", grade.getUid());
        m.put("assignment", grade.getAssignment());
        m.put("score", grade.getScore());
        m.put("teacherComments", grade.getTeacherComments());
        m.put("submission", grade.getSubmission());
        m.put("submittedAt", grade.getSubmittedAt());

        if (p.getGradesJson() == null) {
            p.setGradesJson(new ArrayList<>());
        }
        p.getGradesJson().add(m);
        personRepository.save(p);
        return grade;
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<Grade> submitGrade(@PathVariable Long id, @RequestBody Grade gradeDetails) {
        for (com.open.spring.mvc.person.Person p : personRepository.findAll()) {
            List<Map<String, Object>> gjs = p.getGradesJson();
            if (gjs == null) continue;
            for (Map<String, Object> m : gjs) {
                Object mid = m.get("id");
                if (mid != null && Long.valueOf(String.valueOf(mid)).equals(id)) {
                    if (gradeDetails.getUid() != null) m.put("uid", gradeDetails.getUid());
                    if (gradeDetails.getAssignment() != null) m.put("assignment", gradeDetails.getAssignment());
                    if (gradeDetails.getScore() != null) m.put("score", gradeDetails.getScore());
                    if (gradeDetails.getTeacherComments() != null) m.put("teacherComments", gradeDetails.getTeacherComments());
                    if (gradeDetails.getSubmission() != null) m.put("submission", gradeDetails.getSubmission());
                    if (gradeDetails.getSubmittedAt() != null) m.put("submittedAt", gradeDetails.getSubmittedAt());
                    personRepository.save(p);
                    Grade g = mapToGrade(m);
                    if (g.getUid() == null) g.setUid(p.getUid());
                    return ResponseEntity.ok(g);
                }
            }
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/create-gist")
    @PreAuthorize("permitAll()")   // ← ADD THIS LINE
    public ResponseEntity<Map<String, Object>> createGist(@RequestBody Map<String, Object> body) {
        if (gistToken == null || gistToken.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Gist token not configured on server");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }

        String description = (String) body.getOrDefault("description",
                "Exported AP CSA FRQs / Challenges from Open Coding Society");

        @SuppressWarnings("unchecked")
        Map<String, Object> files = (Map<String, Object>) body.get("files");

        if (files == null || files.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "No files provided");
            return ResponseEntity.badRequest().body(error);
        }

        // Build GitHub payload
        Map<String, Object> gistPayload = new HashMap<>();
        gistPayload.put("description", description);
        gistPayload.put("public", true);
        gistPayload.put("files", files);

        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github+json");
            headers.set("Authorization", "Bearer " + gistToken);
            headers.set("X-GitHub-Api-Version", "2022-11-28");
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(gistPayload, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.github.com/gists",
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> data = response.getBody();
                String url = (String) data.get("html_url");

                Map<String, Object> success = new HashMap<>();
                success.put("success", true);
                success.put("url", url);
                return ResponseEntity.ok(success);
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "GitHub API error: " + response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).body(error);
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to create Gist: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Grade> updateGrade(@PathVariable Long id, @RequestBody Grade gradeDetails) {
        for (com.open.spring.mvc.person.Person p : personRepository.findAll()) {
            List<Map<String, Object>> gjs = p.getGradesJson();
            if (gjs == null) continue;
            for (Map<String, Object> m : gjs) {
                Object mid = m.get("id");
                if (mid != null && Long.valueOf(String.valueOf(mid)).equals(id)) {
                    m.put("assignment", gradeDetails.getAssignment());
                    m.put("score", gradeDetails.getScore());
                    m.put("teacherComments", gradeDetails.getTeacherComments());
                    m.put("submission", gradeDetails.getSubmission());
                    m.put("submittedAt", gradeDetails.getSubmittedAt());
                    personRepository.save(p);
                    Grade g = mapToGrade(m);
                    if (g.getUid() == null) g.setUid(p.getUid());
                    return ResponseEntity.ok(g);
                }
            }
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGrade(@PathVariable Long id) {
        for (com.open.spring.mvc.person.Person p : personRepository.findAll()) {
            List<Map<String, Object>> gjs = p.getGradesJson();
            if (gjs == null) continue;
            boolean removed = gjs.removeIf(m -> {
                Object mid = m.get("id");
                return mid != null && Long.valueOf(String.valueOf(mid)).equals(id);
            });
            if (removed) {
                personRepository.save(p);
                return ResponseEntity.ok().build();
            }
        }
        return ResponseEntity.notFound().build();
    }

    private synchronized Long generateNextGradeId() {
        Long maxId = 0L;
        for (com.open.spring.mvc.person.Person p : personRepository.findAll()) {
            List<Map<String, Object>> gjs = p.getGradesJson();
            if (gjs == null) continue;
            for (Map<String, Object> m : gjs) {
                Object mid = m.get("id");
                if (mid != null) {
                    Long id = Long.valueOf(String.valueOf(mid));
                    if (id > maxId) {
                        maxId = id;
                    }
                }
            }
        }
        return maxId + 1;
    }

    private Grade mapToGrade(Map<String, Object> m) {
        if (m == null) return null;
        Object idObj = m.get("id");
        Long id = idObj == null ? null : Long.valueOf(String.valueOf(idObj));
        String uid = (String) m.get("uid");
        String assignment = m.get("assignment") == null ? null : String.valueOf(m.get("assignment"));
        Double score = null;
        Object scoreObj = m.get("score");
        if (scoreObj != null) {
            try {
                score = Double.valueOf(String.valueOf(scoreObj));
            } catch (NumberFormatException e) {
                score = null;
            }
        }
        String teacherComments = m.get("teacherComments") == null ? null : String.valueOf(m.get("teacherComments"));
        Object submission = m.get("submission");
        String submittedAt = m.get("submittedAt") == null ? null : String.valueOf(m.get("submittedAt"));
        Grade g = new Grade(uid, assignment, score, teacherComments, submission, submittedAt);
        g.setId(id);
        return g;
    }
}