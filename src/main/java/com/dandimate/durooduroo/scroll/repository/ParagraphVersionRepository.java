/** 문단 버전 이력 저장소입니다. */
package com.dandimate.durooduroo.scroll.repository;
import com.dandimate.durooduroo.scroll.domain.ParagraphVersion; import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface ParagraphVersionRepository extends JpaRepository<ParagraphVersion,String>{ List<ParagraphVersion> findAllByParagraphIdOrderByVersionAsc(String paragraphId); }
