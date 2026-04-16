package com.open.spring.mvc.assignments;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.open.spring.mvc.person.Person;
import com.open.spring.mvc.synergy.SynergyGrade;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Entity(name = "AssignmentEntity")
@Table(name = "assignment")
@Getter
@Setter
@NoArgsConstructor
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique=false)
    @NotEmpty
    private String name;

    @NotEmpty
    private String type;

    private String description;

    @NotEmpty
    private String dueDate;

    @NotEmpty
    private String timestamp;

    @OneToMany(mappedBy="assignment", cascade=CascadeType.ALL, orphanRemoval=true)
    @JsonIgnore
    private List<AssignmentSubmission> submissions;

    @ManyToMany
    @JoinTable(
        name = "assignment_person",
        joinColumns = @JoinColumn(name = "assignment_id"),
        inverseJoinColumns = @JoinColumn(name = "person_id")
    )
    private List<Person> assignedGraders;



    @OneToMany(mappedBy="assignment", cascade=CascadeType.ALL, orphanRemoval=true)
    @JsonIgnore
    private List<SynergyGrade> grades;

    @NotNull
    private Double points;

    // Optional assignment resource metadata linked to this assignment ID.
    private String resourceType;
    private String resourceUrl;
    private String resourceFilename;
    private String resourceStoragePath;

    private Long presentationLength;

    @Convert(converter = AssignmentQueueConverter.class)
    private AssignmentQueue assignmentQueue;

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void resetQueue() {
        assignmentQueue.reset();
    }

    // Initialize working list with all provided people
    public void initQueue(List<String> people, Long duration) {
        assignmentQueue.getWorking().addAll(people);
        presentationLength = duration;
    }

    // Add person to waiting and remove from working
    public void addQueue(String person) {
        assignmentQueue.getWorking().remove(person);
        assignmentQueue.getWaiting().add(person);
    }

    // Remove person from waiting and add to working
    public void removeQueue(String person) {
        assignmentQueue.getWaiting().remove(person);
        assignmentQueue.getWorking().add(person);
    }

    // Remove person from waiting and add to completed
    public void doneQueue(String person) {
        assignmentQueue.getWaiting().remove(person);
        assignmentQueue.getCompleted().add(person);
    }

    // Constructor.
    public Assignment(String name, String type, String description, Double points, String dueDate) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.points = points;
        this.dueDate = dueDate; 
        this.timestamp = LocalDateTime.now().format(formatter); // fixed formatting ahhh
        this.resourceType = "none";
        this.resourceUrl = null;
        this.resourceFilename = null;
        this.resourceStoragePath = null;
        // This line is not needed as converter will reset to null after it takes in an empty queue 
        // this.assignmentQueue = new AssignmentQueue();
    }

    public void setUrlResource(String url) {
        this.resourceType = "url";
        this.resourceUrl = url;
        this.resourceFilename = null;
        this.resourceStoragePath = null;
    }

    public void setUrlResource(String url, String uploaderUid) {
        this.resourceType = "url";
        this.resourceUrl = url;
        this.resourceFilename = null;
        this.resourceStoragePath =
            (uploaderUid == null || uploaderUid.isBlank()) ? null : uploaderUid + "/url-resource";
    }

    public void setFileResource(String originalFilename, String storagePath) {
        this.resourceType = "file";
        this.resourceFilename = originalFilename;
        this.resourceStoragePath = storagePath;
        this.resourceUrl = null;
    }

    public static Assignment[] init() {
        return new Assignment[] {
            new Assignment("Assignment 1", "Class Homework", "Unit 1 Homework", 1.0, "10/25/2024"),
            new Assignment("Sprint 1 Live Review", "Live Review", "The final review for sprint 1", 1.0, "11/2/2024"),
            new Assignment("Seed", "Seed", "The student's seed grade", 1.0, "11/2/2080"),
        };
    }

    public List<Person> getAssignedGraders() {
        return assignedGraders;
    }

    public void setAssignedGraders(List<com.open.spring.mvc.person.Person> persons) {
        this.assignedGraders = persons;
    }

    @Override
    public String toString(){
        return this.name;
    }

    public Long getId() {
        return id;
    }

    
    public String getName() {
        return name;
    }
    
    public String getType() {
        return type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getDueDate() {
        return dueDate;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public List<AssignmentSubmission> getSubmissions() {
        return submissions;
    }
    
    public List<SynergyGrade> getGrades() {
        return grades;
    }
    
    public Double getPoints() {
        return points;
    }
    
    public Long getPresentationLength() {
        return presentationLength;
    }
    
    public AssignmentQueue getAssignmentQueue() {
        return assignmentQueue;
    }

    public String formatTimestamp(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }
    
}
