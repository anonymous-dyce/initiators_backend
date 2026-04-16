package com.open.spring.mvc.grades;

public class Grade {
    private Long id;
    private String uid;
    private String assignment;
    private Double score;
    private String teacherComments;
    private Object submission;
    private String submittedAt;

    public Grade() {
    }

    public Grade(String uid, String assignment, Double score, String teacherComments, Object submission) {
        this.uid = uid;
        this.assignment = assignment;
        this.score = score;
        this.teacherComments = teacherComments;
        this.submission = submission;
    }

    public Grade(String uid, String assignment, Double score, String teacherComments, Object submission, String submittedAt) {
        this.uid = uid;
        this.assignment = assignment;
        this.score = score;
        this.teacherComments = teacherComments;
        this.submission = submission;
        this.submittedAt = submittedAt;
    }

    // Getters and setters (must have all of these)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getAssignment() { return assignment; }
    public void setAssignment(String assignment) { this.assignment = assignment; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getTeacherComments() { return teacherComments; }
    public void setTeacherComments(String teacherComments) { this.teacherComments = teacherComments; }

    public Object getSubmission() { return submission; }
    public void setSubmission(Object submission) { this.submission = submission; }

    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
}