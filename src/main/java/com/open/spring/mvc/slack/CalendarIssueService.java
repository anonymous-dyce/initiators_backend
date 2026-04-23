package com.open.spring.mvc.slack;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.open.spring.mvc.groups.Groups;
import com.open.spring.mvc.groups.GroupsJpaRepository;
import com.open.spring.mvc.person.Person;
import com.open.spring.mvc.person.PersonJpaRepository;

@Service
public class CalendarIssueService {

    @Autowired
    private CalendarIssueRepository calendarIssueRepository;

        @Autowired
        private PersonJpaRepository personJpaRepository;

        @Autowired
        private GroupsJpaRepository groupsJpaRepository;

    public List<CalendarIssue> getIssues(String status, String priority, LocalDate dueDate, String eventId, String q,
            String requesterUid, boolean privileged) {
        List<CalendarIssue> accessibleIssues = privileged
                ? calendarIssueRepository.findAll()
            : calendarIssueRepository.findAll().stream()
                .filter(issue -> canViewIssue(issue, requesterUid))
                .collect(Collectors.toList());

        return accessibleIssues.stream()
                .filter(issue -> matchesStatus(issue, status))
                .filter(issue -> matchesPriority(issue, priority))
                .filter(issue -> dueDate == null || dueDate.equals(issue.getDueDate()))
                .filter(issue -> eventId == null || eventId.isBlank() || eventId.equals(issue.getEventId()))
                .filter(issue -> matchesSearch(issue, q))
                .sorted(Comparator.comparing(CalendarIssue::getDueDate)
                        .thenComparing(CalendarIssue::getUpdatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Optional<CalendarIssue> getIssueById(Long id, String requesterUid, boolean privileged) {
        return findAccessibleIssue(id, requesterUid, privileged);
    }

    public CalendarIssue createIssue(CalendarIssue issue, String ownerUid) {
        validateIssue(issue);
        if (issue.getStatus() == null) {
            issue.setStatus(CalendarIssueStatus.OPEN);
        }
        if (issue.getPriority() == null) {
            issue.setPriority(CalendarIssuePriority.MEDIUM);
        }
        issue.setOwnerUid(ownerUid);
        return calendarIssueRepository.save(issue);
    }

    public Optional<CalendarIssue> updateIssue(Long id, CalendarIssue updated, String requesterUid, boolean privileged) {
        validateIssue(updated);

        return findAccessibleIssue(id, requesterUid, privileged).map(existing -> {
            CalendarIssueStatus nextStatus = updated.getStatus() != null ? updated.getStatus() : existing.getStatus();
            if (!isValidStatusTransition(existing.getStatus(), nextStatus)) {
                throw new IllegalArgumentException(
                        "Invalid status transition from " + existing.getStatus() + " to " + nextStatus);
            }

            existing.setTitle(updated.getTitle().trim());
            existing.setDescription(updated.getDescription());
            existing.setStatus(nextStatus);
            existing.setPriority(updated.getPriority() != null ? updated.getPriority() : existing.getPriority());
            existing.setDueDate(updated.getDueDate());
            existing.setEventId(updated.getEventId());
            existing.setGroupName(updated.getGroupName());
            existing.setTags(updated.getTags());
            return calendarIssueRepository.save(existing);
        });
    }

    public Optional<CalendarIssue> updateIssueStatus(Long id, CalendarIssueStatus nextStatus,
            String requesterUid, boolean privileged) {
        return findAccessibleIssue(id, requesterUid, privileged).map(existing -> {
            if (!isValidStatusTransition(existing.getStatus(), nextStatus)) {
                throw new IllegalArgumentException(
                        "Invalid status transition from " + existing.getStatus() + " to " + nextStatus);
            }
            existing.setStatus(nextStatus);
            return calendarIssueRepository.save(existing);
        });
    }

    public boolean deleteIssue(Long id, String requesterUid) {
        if (!calendarIssueRepository.existsByIdAndOwnerUid(id, requesterUid)) {
            return false;
        }
        calendarIssueRepository.deleteById(id);
        return true;
    }

    private Optional<CalendarIssue> findAccessibleIssue(Long id, String requesterUid, boolean privileged) {
        if (privileged) {
            return calendarIssueRepository.findById(id);
        }
        return calendarIssueRepository.findAll().stream()
                .filter(issue -> issue.getId() != null && issue.getId().equals(id))
                .filter(issue -> canViewIssue(issue, requesterUid))
                .findFirst();
    }

    private boolean canViewIssue(CalendarIssue issue, String requesterUid) {
        if (issue == null) {
            return false;
        }

        if (requesterUid == null || requesterUid.isBlank()) {
            return false;
        }

        if (requesterUid.equals(issue.getOwnerUid())) {
            return true;
        }

        String issueGroupName = issue.getGroupName();
        if (issueGroupName == null || issueGroupName.isBlank()) {
            return false;
        }

        Person requester = personJpaRepository.findByUid(requesterUid);
        if (requester == null) {
            return false;
        }

        Set<String> requesterGroupNames = new HashSet<>();
        for (Groups group : groupsJpaRepository.findGroupsByPersonIdWithMembers(requester.getId())) {
            if (group != null && group.getName() != null) {
                requesterGroupNames.add(group.getName());
            }
        }

        return requesterGroupNames.contains(issueGroupName);
    }

    private void validateIssue(CalendarIssue issue) {
        if (issue.getTitle() == null || issue.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (issue.getDueDate() == null) {
            throw new IllegalArgumentException("Due date is required");
        }
    }

    private boolean matchesStatus(CalendarIssue issue, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return issue.getStatus() != null && issue.getStatus().name().equalsIgnoreCase(status);
    }

    private boolean matchesPriority(CalendarIssue issue, String priority) {
        if (priority == null || priority.isBlank()) {
            return true;
        }
        return issue.getPriority() != null && issue.getPriority().name().equalsIgnoreCase(priority);
    }

    private boolean matchesSearch(CalendarIssue issue, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String query = q.toLowerCase(Locale.ROOT);
        String title = issue.getTitle() == null ? "" : issue.getTitle().toLowerCase(Locale.ROOT);
        String description = issue.getDescription() == null ? "" : issue.getDescription().toLowerCase(Locale.ROOT);
        String tags = issue.getTags() == null ? "" : issue.getTags().toLowerCase(Locale.ROOT);
        return title.contains(query) || description.contains(query) || tags.contains(query);
    }

    private boolean isValidStatusTransition(CalendarIssueStatus current, CalendarIssueStatus next) {
        if (current == null || next == null || current == next) {
            return true;
        }

        return switch (current) {
            case OPEN -> next == CalendarIssueStatus.IN_PROGRESS
                    || next == CalendarIssueStatus.BLOCKED
                    || next == CalendarIssueStatus.DONE;
            case IN_PROGRESS -> next == CalendarIssueStatus.OPEN
                    || next == CalendarIssueStatus.BLOCKED
                    || next == CalendarIssueStatus.DONE;
            case BLOCKED -> next == CalendarIssueStatus.OPEN
                    || next == CalendarIssueStatus.IN_PROGRESS
                    || next == CalendarIssueStatus.DONE;
            case DONE -> next == CalendarIssueStatus.OPEN;
        };
    }
}
