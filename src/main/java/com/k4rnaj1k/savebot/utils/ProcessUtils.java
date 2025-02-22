package com.k4rnaj1k.savebot.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@UtilityClass
public class ProcessUtils {
    public static InputStream runCommand(String... commandAndArgs) {
        try {
            return runC(commandAndArgs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static InputStream runC(String... commandAndArgs) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                commandAndArgs
        );

        Process process = null;
        try {
            process = pb.start();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }

        // Read the process's stdout into memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream stdout = process.getInputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = stdout.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }

        // Wait for the process to finish and check the exit code
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for process to finish", e);
        }
        if (exitCode != 0) {
            // Read error output if available
            String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
           throw new RuntimeException(" process failed with exit code " + exitCode +
                    ". Error output: " + errorOutput);
        }

        // Return a ByteArrayInputStream wrapping the downloaded data
        return new ByteArrayInputStream(baos.toByteArray());
    }
}
