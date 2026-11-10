package com.dandimate.durooduroo.chat.quota;

import com.dandimate.durooduroo.chat.dto.ChatMessageResponse;
import com.dandimate.durooduroo.chat.dto.ChatSourceView;
import com.dandimate.durooduroo.chat.service.ChatRecordAccess;
import com.dandimate.durooduroo.common.auth.Tokens;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.reviewerdemo.service.DemoScopeService;
import com.dandimate.durooduroo.user.domain.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 챗봇 질문 횟수(무료 4회 + 크레딧)를 관리한다.
 * 계정 행 잠금은 짧은 트랜잭션 안에서만 잡고, AI 호출 중에는 DB 잠금을 쥐지 않는다.
 */
@Service
public class ChatQuotaService {

    public static final int FREE_LIMIT = 4;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long PENDING_TTL_SECONDS = 600;
    private static final long FINISHED_RETENTION_SECONDS = 86400;

    @Autowired
    private DemoScopeService scopes;
    @Autowired
    private ChatRecordAccess access;
    @PersistenceContext
    private EntityManager em;

    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public ChatQuotaService(PlatformTransactionManager manager, ObjectMapper json) {
        this.tx = new TransactionTemplate(manager);
        this.json = json;
    }

    public record Quota(int freeLimit, int freeRemaining, long credits, OffsetDateTime resetsAt) {
    }

    private record Ticket(String id, String attemptId, ChatMessageResponse replay) {
    }

    public Quota get(String user) {
        return tx.execute(status -> {
            var wallet = wallet(user);
            return new Quota(FREE_LIMIT, Math.max(0, FREE_LIMIT - wallet.freeUsed), wallet.credits,
                    wallet.freeDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime());
        });
    }

    public ChatMessageResponse execute(String user, String key, String fingerprint, Supplier<ChatMessageResponse> answer) {
        if (key != null && (key.length() < 16 || key.length() > 128)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key는 16~128자입니다.");
        }
        String scope = scopes.current(user);
        String id = Tokens.hash(user + ":" + scope + ":" + (key == null ? UUID.randomUUID() : key));
        String hash = Tokens.hash(fingerprint);

        Ticket ticket = tx.execute(status -> reserve(user, scope, id, hash));
        if (ticket.replay() != null) {
            requireSameRecords(user, ticket.replay());
            return ticket.replay();
        }

        try {
            var response = answer.get();
            tx.executeWithoutResult(status -> {
                var wallet = wallet(user);
                scopes.requireCurrent(user, scope);
                var usage = em.find(ChatUsage.class, id);
                if (!usage.status.equals("PENDING") || !ticket.attemptId().equals(usage.attemptId)) {
                    throw new BusinessException(ErrorCode.AI_TEMPORARILY_UNAVAILABLE,
                            "질문 처리 시간이 초과되었습니다. 다시 시도해 주세요.");
                }
                if (!Set.of("ANSWER", "CONFLICT").contains(response.outcome())) {
                    release(wallet, usage);
                } else {
                    usage.status = "CHARGED";
                }
                if (key != null) {
                    usage.response = write(response);
                }
            });
            return response;
        } catch (RuntimeException failure) {
            tx.executeWithoutResult(status -> {
                var wallet = wallet(user);
                var usage = em.find(ChatUsage.class, id);
                if (usage != null && usage.status.equals("PENDING") && ticket.attemptId().equals(usage.attemptId)) {
                    release(wallet, usage);
                }
            });
            throw failure;
        }
    }

    @Scheduled(fixedDelayString = "PT1H")
    public void cleanup() {
        tx.executeWithoutResult(status -> em
                .createQuery("delete from ChatUsage u where u.status<>'PENDING' and u.expiresAt<:before")
                .setParameter("before", Instant.now().minusSeconds(FINISHED_RETENTION_SECONDS))
                .executeUpdate());
    }

    /** 잠긴 결제 주문이 DONE으로 바뀌는 같은 트랜잭션 안에서 호출된다. */
    @Transactional
    public void grant(String user, int credits) {
        if (credits <= 0) {
            throw new IllegalArgumentException();
        }
        wallet(user).credits += credits;
    }

    /** 같은 요청 키로 다시 오면 저장된 응답을 돌려주고, 처음이면 무료 횟수나 크레딧 하나를 먼저 잡아 둔다. */
    private Ticket reserve(String user, String scope, String id, String hash) {
        var wallet = wallet(user);
        scopes.requireCurrent(user, scope);
        var previous = em.find(ChatUsage.class, id);
        if (previous != null) {
            if (!hash.equals(previous.requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "다른 질문에 사용된 요청 키입니다.");
            }
            if (previous.response != null) {
                return new Ticket(id, null, read(previous.response));
            }
            if (previous.status.equals("PENDING")) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "질문을 처리 중입니다.");
            }
            em.remove(previous);
            em.flush();
        }
        if (wallet.freeUsed >= FREE_LIMIT && wallet.credits <= 0) {
            throw new BusinessException(ErrorCode.CHAT_LIMIT_EXCEEDED, "무료 질문과 크레딧을 모두 사용했어요.");
        }
        var usage = new ChatUsage();
        usage.setDemoScope(scope);
        usage.id = id;
        usage.userId = user;
        usage.requestHash = hash;
        usage.status = "PENDING";
        usage.usageDate = wallet.freeDate;
        usage.attemptId = UUID.randomUUID().toString();
        usage.free = wallet.freeUsed < FREE_LIMIT;
        usage.expiresAt = Instant.now().plusSeconds(PENDING_TTL_SECONDS);
        if (usage.free) {
            wallet.freeUsed++;
        } else {
            wallet.credits--;
        }
        em.persist(usage);
        return new Ticket(id, usage.attemptId, null);
    }

    /** 저장된 응답을 다시 줄 때, 그 사이 기록이나 열람 권한이 바뀌었으면 거절한다. */
    private void requireSameRecords(String user, ChatMessageResponse replay) {
        var current = access.load(user, false);
        for (var source : replay.sources()) {
            var visible = new ArrayList<ChatSourceView.EntryInfo>(source.entries());
            if (source.firstEntry() != null) {
                visible.add(source.firstEntry());
            }
            for (var entry : visible) {
                var now = current.get(entry.entryId());
                if (now == null || !now.entry().equals(entry)) {
                    throw new BusinessException(ErrorCode.VERSION_CONFLICT, "기록이나 권한이 바뀌었어요. 새 요청으로 질문해 주세요.");
                }
            }
        }
    }

    private ChatWallet wallet(String user) {
        if (em.find(UserAccount.class, user, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new BusinessException(ErrorCode.INVALID_MEMBER_TOKEN, "로그인이 필요합니다.");
        }
        ChatWallet wallet = em.find(ChatWallet.class, user);
        if (wallet == null) {
            wallet = new ChatWallet(user);
            em.persist(wallet);
        }
        wallet.reset(LocalDate.now(KST));
        var expired = em.createQuery(
                        "select u from ChatUsage u where u.userId=:user and u.status='PENDING' and u.expiresAt<:now",
                        ChatUsage.class)
                .setParameter("user", user)
                .setParameter("now", Instant.now())
                .getResultList();
        for (var usage : expired) {
            release(wallet, usage);
        }
        return wallet;
    }

    private void release(ChatWallet wallet, ChatUsage usage) {
        if (usage.free) {
            if (usage.usageDate.equals(wallet.freeDate)) {
                wallet.freeUsed--;
            }
        } else {
            wallet.credits++;
        }
        usage.status = "RELEASED";
    }

    private String write(ChatMessageResponse value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ChatMessageResponse read(String value) {
        try {
            return json.readValue(value, ChatMessageResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
