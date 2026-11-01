package com.dandimate.durooduroo.room;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.dandimate.durooduroo.ai.common.AiFailure;
import com.dandimate.durooduroo.ai.transcription.*;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.domain.*;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.audio.VoiceSamples;
import com.dandimate.durooduroo.memory.service.*;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.dto.UploadRequest;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.photo.service.PhotoUploadService;
import com.dandimate.durooduroo.room.domain.*;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.support.LegacyWorkflowSecurity;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "storage.local-root=build/test-plan-uploads")
@AutoConfigureMockMvc
@Import(LegacyWorkflowSecurity.class)
class RoomPlanFlowTest {
    @Autowired RoomRepository rooms;
    @Autowired MemberRepository members;
    @Autowired PhotoRepository photos;
    @Autowired PhotoUploadService uploads;
    @Autowired TranscriptionService transcription;
    @Autowired VoiceQuotaService quota;
    @Autowired VoiceReservationRecovery recovery;
    @Autowired com.dandimate.durooduroo.memory.repository.VoiceUsageRepository usages;
    @Autowired PlatformTransactionManager manager;
    @Autowired MockMvc mvc;
    @MockitoBean TranscriptionAiService stt;

    @BeforeEach void prepare() {
        doReturn(new TranscriptionResult("v1", "함께 여행했던 날", List.of("ko"), "fake", "v1", "test")).when(stt).transcribe(any());
    }

    record Fixture(Room room, Member host, Photo photo) {}
    Fixture fixture(RoomPlan plan) {
        Room r = Room.create("여행", LocalDate.now(), 2, plan); // 테스트에서만 유료 방 구성
        rooms.saveAndFlush(r);
        Member m = members.saveAndFlush(Member.host(r.getId(), "서연"));
        Photo photo = photos.saveAndFlush(Photo.pending(UUID.randomUUID().toString(), r.getId(), m.getId(), "key", "local", "image/png", 1));
        return new Fixture(r, m, photo);
    }
    String token(Member m) { return "Bearer " + m.getIssuedAccessToken(); }
    String key() { return UUID.randomUUID().toString(); }
    MockMultipartFile audio(int seconds) { return new MockMultipartFile("file", "voice.wav", "audio/wav", VoiceSamples.wav(seconds)); }
    UploadRequest files(int count, int start) {
        return new UploadRequest(IntStream.range(start, start + count).mapToObj(i ->
                new UploadRequest.FileRequest("c" + i, "a.png", "image/png", 10, i)).toList());
    }

    @Test void capacityIsValidatedAndInviteUsesSelectedCapacity() throws Exception {
        String json = "{\"name\":\"여행\",\"meetingDate\":\"2026-11-01\",\"planType\":\"FREE\",\"hostNickname\":\"서연\",\"capacity\":%d,\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}}";
        for (int invalid : new int[]{0,9}) mvc.perform(post("/api/v1/rooms").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON).content(json.formatted(invalid))).andExpect(status().isBadRequest());
        String response = mvc.perform(post("/api/v1/rooms").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON).content(json.formatted(1)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.room.capacity").value(1))
                .andExpect(jsonPath("$.data.room.planType").value("FREE")).andReturn().getResponse().getContentAsString();
        String invite = com.jayway.jsonpath.JsonPath.read(response,"$.data.inviteToken");
        mvc.perform(get("/api/v1/invites/"+invite)).andExpect(status().isOk()).andExpect(jsonPath("$.data.capacity").value(1)).andExpect(jsonPath("$.data.canJoin").value(false));
        mvc.perform(post("/api/v1/invites/"+invite+"/join").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"민주\",\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ROOM_FULL"));
    }

    @Test void planSelectionIsRequiredAndUnpaidPlansNeverCreateFreeRooms() throws Exception {
        Map<String,Object> request = new HashMap<>(Map.of("name","여행","meetingDate","2026-11-01","hostNickname","서연","capacity",5,
                "consent",Map.of("version","v1","terms",true,"mediaProcessing",true,"externalAiProcessing",true)));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        long before = rooms.count();
        mvc.perform(post("/api/v1/rooms").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        request.put("planType","INVALID");
        mvc.perform(post("/api/v1/rooms").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        for (String plan : List.of("LITE","PRO")) {
            request.put("planType",plan);
            mvc.perform(post("/api/v1/rooms").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request))).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_CONFIGURED"));
        }
        assertThat(rooms.count()).isEqualTo(before);
        request.put("planType","FREE");
        mvc.perform(post("/api/v1/rooms").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.room.capacity").value(5)).andExpect(jsonPath("$.data.room.planType").value("FREE"));
        assertThat(rooms.count()).isEqualTo(before+1);
    }

    @ParameterizedTest @EnumSource(RoomPlan.class)
    void photosAreLimitedPerMemberNotSharedAndHomeReturnsQuota(RoomPlan plan) throws Exception {
        Fixture f = fixture(plan);
        uploads.requestUploads(f.room.getId(),token(f.host),files(plan.photosPerMember()-1,2));
        assertThatThrownBy(() -> uploads.requestUploads(f.room.getId(),token(f.host),files(1,1))).isInstanceOf(BusinessException.class);
        Member guest = members.saveAndFlush(Member.join(f.room.getId(),"민주",FlowerCode.FORSYTHIA));
        uploads.requestUploads(f.room.getId(),token(guest),files(plan.photosPerMember(),1));
        assertThat(photos.countByRoomId(f.room.getId())).isEqualTo(plan.photosPerMember()*2);
        mvc.perform(get("/api/v1/rooms/"+f.room.getId()).header("Authorization",token(f.host))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.room.quota.myPhotosRemaining").value(0))
                .andExpect(jsonPath("$.data.room.quota.photosPerRoom").value(plan.photosPerRoom()))
                .andExpect(jsonPath("$.data.room.capacity").value(2));
    }

    @ParameterizedTest @EnumSource(RoomPlan.class)
    void voiceAndRegenerationRespectEachPlan(RoomPlan plan) {
        Fixture f = fixture(plan);
        transcription.forPhoto(f.photo.getId(),token(f.host),"FIRST",audio(plan.firstSeconds()),key());
        assertThatThrownBy(() -> transcription.forPhoto(f.photo.getId(),token(f.host),"FIRST",audio(plan.firstSeconds()+1),key())).isInstanceOf(BusinessException.class);
        assertThat(rooms.findById(f.room.getId()).orElseThrow().getVoiceUsedMs()).isEqualTo(plan.firstSeconds()*1000L);
        String reservation = key();
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            Room r = rooms.findByIdForUpdate(f.room.getId()).orElseThrow();
            r.startCirculating(); r.startWeaving(); r.completeWeaving();
            for (int i=0; i<plan.regenerations(); i++) r.useRegeneration();
            assertThatThrownBy(r::useRegeneration).isInstanceOf(BusinessException.class);
        });
        quota.reserve(reservation,f.room.getId(),f.host.getId(),"hash",plan.otherSeconds()*1000L,"REPLY");
        assertThatThrownBy(() -> quota.reserve(key(),f.room.getId(),f.host.getId(),"hash",plan.otherSeconds()*1000L+1,"REPLY")).isInstanceOf(BusinessException.class);
        quota.fail(f.room.getId(),reservation);
    }

    @Test void repeatedAudioIsChargedOnceAndChangedPayloadIsRejected() {
        Fixture f = fixture(RoomPlan.FREE); String key = key();
        var first = transcription.forPhoto(f.photo.getId(),token(f.host),"FIRST",audio(6),key);
        var replay = transcription.forPhoto(f.photo.getId(),token(f.host),"FIRST",audio(6),key);
        assertThat(replay).isEqualTo(first);
        assertThatThrownBy(() -> transcription.forPhoto(f.photo.getId(),token(f.host),"FIRST",audio(7),key)).isInstanceOf(BusinessException.class);
        verify(stt,times(1)).transcribe(any());
        Room room = rooms.findById(f.room.getId()).orElseThrow();
        assertThat(room.getVoiceUsedMs()).isEqualTo(6000);
        assertThat(room.getVoiceReservedMs()).isZero();
    }

    @Test void failedSttReleasesReservationAndSameRequestCanRetry() {
        Fixture f = fixture(RoomPlan.FREE); String key = key();
        when(stt.transcribe(any())).thenThrow(new AiFailure(AiFailure.Code.OPENAI_TIMEOUT,true,"timeout"));
        assertThatThrownBy(() -> transcription.forPhoto(f.photo.getId(),token(f.host),"FIRST",audio(6),key)).isInstanceOf(BusinessException.class);
        Room room = rooms.findById(f.room.getId()).orElseThrow();
        assertThat(room.getVoiceUsedMs()).isZero(); assertThat(room.getVoiceReservedMs()).isZero();
        prepare();
        transcription.forPhoto(f.photo.getId(),token(f.host),"FIRST",audio(6),key);
        assertThat(rooms.findById(f.room.getId()).orElseThrow().getVoiceUsedMs()).isEqualTo(6000);
    }

    @Test void unknownDurationIsRejectedBeforeCallingAi() {
        Fixture f = fixture(RoomPlan.FREE);
        assertThatThrownBy(() -> transcription.forPhoto(f.photo.getId(),token(f.host),"FIRST",new MockMultipartFile("file","bad.wav","audio/wav",new byte[]{1,2}),key()))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(stt);
        assertThat(rooms.findById(f.room.getId()).orElseThrow().getVoiceReservedMs()).isZero();
    }

    @Test void interruptedReservationIsReleasedAndCannotBeReusedByALateWorker() {
        Fixture f = fixture(RoomPlan.FREE); String id = key();
        var usage = quota.reserve(id,f.room.getId(),f.host.getId(),"hash",6000,"FIRST");
        ReflectionTestUtils.setField(usage,"updatedAt",java.time.LocalDateTime.now().minusHours(2));
        usages.saveAndFlush(usage);
        recovery.recover();
        assertThat(rooms.findById(f.room.getId()).orElseThrow().getVoiceReservedMs()).isZero();
        assertThat(usages.findById(id).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> quota.reserve(id,f.room.getId(),f.host.getId(),"hash",6000,"FIRST")).isInstanceOf(BusinessException.class);
        var draft = com.dandimate.durooduroo.memory.domain.TranscriptionDraft.create(key(),f.room.getId(),f.host.getId(),"PHOTO",f.photo.getId(),"FIRST",key(),"local","늦은 응답",6000L,"fake","test");
        assertThatThrownBy(() -> quota.complete(id,draft,List.of("ko"))).isInstanceOf(IllegalStateException.class);
    }

    @Test void longVoiceTranscriptCanBeConfirmedWhileTextLimitRemains() throws Exception {
        Fixture f=fixture(RoomPlan.PRO);
        f.photo.complete("hash"); photos.saveAndFlush(f.photo);
        String longText="함께 여행했던 추억입니다. ".repeat(40);
        doReturn(new TranscriptionResult("v1",longText,List.of("ko"),"fake","v1","test")).when(stt).transcribe(any());
        var draft=transcription.forPhoto(f.photo.getId(),token(f.host),"FIRST",audio(120),key());
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        mvc.perform(post("/api/v1/photos/"+f.photo.getId()+"/entries").header("Authorization",token(f.host)).header("Idempotency-Key",key())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("kind","FIRST","inputMode","TEXT","transcript",longText))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/photos/"+f.photo.getId()+"/entries").header("Authorization",token(f.host)).header("Idempotency-Key",key())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("kind","FIRST","inputMode","VOICE","transcript",longText,"transcriptionId",draft.transcriptionId()))))
                .andExpect(status().isCreated());
        assertThat(rooms.findById(f.room.getId()).orElseThrow().getVoiceUsedMs()).isEqualTo(120000);
    }

    @Test void concurrentVoiceAndPhotoRequestsCannotExceedQuota() throws Exception {
        Fixture f = fixture(RoomPlan.FREE);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            Room r=rooms.findByIdForUpdate(f.room.getId()).orElseThrow();
            r.reserveVoice(894_000); r.settleVoice(894_000,true);
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> voice = () -> { start.await(); try { quota.reserve(key(),f.room.getId(),f.host.getId(),"hash",6000,"FIRST"); return true; } catch(BusinessException e) {return false;} };
            var a=pool.submit(voice); var b=pool.submit(voice); start.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(rooms.findById(f.room.getId()).orElseThrow().getVoiceRemainingMs()).isZero();
            Callable<Boolean> upload = () -> {
                int status = mvc.perform(post("/api/v1/rooms/"+f.room.getId()+"/photos/upload-requests")
                    .header("Authorization",token(f.host)).header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON)
                    .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(files(2,2))))
                    .andReturn().getResponse().getStatus();
                assertThat(status).isIn(201,422);
                return status == 201;
            };
            var c=pool.submit(upload); var d=pool.submit(upload);
            assertThat(List.of(c.get(10,TimeUnit.SECONDS),d.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(photos.countByRoomId(f.room.getId())).isEqualTo(3);
        }
    }
}
