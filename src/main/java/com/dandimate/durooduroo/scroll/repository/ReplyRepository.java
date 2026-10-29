/** 답가 저장소입니다. */
package com.dandimate.durooduroo.scroll.repository;
import com.dandimate.durooduroo.scroll.domain.Reply; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ReplyRepository extends JpaRepository<Reply,String>{List<Reply> findAllByParagraphId(String paragraphId);}
