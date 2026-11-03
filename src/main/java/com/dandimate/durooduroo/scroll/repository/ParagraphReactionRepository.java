/** 문단 반응 저장소입니다. */
package com.dandimate.durooduroo.scroll.repository;
import com.dandimate.durooduroo.scroll.domain.ParagraphReaction; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ParagraphReactionRepository extends JpaRepository<ParagraphReaction,String>{ Optional<ParagraphReaction> findByParagraphIdAndMemberId(String paragraphId,String memberId); long countByParagraphId(String paragraphId); }
