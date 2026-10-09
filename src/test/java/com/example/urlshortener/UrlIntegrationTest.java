package com.example.urlshortener;

import com.example.urlshortener.cache.UrlCacheService;
import com.example.urlshortener.dto.CreateUrlRequest;
import com.example.urlshortener.dto.UpdateUrlRequest;
import com.example.urlshortener.entity.Url;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.UrlRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UrlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @MockBean
    private UrlCacheService urlCacheService;

    @BeforeEach
    void setUp() {
        clickEventRepository.deleteAll();
        urlRepository.deleteAll();
        // Default mock behavior for cache: miss by default to let DB handle it
        when(urlCacheService.get(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Complete link lifecycle: create -> redirect -> details -> analytics -> deactivate -> delete")
    void shouldExecuteFullLinkLifecycle() throws Exception {
        // 1. Create short URL
        CreateUrlRequest createRequest = new CreateUrlRequest("https://example.com/products/laptop", null);
        MvcResult createResult = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode", notNullValue()))
                .andExpect(jsonPath("$.shortUrl", containsString("http://localhost:8080/")))
                .andExpect(jsonPath("$.originalUrl", is("https://example.com/products/laptop")))
                .andExpect(jsonPath("$.active", is(true)))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String shortCode = jsonNode.get("shortCode").asText();

        // 2. Redirect to destination (HTTP 302 Found)
        mockMvc.perform(get("/" + shortCode)
                        .header(HttpHeaders.REFERER, "https://news.ycombinator.com")
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 IntegrationTest"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/products/laptop"));

        // Second redirect to verify click counter increment
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/products/laptop"));

        // 3. Inspect URL details
        mockMvc.perform(get("/api/v1/urls/" + shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode", is(shortCode)))
                .andExpect(jsonPath("$.clickCount", is(2)))
                .andExpect(jsonPath("$.active", is(true)));

        // 4. Inspect Click Analytics
        mockMvc.perform(get("/api/v1/urls/" + shortCode + "/analytics?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode", is(shortCode)))
                .andExpect(jsonPath("$.totalClicks", is(2)))
                .andExpect(jsonPath("$.totalEvents", is(2)))
                .andExpect(jsonPath("$.events", hasSize(2)));

        // 5. Deactivate link (PATCH)
        UpdateUrlRequest patchRequest = new UpdateUrlRequest(null, false, null);
        mockMvc.perform(patch("/api/v1/urls/" + shortCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        // 6. Verify deactivated link returns 410 Gone on redirect
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isGone());

        // 7. Delete link (DELETE)
        mockMvc.perform(delete("/api/v1/urls/" + shortCode))
                .andExpect(status().isNoContent());

        // 8. Verify deleted link returns 404 Not Found on redirect
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Validation failure returns 400 Bad Request with fieldErrors")
    void shouldReturnBadRequestForInvalidUrl() throws Exception {
        CreateUrlRequest invalidRequest = new CreateUrlRequest("javascript:alert(1)", null);

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.fieldErrors.originalUrl", containsString("Must be a valid HTTP or HTTPS URL")));
    }

    @Test
    @DisplayName("Expired URL returns 410 Gone on redirect")
    void shouldReturnGoneForExpiredUrl() throws Exception {
        Url expiredUrl = new Url("exp1234", "https://example.com", Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60));
        urlRepository.save(expiredUrl);

        mockMvc.perform(get("/exp1234"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status", is(410)))
                .andExpect(jsonPath("$.error", is("Gone")));
    }

    @Test
    @DisplayName("Non-existent URL returns 404 Not Found on redirect")
    void shouldReturnNotFoundForUnknownCode() throws Exception {
        mockMvc.perform(get("/unknown99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    @DisplayName("Listing URLs returns paginated response")
    void shouldListUrlsWithPagination() throws Exception {
        Url url1 = new Url("code111", "https://a.com", Instant.now(), null);
        Url url2 = new Url("code222", "https://b.com", Instant.now(), null);
        urlRepository.save(url1);
        urlRepository.save(url2);

        mockMvc.perform(get("/api/v1/urls?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.totalElements", is(2)));
    }
}
