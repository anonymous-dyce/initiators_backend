package com.open.spring.mvc.Make_debugger;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MakeDebuggerController {

    private static final Logger logger = LoggerFactory.getLogger(MakeDebuggerController.class);

    @Autowired
    private make_debug makeDebugService;

    /**
     * Analyzes Jekyll build logs using Gemini AI
     * @param requestBody JSON containing "log" field with the log content
     * @return ResponseEntity with analysis result
     */
    @PostMapping("/gemini/analyze-log")
    public ResponseEntity<Map<String, Object>> analyzeLog(@RequestBody Map<String, String> requestBody) {
        try {
            String log = requestBody.get("log");
            if (log == null || log.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Log field is required");
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
            }

            // Use the make_debug service to analyze the log
            String analysis = makeDebugService.analyzeLog(log);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("analysis", analysis);

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Error analyzing log", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Analysis failed: " + e.getMessage());
            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}