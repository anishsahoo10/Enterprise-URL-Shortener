package com.example.urlshortener.repository;

import com.example.urlshortener.entity.ClickEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    Page<ClickEvent> findByUrlIdOrderByClickedAtDesc(Long urlId, Pageable pageable);

    long countByUrlId(Long urlId);
}
