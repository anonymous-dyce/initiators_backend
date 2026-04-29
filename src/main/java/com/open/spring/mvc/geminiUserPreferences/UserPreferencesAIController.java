package com.open.spring.mvc.geminiUserPreferences;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import io.github.cdimascio.dotenv.Dotenv;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@RestController
@RequestMapping("/api/upai")
public class UserPreferencesAIController {

    private final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private final String geminiApiKey = dotenv.get("GEMINI_API_KEY");
    private final String geminiApiUrl = dotenv.get("GEMINI_API_URL");
    
    private void validateApiConfig() throws IllegalStateException {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured in .env file");
        }
        if (geminiApiUrl == null || geminiApiUrl.isEmpty()) {
            throw new IllegalStateException("GEMINI_API_URL is not configured in .env file");
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThemeRequest {
        // The free-form description of the user's theme preferences
        private String prompt;
    }

    // POST - Generate a site theme recommendation from a user's preferences
    // Endpoint: POST /api/upai  (frontend posts to this root path)
    @PostMapping("")
    public ResponseEntity<?> gradeTheme(@RequestBody ThemeRequest request) {
        try {
            validateApiConfig();
            
            String prompt = request.getPrompt();

            if (prompt == null || prompt.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing prompt"));
            }

            // Build a clear instruction for Gemini to return only a compact JSON object
            String fullPrompt = String.format("""
                You are a design assistant that recommends a website theme based on a user's free-form preferences.
                The user may provide adjectives like \"modern\", \"playful\", \"professional\", preferred colors, shades, tones, tints, hues, and any other hints.
                Produce a single JSON object — and ONLY the JSON object — with the following keys:
                - backgroundColor: a hex color like \"#RRGGBB\" for the page background.
                - buttonColor: a hex color for primary buttons.
                - selectionColor: a hex color for selection/highlight states.
                - textColor: a hex color for primary text.
                - fontFamily: one of [\"inter\", \"open sans\", \"roboto\", \"lato\", \"montserrat\", \"georgia serif\", \"source code pro\"]
                - suggestions: a very short string (1-2 sentences) describing why these choices fit the user's request.

                Use HEX color codes. If a color family is requested (e.g., \"pastel blues\"), pick a representative HEX.
                Keep the JSON compact (no extra explanation). If you cannot decide between two fonts, pick the most suitable one from the allowed list.

                User preferences: %s
            """, prompt.replace("\"", "\\\"").replace("\n", " "));

            String jsonPayload = String.format("""
                {
                    "contents": [{
                        "parts": [{
                            "text": "%s"
                        }]
                    }]
                }
            """, fullPrompt.replace("\"", "\\\"").replace("\n", "\\n"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> httpRequest = new HttpEntity<>(jsonPayload, headers);
            String fullUrl = geminiApiUrl + "?key=" + geminiApiKey;

            RestTemplate restTemplate = new RestTemplate();
            String geminiBody = null;
            String extractedText = null;
            try {
                ResponseEntity<String> response = restTemplate.exchange(fullUrl, HttpMethod.POST, httpRequest, String.class);
                geminiBody = response.getBody();
                extractedText = extractGradingText(geminiBody);
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                // Provider quota exhausted. Log and provide a deterministic fallback so frontend can continue testing.
                e.printStackTrace();
                String fallback = "{\"backgroundColor\":\"#FFFFFF\",\"buttonColor\":\"#1F8EF1\",\"selectionColor\":\"#DDEEFF\",\"textColor\":\"#111827\",\"fontFamily\":\"inter\",\"suggestions\":\"Failed to generate theme due to quota limit. When you click on 'Apply to Form', a fallback theme will be implemented.\"}";
                extractedText = fallback;
                geminiBody = "";
            }

            // Try to parse extractedText as JSON; if it's wrapped in text, try to find the JSON substring
            ObjectMapper mapper = new ObjectMapper();
            Object responseObject = null;
            try {
                responseObject = mapper.readValue(extractedText, Map.class);
            } catch (Exception e) {
                // Attempt to locate first '{' and last '}' and parse substring
                int first = extractedText.indexOf('{');
                int last = extractedText.lastIndexOf('}');
                if (first >= 0 && last > first) {
                    String maybeJson = extractedText.substring(first, last + 1);
                    try {
                        responseObject = mapper.readValue(maybeJson, Map.class);
                        // Use the cleaned JSON text as extractedText
                        extractedText = maybeJson;
                    } catch (Exception ex) {
                        responseObject = extractedText;
                    }
                } else {
                    responseObject = extractedText;
                }
            }

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "prompt", prompt,
                "response", responseObject
            ));

        } catch (HttpClientErrorException.TooManyRequests e) {
            return ResponseEntity.status(429).body(Map.of("error", "Gemini quota exceeded. Please try again later."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Configuration error: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    // Helper method to extract grading text from Gemini API response
    private String extractGradingText(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            
            // Navigate: candidates[0].content.parts[0].text
            JsonNode textNode = root.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text");
            
            if (textNode.isTextual()) {
                return textNode.asText();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return "Error parsing grading result";
    }
}
