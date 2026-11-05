package com.dandimate.durooduroo.embedding.service;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** cl100k_base, at most 1024 tokens/chunk. Original text remains in memory_entries. */
public final class EmbeddingText {
    private static final com.knuddels.jtokkit.api.Encoding ENCODING =
        Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    private EmbeddingText() {}
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static List<String> chunks(String text) {
        List<String> result = new ArrayList<>();
        int[] points = text.codePoints().toArray();
        for (int start=0; start<points.length;) {
            int end=Math.min(start+512,points.length);
            String part=new String(points,start,end-start);
            while (ENCODING.countTokensOrdinary(part)>1024) { end--; part=new String(points,start,end-start); }
            result.add(part); start=end;
        }
        return result;
    }
}
