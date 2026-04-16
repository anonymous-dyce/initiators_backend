package com.open.spring.mvc.assignments;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignmentSubmissionsDTO {
    private Long id;
    private Map<String, Object> content;
    private String comment;

    public AssignmentSubmissionsDTO(AssignmentSubmission submission) {
        this.id = submission.getId();
        this.content = submission.getContent();
        this.comment = submission.getComment();
    }
}