package com.open.spring.mvc.Make_debugger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

@Service
public class make_debug {

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    /**
     * Analyzes Jekyll build logs using Gemini AI
     * @param log The log content to analyze
     * @return Analysis result from Gemini
     */
    public String analyzeLog(String log) {
        try {
            // ✅ Safety check for API key
            if (geminiApiKey == null || geminiApiKey.isEmpty()) {
                return "Gemini API key not configured.";
            }

            Client client = Client.builder().apiKey(geminiApiKey).build();

            String systemPrompt = """
You are an AI assistant that analyzes Jekyll build logs.

Your job:
1. Determine whether the build succeeded or failed.
2. If it failed, identify the most likely cause.
3. Recommend what a student should do to fix the build or Makefile.
4. Do not print the full log contents.
5. Keep the answer concise and actionable.
""";

            // Truncate log to prevent huge inputs
            if (log.length() > 8000) {
                log = log.substring(log.length() - 8000);
            }

            String prompt = systemPrompt + "\n\n" + log;

            GenerateContentResponse resp =
                client.models.generateContent("models/gemini-2.5-flash", prompt, null);

            String result = resp.text().trim();

            // ✅ PRINT TO TERMINAL (important)
            System.out.println("\n===== GEMINI ANALYSIS =====");
            System.out.println(result);
            System.out.println("===========================\n");

            return result;

        } catch (Exception e) {
            return "AI analysis failed: " + e.getMessage();
        }
    }
}
