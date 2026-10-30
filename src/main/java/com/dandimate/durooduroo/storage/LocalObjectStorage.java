/** 개발 환경에서 사용하는 로컬 파일 저장소입니다. presigned URL이 없으므로 앱의 서명된 업로드·이미지 API를 쓴다. */
package com.dandimate.durooduroo.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {

    private final Path root;
    private final Path publicRoot;

    public LocalObjectStorage(@Value("${storage.local-root:uploads}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        this.publicRoot = Paths.get(root).normalize();
    }

    @Override
    public String locationFor(String key) {
        return publicRoot.resolve(root.relativize(resolveKey(key))).toString();
    }

    @Override
    public void putBytes(String key, byte[] bytes, String contentType) {
        Path path = resolveKey(key);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String location) {
        resolveLocation(location).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // 지우지 못한 파일은 다음 정리 작업에서 다시 시도한다.
            }
        });
    }

    @Override
    public void deletePrefix(String keyPrefix) {
        Path dir = resolveKey(keyPrefix);
        if (!Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<String> presignedPutUrl(String key, String contentType, Duration ttl) {
        return Optional.empty();
    }

    @Override
    public Optional<StoredObject> head(String location) {
        return resolveLocation(location).filter(Files::isRegularFile).map(path -> {
            try {
                String type = MediaTypeFactory.getMediaType(path.getFileName().toString()).map(Object::toString).orElse("application/octet-stream");
                return new StoredObject(Files.size(path), type);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public Optional<String> presignedGetUrl(String location, Duration ttl) {
        return Optional.empty();
    }

    @Override
    public Optional<byte[]> read(String location) {
        return resolveLocation(location).filter(Files::isRegularFile).map(path -> {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public Optional<Resource> open(String location) {
        return resolveLocation(location).filter(Files::isRegularFile).map(FileSystemResource::new);
    }

    private Path resolveKey(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IllegalArgumentException("invalid storage key");
        return path;
    }

    private Optional<Path> resolveLocation(String location) {
        try {
            Path path = root.resolve(publicRoot.relativize(Paths.get(location).normalize())).normalize();
            return path.startsWith(root) ? Optional.of(path) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
