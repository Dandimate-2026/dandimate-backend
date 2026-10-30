/** 문단 말투 보존 평가 저장소입니다. */
package com.dandimate.durooduroo.scroll.repository;
import com.dandimate.durooduroo.scroll.domain.ParagraphRating; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ParagraphRatingRepository extends JpaRepository<ParagraphRating,String>{ Optional<ParagraphRating> findByParagraphIdAndMemberId(String paragraphId,String memberId); }
