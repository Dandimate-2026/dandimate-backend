/** 문단 저장소입니다. */
package com.dandimate.durooduroo.scroll.repository;
import com.dandimate.durooduroo.scroll.domain.Paragraph; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ParagraphRepository extends JpaRepository<Paragraph,String>{ List<Paragraph> findAllByScrollId(String scrollId); List<Paragraph> findAllByScrollIdOrderByDisplayOrderAsc(String scrollId); }
