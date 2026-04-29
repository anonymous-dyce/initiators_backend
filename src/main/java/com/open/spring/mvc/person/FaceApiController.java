package com.open.spring.mvc.person;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@RestController
@RequestMapping("/api/face")
public class FaceApiController {
    private static final Logger logger = LoggerFactory.getLogger(FaceApiController.class);

    @Autowired
    private PersonJpaRepository repository;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @Setter
    public static class FaceDto {
        private String faceData;
    }

    /**
     * Registration for authenticated users to save their face data.
     */
    @PostMapping("/register")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> registerFace(Authentication authentication, @RequestBody FaceDto faceDto) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String uid = userDetails.getUsername();

        Person person = repository.findByUid(uid);
        if (person == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        if (faceDto.getFaceData() == null || faceDto.getFaceData().isEmpty()) {
            return new ResponseEntity<>("Face data is required", HttpStatus.BAD_REQUEST);
        }

        person.setFaceData(faceDto.getFaceData());
        repository.save(person);

        logger.info("Face data registered for user: {}", uid);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Face data registered successfully");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Restricted endpoint for teachers and admins to get all face data for matching.
     */
    @GetMapping("/faces")
    @PreAuthorize("hasAnyAuthority('ROLE_TEACHER', 'ROLE_ADMIN')")
    public ResponseEntity<List<Map<String, String>>> getFaces() {
        List<Person> people = repository.findByFaceDataIsNotNull();
        List<Map<String, String>> faces = new ArrayList<>();
        for (Person person : people) {
            Map<String, String> face = new HashMap<>();
            face.put("uid", person.getUid());
            face.put("name", person.getName());
            face.put("faceData", person.getFaceData());
            faces.add(face);
        }
        return new ResponseEntity<>(faces, HttpStatus.OK);
    }
}
