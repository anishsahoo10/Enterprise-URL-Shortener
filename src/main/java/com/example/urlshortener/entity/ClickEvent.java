package com.example.urlshortener.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "click_events", indexes = {
        @Index(name = "idx_click_events_url_id_clicked_at", columnList = "url_id, clicked_at DESC")
})
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "url_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Url url;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "referrer", columnDefinition = "TEXT")
    private String referrer;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    public ClickEvent() {
    }

    public ClickEvent(Url url, Instant clickedAt, String referrer, String userAgent) {
        this.url = url;
        this.clickedAt = clickedAt != null ? clickedAt : Instant.now();
        this.referrer = referrer;
        this.userAgent = userAgent;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Url getUrl() {
        return url;
    }

    public void setUrl(Url url) {
        this.url = url;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(Instant clickedAt) {
        this.clickedAt = clickedAt;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClickEvent that = (ClickEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
