/**
 * 서버에 설치된 도구로 HEIC를 JPEG로 바꾼다. 시작할 때 heif-convert(libheif) → magick(ImageMagick) → sips(macOS) 순서로 찾는다.
 * 운영 이미지(Dockerfile)에는 heif-convert를 설치한다. 도구가 없으면 변환은 실패하고 사진은 재시도 후 FAILED가 된다.
 */
package com.dandimate.durooduroo.photo.image;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CommandHeicConverter implements HeicConverter {

    private static final Logger log = LoggerFactory.getLogger(CommandHeicConverter.class);

    private enum Tool { HEIF_CONVERT, MAGICK, SIPS }

    private final Optional<Tool> tool;

    public CommandHeicConverter() {
        this.tool = detect();
        if (tool.isEmpty()) log.warn("No HEIC converter found (heif-convert, magick, sips); HEIC photos will end as FAILED");
        else log.info("HEIC converter: {}", tool.get());
    }

    @Override
    public byte[] toJpeg(byte[] heic) {
        Tool selected = tool.orElseThrow(() -> new IllegalStateException("no HEIC converter installed"));
        Path dir = null;
        try {
            dir = Files.createTempDirectory("heic");
            Path in = dir.resolve("in.heic");
            Path out = dir.resolve("out.jpg");
            Files.write(in, heic);
            List<String> command = switch (selected) {
                case HEIF_CONVERT -> List.of("heif-convert", "-q", "90", in.toString(), out.toString());
                case MAGICK -> List.of("magick", in.toString(), "-quality", "90", out.toString());
                case SIPS -> List.of("sips", "-s", "format", "jpeg", "-s", "formatOptions", "90", in.toString(), "--out", out.toString());
            };
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("HEIC conversion timed out");
            }
            if (process.exitValue() != 0 || !Files.exists(out)) throw new IllegalStateException("HEIC conversion failed with exit " + process.exitValue());
            return Files.readAllBytes(out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (dir != null) {
                try (var files = Files.walk(dir)) {
                    files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                } catch (Exception ignored) {
                    // 임시 파일 정리 실패는 무시한다.
                }
            }
        }
    }

    private static Optional<Tool> detect() {
        if (available("heif-convert", "--version")) return Optional.of(Tool.HEIF_CONVERT);
        if (available("magick", "-version")) return Optional.of(Tool.MAGICK);
        if (available("sips", "--help")) return Optional.of(Tool.SIPS);
        return Optional.empty();
    }

    private static boolean available(String command, String arg) {
        try {
            Process process = new ProcessBuilder(command, arg).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }
}
