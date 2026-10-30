/**
 * 음성 길이를 서버가 직접 잰다 (명세 2장: 클라이언트가 보낸 길이는 믿지 않는다).
 * 1) ffprobe가 있으면 ffprobe로 잰다 (webm 포함 모든 형식). 운영 서버에는 ffprobe 설치를 권장한다.
 * 2) 없으면 MP4/M4A(mvhd)와 WAV 헤더를 직접 읽는다.
 * 둘 다 안 되면 비어 있고, 호출하는 쪽이 길이 검사를 건너뛴다.
 */
package com.dandimate.durooduroo.memory.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AudioDurationProbe {

    private static final Logger log = LoggerFactory.getLogger(AudioDurationProbe.class);

    private final String ffprobePath;
    private final boolean ffprobeAvailable;

    public AudioDurationProbe(@Value("${app.audio.ffprobe-path:ffprobe}") String ffprobePath) {
        this.ffprobePath = ffprobePath;
        this.ffprobeAvailable = checkFfprobe(ffprobePath);
        if (!ffprobeAvailable) log.warn("ffprobe not found at '{}'; only MP4/M4A/WAV durations can be measured", ffprobePath);
    }

    public Optional<Duration> probe(byte[] audio) {
        if (ffprobeAvailable) {
            Optional<Duration> measured = ffprobe(audio);
            if (measured.isPresent()) return measured;
        }
        return mp4(audio).or(() -> wav(audio));
    }

    private Optional<Duration> ffprobe(byte[] audio) {
        Path file = null;
        try {
            file = Files.createTempFile("audio-probe", ".bin");
            Files.write(file, audio);
            Process process = new ProcessBuilder(ffprobePath, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file.toString())
                    .redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return Optional.of(Duration.ofMillis(Math.round(Double.parseDouble(out) * 1000)));
        } catch (Exception e) {
            return Optional.empty();
        } finally {
            if (file != null) {
                try { Files.deleteIfExists(file); } catch (Exception ignored) { }
            }
        }
    }

    /** ISO BMFF: moov > mvhd의 timescale·duration. */
    static Optional<Duration> mp4(byte[] data) {
        return findBox(data, 0, data.length, "moov").flatMap(moov -> findBox(data, moov[0], moov[1], "mvhd")).flatMap(mvhd -> {
            ByteBuffer buf = ByteBuffer.wrap(data, mvhd[0], mvhd[1] - mvhd[0]).order(ByteOrder.BIG_ENDIAN);
            int version = buf.get() & 0xff;
            buf.position(buf.position() + 3);
            long timescale;
            long duration;
            if (version == 1) {
                buf.position(buf.position() + 16);
                timescale = buf.getInt() & 0xffffffffL;
                duration = buf.getLong();
            } else {
                buf.position(buf.position() + 8);
                timescale = buf.getInt() & 0xffffffffL;
                duration = buf.getInt() & 0xffffffffL;
            }
            return timescale == 0 ? Optional.empty() : Optional.of(Duration.ofMillis(duration * 1000 / timescale));
        });
    }

    /** 박스 본문의 [시작, 끝) 위치. */
    private static Optional<int[]> findBox(byte[] data, int from, int to, String type) {
        int pos = from;
        while (pos + 8 <= to) {
            long size = ByteBuffer.wrap(data, pos, 4).getInt() & 0xffffffffL;
            String boxType = new String(data, pos + 4, 4, StandardCharsets.ISO_8859_1);
            int header = 8;
            if (size == 1 && pos + 16 <= to) {
                size = ByteBuffer.wrap(data, pos + 8, 8).getLong();
                header = 16;
            } else if (size == 0) {
                size = to - pos;
            }
            if (size < header || pos + size > to) return Optional.empty();
            if (boxType.equals(type)) return Optional.of(new int[]{pos + header, (int) (pos + size)});
            pos += (int) size;
        }
        return Optional.empty();
    }

    /** RIFF/WAVE: fmt의 byteRate와 data 크기. */
    static Optional<Duration> wav(byte[] data) {
        if (data.length < 12 || !new String(data, 0, 4, StandardCharsets.ISO_8859_1).equals("RIFF")
                || !new String(data, 8, 4, StandardCharsets.ISO_8859_1).equals("WAVE")) return Optional.empty();
        long byteRate = 0;
        int pos = 12;
        while (pos + 8 <= data.length) {
            String id = new String(data, pos, 4, StandardCharsets.ISO_8859_1);
            long size = ByteBuffer.wrap(data, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL;
            if (id.equals("fmt ") && pos + 16 <= data.length) {
                byteRate = ByteBuffer.wrap(data, pos + 16, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL;
            } else if (id.equals("data")) {
                return byteRate == 0 ? Optional.empty() : Optional.of(Duration.ofMillis(size * 1000 / byteRate));
            }
            pos += 8 + (int) size + (int) (size % 2);
        }
        return Optional.empty();
    }

    private static boolean checkFfprobe(String path) {
        try {
            Process process = new ProcessBuilder(path, "-version").redirectErrorStream(true).start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
