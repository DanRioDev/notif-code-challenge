# notif-test API

This document describes the HTTP API, request/response DTOs, error shapes, and environment flags.

## Overview
- Base URL: http://localhost:3000
- Content-Type: application/json
- Authentication: none (dev only)
- Async mode: optional via SEND_ASYNC=true (returns 202 Accepted)

## Endpoints

### POST /api/messages
Submit a message for a category; it will be delivered to users subscribed to that category via their preferred channels.

Request body (JSON):
- category: string. One of "sports", "finance", "movies". Case-insensitive keywords like :sports are also accepted.
- messageBody: string (non-empty)

Notes:
- You may also send "message-body" instead of "messageBody"; both are accepted.

Example:
{
  "category": "sports",
  "messageBody": "Hello fans!"
}

Synchronous response (default):
Status: 200 OK
{
  "message": {
    "message-id": 1,
    "message-category": "sports",
    "message-body": "Hello fans!"
  },
  "results": [
    {"user-id": 1, "channel": "email", "status": "success", "log-id": 42},
    {"user-id": 1, "channel": "sms",   "status": "success", "log-id": 43}
  ]
}

Async response (when SEND_ASYNC=true):
Status: 202 Accepted
{
  "status": "enqueued"
}

Error responses:
- 400 Bad Request
  {
    "error": "Invalid category",
    "data": {"category": "unknown", "allowed": [":sports", ":finance", ":movies"]}
  }
- 400 Bad Request (blank message)
  {"error": "Message body must be non-empty"}
- 500 Internal Server Error
  {"error": "<details>"}

### GET /api/logs
Fetch notification logs.

Status: 200 OK
[
  {
    "id": 42,
    "message_id": 1,
    "channel": "email",
    "status": "success",
    "error": null,
    "created_at": "2025-08-07T12:34:56Z"
  }
]

Notes:
- When LOGS_BACKEND=postgres, the shape includes DB fields as shown above.
- When LOGS_BACKEND=memory, the shape may include domain fields like :log-id, :message-id, :category, :user, etc.

## Delivery semantics and retries
- Exponential backoff with jitter is applied for retryable failures.
- Retry classification:
  - Explicit {:retryable? true}
  - Exceptions (info = "exception")
  - Provider response codes 429 and 5xx are treated as retryable
- Maximum retry attempts: 2 (configurable in code)

## Environment variables
- PORT: HTTP port (default 3000)
- SEND_ASYNC: "true" enables async dispatch queue (core.async)
- SEND_WORKERS: integer worker count (default 2)
- SEND_BUFFER: queue capacity (default 100)
- LOGS_BACKEND: "memory" (default) or "postgres"

## Notifier registry and channels
- The system uses a registry to resolve notifiers by channel (:sms, :email, :push).
- In dev, simple in-memory notifiers are used. In production, credentials like SMS_API_KEY, EMAIL_SMTP_URL, PUSH_TOKEN would be injected into the registry.

## Error shapes
- Validation errors use ExceptionInfo with {:error, :data} returned as JSON with 400 status.
- Unhandled server errors return 500 with {"error": "..."}.

