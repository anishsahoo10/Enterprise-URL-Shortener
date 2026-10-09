# Enterprise URL Shortener — API Examples & Specification

Base Path for Management APIs: `http://localhost:8080/api/v1`  
Redirect Root Route: `http://localhost:8080/{shortCode}`

---

## 1. Create a Short URL

### Request (POST `/api/v1/urls`)
```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl": "https://developer.mozilla.org/en-US/docs/Web/HTTP",
    "expiresAt": "2028-12-31T23:59:59Z"
  }'
```

### Response (`201 Created`)
```json
{
  "shortCode": "k8P2qZa",
  "shortUrl": "http://localhost:8080/k8P2qZa",
  "originalUrl": "https://developer.mozilla.org/en-US/docs/Web/HTTP",
  "createdAt": "2026-10-09T18:00:00Z",
  "expiresAt": "2028-12-31T23:59:59Z",
  "active": true
}
```

---

## 2. Validation Error Handling

Invalid URL schemes (e.g., `javascript:`, `ftp:`, `data:`, or blank) return HTTP 400 with field-level details.

### Request
```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl": "javascript:alert('XSS')"
  }'
```

### Response (`400 Bad Request`)
```json
{
  "timestamp": "2026-10-09T18:01:23.456Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/v1/urls",
  "fieldErrors": {
    "originalUrl": "Must be a valid HTTP or HTTPS URL"
  }
}
```

---

## 3. Redirect to Original Destination

When a client opens the short link, the service inspects cache/database, increments the atomic click counter, records the click event, and returns HTTP 302.

### Request (GET `/{shortCode}`)
```bash
curl -i -X GET http://localhost:8080/k8P2qZa \
  -H "Referer: https://twitter.com" \
  -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
```

### Response (`302 Found`)
```http
HTTP/1.1 302 Found
Location: https://developer.mozilla.org/en-US/docs/Web/HTTP
Content-Length: 0
```

---

## 4. Expired or Deactivated Link Redirect

If a link is inactive or past its expiration date, it does not redirect and returns `410 Gone`.

### Response (`410 Gone`)
```json
{
  "timestamp": "2026-10-09T18:02:11.123Z",
  "status": 410,
  "error": "Gone",
  "message": "The requested short link is expired or inactive: k8P2qZa",
  "path": "/k8P2qZa"
}
```

---

## 5. Non-Existent Code Redirect

### Response (`404 Not Found`)
```json
{
  "timestamp": "2026-10-09T18:02:45.789Z",
  "status": 404,
  "error": "Not Found",
  "message": "Short URL not found for code: nonExistent",
  "path": "/nonExistent"
}
```

---

## 6. Inspect URL Details

### Request (GET `/api/v1/urls/{shortCode}`)
```bash
curl -i -X GET http://localhost:8080/api/v1/urls/k8P2qZa
```

### Response (`200 OK`)
```json
{
  "shortCode": "k8P2qZa",
  "shortUrl": "http://localhost:8080/k8P2qZa",
  "originalUrl": "https://developer.mozilla.org/en-US/docs/Web/HTTP",
  "createdAt": "2026-10-09T18:00:00Z",
  "expiresAt": "2028-12-31T23:59:59Z",
  "active": true,
  "clickCount": 14
}
```

---

## 7. Inspect Click Analytics

### Request (GET `/api/v1/urls/{shortCode}/analytics?page=0&size=20`)
```bash
curl -i -X GET "http://localhost:8080/api/v1/urls/k8P2qZa/analytics?page=0&size=20"
```

### Response (`200 OK`)
```json
{
  "shortCode": "k8P2qZa",
  "totalClicks": 14,
  "events": [
    {
      "clickedAt": "2026-10-09T18:05:12Z",
      "referrer": "https://twitter.com"
    },
    {
      "clickedAt": "2026-10-09T18:03:40Z",
      "referrer": "https://linkedin.com"
    }
  ],
  "page": 0,
  "size": 20,
  "totalEvents": 14
}
```

---

## 8. List URLs with Pagination

### Request (GET `/api/v1/urls?page=0&size=10`)
```bash
curl -i -X GET "http://localhost:8080/api/v1/urls?page=0&size=10"
```

### Response (`200 OK`)
```json
{
  "content": [
    {
      "shortCode": "k8P2qZa",
      "shortUrl": "http://localhost:8080/k8P2qZa",
      "originalUrl": "https://developer.mozilla.org/en-US/docs/Web/HTTP",
      "createdAt": "2026-10-09T18:00:00Z",
      "expiresAt": "2028-12-31T23:59:59Z",
      "active": true
    }
  ],
  "page": {
    "size": 10,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## 9. Update / Deactivate a URL

Modifications immediately evict any cached Redis entry.

### Request (PATCH `/api/v1/urls/{shortCode}`)
```bash
curl -i -X PATCH http://localhost:8080/api/v1/urls/k8P2qZa \
  -H "Content-Type: application/json" \
  -d '{
    "active": false
  }'
```

### Response (`200 OK`)
```json
{
  "shortCode": "k8P2qZa",
  "shortUrl": "http://localhost:8080/k8P2qZa",
  "originalUrl": "https://developer.mozilla.org/en-US/docs/Web/HTTP",
  "createdAt": "2026-10-09T18:00:00Z",
  "expiresAt": "2028-12-31T23:59:59Z",
  "active": false,
  "clickCount": 14
}
```

---

## 10. Delete a URL

Hard deletion deletes the URL, cascades click events, and evicts the cache entry.

### Request (DELETE `/api/v1/urls/{shortCode}`)
```bash
curl -i -X DELETE http://localhost:8080/api/v1/urls/k8P2qZa
```

### Response (`204 No Content`)

---

## 11. Health Check Endpoint

### Request (GET `/actuator/health`)
```bash
curl -i -X GET http://localhost:8080/actuator/health
```

### Response (`200 OK`)
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL"
      }
    },
    "redis": {
      "status": "UP"
    }
  }
}
```
