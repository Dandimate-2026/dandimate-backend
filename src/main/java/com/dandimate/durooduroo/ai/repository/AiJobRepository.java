/** AI 작업 상태를 저장하는 저장소입니다. */
package com.dandimate.durooduroo.ai.repository;
import com.dandimate.durooduroo.ai.domain.AiJob; import org.springframework.data.jpa.repository.JpaRepository;
public interface AiJobRepository extends JpaRepository<AiJob,String> {}
