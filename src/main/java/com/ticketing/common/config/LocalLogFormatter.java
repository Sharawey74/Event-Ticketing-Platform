package com.ticketing.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatically parses the NDJSON log file into a formatted JSON array file
 * for easier developer viewing, running continuously in the background.
 */
@Component
@Profile({"local", "default"})
public class LocalLogFormatter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalLogFormatter.class);
    private final ObjectMapper mapper;

    public LocalLogFormatter() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread formatterThread = new Thread(() -> {
            Path input = Paths.get("logs/logging.json");
            Path output = Paths.get("logs/logging-pretty.json");
            long lastModified = 0;

            log.info("Started background log formatter: watching {} -> {}", input, output);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (Files.exists(input)) {
                        long currentModified = Files.getLastModifiedTime(input).toMillis();
                        if (currentModified > lastModified) {
                            formatLogs(input, output);
                            lastModified = currentModified;
                        }
                    }
                    Thread.sleep(2000); // Poll every 2 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Swallow exceptions so the daemon doesn't crash on bad I/O or partial writes
                }
            }
        });
        
        formatterThread.setDaemon(true);
        formatterThread.setName("LocalLogFormatter-Daemon");
        formatterThread.start();
    }

    private void formatLogs(Path input, Path output) throws IOException {
        List<JsonNode> parsedLogs = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(input)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    try {
                        parsedLogs.add(mapper.readTree(line));
                    } catch (Exception ignored) {
                        // Ignore malformed lines (could be mid-write)
                    }
                }
            }
        }
        
        if (!parsedLogs.isEmpty()) {
            // Write to a temporary file first, then move it atomically 
            // so editors don't read a partially written JSON array
            Path tempOutput = Paths.get(output.toString() + ".tmp");
            mapper.writeValue(tempOutput.toFile(), parsedLogs);
            Files.move(tempOutput, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
