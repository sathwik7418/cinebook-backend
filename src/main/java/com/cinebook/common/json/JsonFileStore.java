package com.cinebook.common.json;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Small atomic JSON persistence helper for the single-process college demo. */
@Service
public class JsonFileStore {
    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonFileStore(@Value("${cinebook.storage.directory:./data}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    public synchronized <T> List<T> readList(String filename, Class<T> type) {
        Path path = path(filename);
        if (!Files.exists(path)) return new ArrayList<>();
        try {
            JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, type);
            return new ArrayList<>(mapper.readValue(path.toFile(), listType));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read local application data.", e);
        }
    }

    public synchronized <T> void writeList(String filename, List<T> values) {
        Path target = path(filename);
        try {
            Files.createDirectories(root);
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            mapper.writeValue(temp.toFile(), values);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save local application data.", e);
        }
    }

    private Path path(String filename) {
        if (filename == null || !filename.matches("[a-z][a-z0-9-]{1,50}\\.json"))
            throw new IllegalArgumentException("Invalid data file name.");
        Path result = root.resolve(filename).normalize();
        if (!result.startsWith(root)) throw new IllegalArgumentException("Invalid data path.");
        return result;
    }
}
