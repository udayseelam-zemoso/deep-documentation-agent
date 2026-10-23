package com.deepdoc.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SourceFiles {
    private static final Logger logger = LoggerFactory.getLogger(SourceFiles.class);

    private SourceFiles() {
    }

    /** Reads a source file as UTF-8, falling back to ISO-8859-1. Returns null if unreadable. */
    public static String read(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            try {
                return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        } catch (IOException e) {
            logger.warn("Skipping unreadable file {}: {}", file, e.getMessage());
            return null;
        }
    }
}
