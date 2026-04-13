package com.open.spring.mvc.grades;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentJpaRepository_gradingsystem extends JpaRepository<Assignment, Long> {
    List<Assignment> findByTrimester(int trimester);
    List<Assignment> findByName(String name);
}
