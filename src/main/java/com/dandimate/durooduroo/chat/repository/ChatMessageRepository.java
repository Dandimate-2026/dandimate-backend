package com.dandimate.durooduroo.chat.repository;
import com.dandimate.durooduroo.chat.domain.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface ChatMessageRepository extends JpaRepository<ChatMessage,String> { List<ChatMessage> findAllByUserIdAndConversationIdOrderByCreatedAtAsc(String userId,String conversationId); List<ChatMessage> findAllByUserIdOrderByCreatedAtDesc(String userId); @Query("select distinct m.conversationId from ChatMessage m where m.userId=?1 order by m.conversationId") List<String> findConversationIdsByUserId(String userId); void deleteByUserIdAndConversationId(String userId,String conversationId); void deleteAllByUserId(String userId); }
