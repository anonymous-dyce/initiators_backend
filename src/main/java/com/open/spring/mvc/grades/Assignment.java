package com.open.spring.mvc.grades;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "GradesAssignment")
@Table(name = "assignments")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;
    private int trimester;
    private LocalDate dueDate;
    private double pointsWorth;

    public Assignment() {}

    public Assignment(String name, int trimester, LocalDate dueDate, double pointsWorth) {
        this.name = name;
        this.trimester = trimester;
        this.dueDate = dueDate;
        this.pointsWorth = pointsWorth;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getTrimester() { return trimester; }
    public void setTrimester(int trimester) { this.trimester = trimester; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public double getPointsWorth() { return pointsWorth; }
    public void setPointsWorth(double pointsWorth) { this.pointsWorth = pointsWorth; }
}
