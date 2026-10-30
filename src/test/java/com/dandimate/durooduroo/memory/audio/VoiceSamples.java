package com.dandimate.durooduroo.memory.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 테스트용 무음 WAV(8kHz, 8bit, mono). */
public final class VoiceSamples {

    private VoiceSamples() {
    }

    public static byte[] wav(int seconds) {
        int rate = 8000;
        int dataSize = rate * seconds;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes()).putInt(36 + dataSize).put("WAVE".getBytes());
        buf.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate).putShort((short) 1).putShort((short) 8);
        buf.put("data".getBytes()).putInt(dataSize);
        return buf.array();
    }
}
