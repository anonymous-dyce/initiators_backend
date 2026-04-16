package com.open.spring.mvc.grades;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignment_gradingsystem")
public class AssignmentController {

    @Autowired
    private AssignmentJpaRepository_gradingsystem assignmentRepository;

    // GET all assignments
    @GetMapping
    public List<Assignment> getAllAssignments() {
        return assignmentRepository.findAll();
    }

    // GET assignment by ID
    @GetMapping("/{id}")
    public ResponseEntity<Assignment> getAssignmentById(@PathVariable Long id) {
        Optional<Assignment> assignment = assignmentRepository.findById(id);
        return assignment.map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
    }

    // GET assignments by trimester
    @GetMapping("/trimester/{trimester}")
    public List<Assignment> getAssignmentsByTrimester(@PathVariable int trimester) {
        return assignmentRepository.findByTrimester(trimester);
    }

    // POST - create a new assignment
    // Expected JSON body:
    // {
    //   "name": "Homework 1",
    //   "trimester": 1,
    //   "dueDate": "2025-04-01",
    //   "pointsWorth": 100.0
    // }
    @PostMapping
    public ResponseEntity<Assignment> createAssignment(@RequestBody Assignment assignment) {
        Assignment saved = assignmentRepository.save(assignment);
        return ResponseEntity.ok(saved);
    }

    // PUT - update an existing assignment
    @PutMapping("/{id}")
    public ResponseEntity<Assignment> updateAssignment(@PathVariable Long id,
                                                       @RequestBody Assignment details) {
        Optional<Assignment> existing = assignmentRepository.findById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();

        Assignment a = existing.get();
        a.setName(details.getName());
        a.setTrimester(details.getTrimester());
        a.setDueDate(details.getDueDate());
        a.setPointsWorth(details.getPointsWorth());

        return ResponseEntity.ok(assignmentRepository.save(a));
    }

    // DELETE - remove an assignment
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        if (!assignmentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        assignmentRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
