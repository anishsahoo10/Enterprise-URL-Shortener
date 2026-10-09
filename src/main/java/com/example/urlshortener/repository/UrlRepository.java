package com.example.urlshortener.repository;

import com.example.urlshortener.entity.Url;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.id = :urlId")
    int incrementClickCount(@Param("urlId") Long urlId);

    Page<Url> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
