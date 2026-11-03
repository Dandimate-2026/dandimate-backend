/** 문단 인용 출처 저장소입니다. */
package com.dandimate.durooduroo.scroll.repository;
import com.dandimate.durooduroo.scroll.domain.ParagraphSource; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ParagraphSourceRepository extends JpaRepository<ParagraphSource,String>{ List<ParagraphSource> findAllByParagraphIdInOrderBySortOrderAsc(Collection<String> paragraphIds); }
