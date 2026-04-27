package com.open.spring.mvc.Make_debugger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/make")
public class MakeDebugController {

    @Autowired
    private make_debug service;

    // ✅ OPTION A: Send raw text (simpler)
    @PostMapping("/analyze")
    public String analyzeLog(@RequestBody String log) {
        return service.analyzeLog(log);
    }

    // ✅ OPTION B: Send file (.log, .txt, etc.)
    @PostMapping("/upload")
    public String analyzeFile(@RequestParam("file") MultipartFile file) {
        try {
            String content = new String(file.getBytes());
            return service.analyzeLog(content);
        } catch (Exception e) {
            return "File processing failed: " + e.getMessage();
        }
    }
}