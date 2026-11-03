package com.dandimate.durooduroo.photo.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** 실제 도구로 HEIC → JPEG 변환을 확인한다. macOS에서는 sips로 HEIC를 만들어 넣고, 도구가 없는 환경에서는 건너뛴다. */
class CommandHeicConverterTest {

    @Test
    void 설치된_도구로_HEIC를_JPEG로_바꾼다() throws Exception {
        assumeTrue(Path.of("/usr/bin/sips").toFile().canExecute(), "sips(macOS)로 HEIC 샘플을 만들 수 있을 때만 실행");
        Path dir = Files.createTempDirectory("heic-test");
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        image.setRGB(5, 5, 0xFF0000);
        ImageIO.write(image, "png", dir.resolve("in.png").toFile());
        Process make = new ProcessBuilder("sips", "-s", "format", "heic", dir.resolve("in.png").toString(), "--out", dir.resolve("in.heic").toString())
                .redirectErrorStream(true).start();
        make.getInputStream().readAllBytes();
        assumeTrue(make.waitFor() == 0 && Files.exists(dir.resolve("in.heic")), "이 macOS가 HEIC 쓰기를 지원할 때만 실행");

        byte[] jpeg = new CommandHeicConverter().toJpeg(Files.readAllBytes(dir.resolve("in.heic")));

        assertThat(jpeg[0] & 0xff).isEqualTo(0xFF);
        assertThat(jpeg[1] & 0xff).isEqualTo(0xD8);
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(JpegMetadata.strip(jpeg)));
        assertThat(decoded.getWidth()).isEqualTo(32);
    }
}
