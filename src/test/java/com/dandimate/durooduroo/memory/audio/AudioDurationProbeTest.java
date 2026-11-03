package com.dandimate.durooduroo.memory.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AudioDurationProbeTest {

    @Test
    void WAV_헤더로_길이를_잰다() {
        assertThat(AudioDurationProbe.wav(VoiceSamples.wav(6))).contains(Duration.ofSeconds(6));
    }

    @Test
    void MP4_mvhd로_길이를_잰다() {
        ByteBuffer mvhd = ByteBuffer.allocate(8 + 20);
        mvhd.putInt(28).put("mvhd".getBytes()).put((byte) 0).put(new byte[3]).putInt(0).putInt(0).putInt(1000).putInt(12_500);
        ByteBuffer file = ByteBuffer.allocate(8 + 8 + 28);
        file.putInt(8).put("ftyp".getBytes()).putInt(8 + 28).put("moov".getBytes()).put(mvhd.array());

        assertThat(AudioDurationProbe.mp4(file.array())).contains(Duration.ofMillis(12_500));
    }

    @Test
    void 모르는_형식은_비어_있다() {
        assertThat(AudioDurationProbe.mp4(new byte[]{1, 2, 3})).isEmpty();
        assertThat(AudioDurationProbe.wav(new byte[]{1, 2, 3})).isEmpty();
    }
}
