package com.open.spring.mvc.slack;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar/issues")
@CrossOrigin(origins = { "http://127.0.0.1:4500", "https://pages.opencodingsociety.com" }, allowCredentials = "true")
public class CalendarIssueController {

    @Autowired
    private CalendarIssueService calendarIssueService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getIssues(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String dueDate,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        LocalDate parsedDueDate = null;
        if (dueDate != null && !dueDate.isBlank()) {
            parsedDueDate = LocalDate.parse(dueDate);
        }

        String requesterUid = userDetails.getUsername();
        boolean privileged = hasPrivilegedRole(userDetails);

        List<Map<String, Object>> data = calendarIssueService
                .getIssues(status, priority, parsedDueDate, eventId, q, requesterUid, privileged)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(data);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getIssueById(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required"));
        }

        return calendarIssueService.getIssueById(id, userDetails.getUsername(), hasPrivilegedRole(userDetails))
                .<ResponseEntity<?>>map(issue -> ResponseEntity.ok(toResponse(issue)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Issue not found")));
    }

    @PostMapping
    public ResponseEntity<?> createIssue(@RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required"));
        }

        try {
            CalendarIssue issue = fromPayload(payload);
            CalendarIssue saved = calendarIssueService.createIssue(issue, userDetails.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unable to create issue", "error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateIssue(@PathVariable Long id, @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required"));
        }

        try {
            CalendarIssue issue = fromPayload(payload);
            return calendarIssueService.updateIssue(id, issue, userDetails.getUsername(), hasPrivilegedRole(userDetails))
                    .<ResponseEntity<?>>map(updated -> ResponseEntity.ok(toResponse(updated)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("message", "Issue not found")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unable to update issue", "error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required"));
        }

        try {
            String statusValue = payload.get("status");
            CalendarIssueStatus status = parseStatus(statusValue);
            return calendarIssueService.updateIssueStatus(id, status, userDetails.getUsername(),
                            hasPrivilegedRole(userDetails))
                    .<ResponseEntity<?>>map(updated -> ResponseEntity.ok(toResponse(updated)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("message", "Issue not found")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteIssue(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required"));
        }

        if (!calendarIssueService.deleteIssue(id, userDetails.getUsername())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Issue not found"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    private CalendarIssue fromPayload(Map<String, Object> payload) {
        CalendarIssue issue = new CalendarIssue();
        issue.setTitle(trimToNull((String) payload.get("title")));
        issue.setDescription((String) payload.getOrDefault("description", ""));
        issue.setStatus(parseStatus((String) payload.get("status")));
        issue.setPriority(parsePriority((String) payload.get("priority")));

        String dueDate = (String) payload.get("dueDate");
        if (dueDate != null && !dueDate.isBlank()) {
            issue.setDueDate(LocalDate.parse(dueDate));
        }

        issue.setEventId(trimToNull((String) payload.get("eventId")));
        issue.setGroupName(trimToNull((String) payload.get("groupName")));
        issue.setTags(normalizeTags(payload.get("tags")));
        return issue;
    }

    private Map<String, Object> toResponse(CalendarIssue issue) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", issue.getId());
        data.put("title", issue.getTitle());
        data.put("description", issue.getDescription());
        data.put("author", issue.getOwnerUid());
        data.put("status", issue.getStatus() == null ? null : issue.getStatus().name().toLowerCase());
        data.put("priority", issue.getPriority() == null ? null : issue.getPriority().name().toLowerCase());
        data.put("dueDate", issue.getDueDate() == null ? null : issue.getDueDate().toString());
        data.put("eventId", issue.getEventId());
        data.put("groupName", issue.getGroupName());
        data.put("tags", parseTags(issue.getTags()));
        data.put("createdAt", issue.getCreatedAt() == null ? null : issue.getCreatedAt().toString());
        data.put("updatedAt", issue.getUpdatedAt() == null ? null : issue.getUpdatedAt().toString());
        return data;
    }

    private CalendarIssueStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return CalendarIssueStatus.OPEN;
        }
        return switch (raw.trim().toLowerCase()) {
            case "open" -> CalendarIssueStatus.OPEN;
            case "in-progress", "in_progress" -> CalendarIssueStatus.IN_PROGRESS;
            case "blocked" -> CalendarIssueStatus.BLOCKED;
            case "done" -> CalendarIssueStatus.DONE;
            default -> throw new IllegalArgumentException("Invalid status: " + raw);
        };
    }

    private CalendarIssuePriority parsePriority(String raw) {
        if (raw == null || raw.isBlank()) {
            return CalendarIssuePriority.MEDIUM;
        }
        return switch (raw.trim().toLowerCase()) {
            case "low" -> CalendarIssuePriority.LOW;
            case "medium" -> CalendarIssuePriority.MEDIUM;
            case "high" -> CalendarIssuePriority.HIGH;
            default -> throw new IllegalArgumentException("Invalid priority: " + raw);
        };
    }

    private String normalizeTags(Object rawTags) {
        if (rawTags == null) {
            return "";
        }

        if (rawTags instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.joining(","));
        }

        String tagsString = String.valueOf(rawTags);
        return Arrays.stream(tagsString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasPrivilegedRole(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_ADMIN".equals(role) || "ROLE_TEACHER".equals(role));
    }
}
