/** OpenAI Audio Transcription API를 호출하는 실제 STT 어댑터입니다. */
package com.dandimate.durooduroo.ai.service;

import com.dandimate.durooduroo.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OpenAiTranscriptionService {
    private final RestClient client;
    private final String apiKey;
    private final String model;
    public OpenAiTranscriptionService(@Value("${openai.api-key:}") String apiKey, @Value("${openai.stt-model:gpt-transcribe}") String model) {
        this.apiKey=apiKey; this.model=model; this.client=RestClient.builder().baseUrl("https://api.openai.com/v1").build();
    }
    public String transcribe(MultipartFile file) {
        if (apiKey.isBlank()) throw new BusinessException("STT_NOT_CONFIGURED", "OPENAI_API_KEY가 설정되지 않았습니다.");
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) { @Override public String getFilename(){ return file.getOriginalFilename()==null?"audio.bin":file.getOriginalFilename(); } };
            MultiValueMap<String,Object> body=new LinkedMultiValueMap<>(); body.add("model",model); body.add("file",resource);
            var response=client.post().uri("/audio/transcriptions").header("Authorization","Bearer "+apiKey).contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(java.util.Map.class);
            Object text=response==null?null:response.get("text"); if(text==null||text.toString().isBlank())throw new BusinessException("STT_EMPTY","음성에서 텍스트를 추출하지 못했습니다."); return text.toString();
        } catch (BusinessException e) { throw e; } catch (Exception e) { throw new BusinessException("STT_FAILED", "음성 변환에 실패했습니다."); }
    }
}
