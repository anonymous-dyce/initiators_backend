package com.open.spring.mvc.slack;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarIssueRepository extends JpaRepository<CalendarIssue, Long> {
    List<CalendarIssue> findByDueDate(LocalDate dueDate);
    List<CalendarIssue> findByOwnerUid(String ownerUid);
    java.util.Optional<CalendarIssue> findByIdAndOwnerUid(Long id, String ownerUid);
    boolean existsByIdAndOwnerUid(Long id, String ownerUid);
}
