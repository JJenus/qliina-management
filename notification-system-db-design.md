# Notification System — Database Design

## 1. Overview

Users own preferences and devices. **Notifications** is the hub: a logical
event ("this user should be told X"), created from a user + a template. It
fans out into one **delivery** row per channel (email, SMS, push, in-app),
and each delivery accumulates an append-only stream of **delivery_events**
(sent, delivered, opened, clicked, failed).

```
users ──┬─< preferences
        ├─< devices ──────┐
        └─< notifications ┤
                 │  \      │
              templates    │
                 │         │
                 └─< deliveries >── devices
                          │
                          └─< delivery_events
```

The key design decision: **notification** (the decision to notify) is a
separate concept from **delivery** (one channel's attempt to actually send
it). This is what lets a single trigger produce an email attempt *and* a
push attempt with independent, trackable success/failure states, instead of
conflating "we decided to notify" with "we successfully sent an email."

---

## 2. Core tables

### users
| column | type | notes |
|---|---|---|
| id | PK | |
| email | string | |
| phone | string, nullable | |

### preferences
| column | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK -> users | |
| category | string | e.g. `order_updates`, `marketing` |
| channel | enum | `email`, `sms`, `push`, `in_app` |
| enabled | bool | |

### templates
| column | type | notes |
|---|---|---|
| id | PK | |
| key | string | e.g. `order_shipped` |
| channel | enum | which channel this template renders for |
| body | text | |
| is_mandatory | bool, default false | bypasses preference check — see §3 |

### notifications (hub)
| column | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK -> users | |
| template_id | FK -> templates | |
| category | string | |
| status | enum | |

### devices
| column | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK -> users | |
| push_token | string | |
| platform | enum | ios, android, web |
| is_valid | bool, default true | flipped false on a hard-invalid response — see §4 |

### deliveries
| column | type | notes |
|---|---|---|
| id | PK | |
| notification_id | FK -> notifications | |
| device_id | FK -> devices, nullable | set for push deliveries |
| channel | enum | |
| status | enum | `pending`, `sent`, `failed`, `exhausted` |
| attempt_count | int, default 0 | |
| max_attempts | int, default 5 | |
| next_retry_at | timestamp, nullable | |
| last_error | text, nullable | |
| last_error_type | enum, nullable | `transient`, `permanent` — see §4 |

### delivery_events
| column | type | notes |
|---|---|---|
| id | PK | |
| delivery_id | FK -> deliveries | |
| event_type | enum | `sent`, `delivered`, `opened`, `clicked`, `failed` |
| occurred_at | timestamp | |

---

## 3. Preference-checking before fan-out

A decision step in application code between "notification created" and
"deliveries created" — not a stored relationship.

**Flow when a notification is triggered:**
1. Load the notification's category from its template.
2. Query the user's channel-level preferences for that category.
3. Apply a default for any channel with no explicit row (opt-out systems
   default to enabled; opt-in categories like marketing default to disabled).
4. For each channel that resolves to enabled, create a delivery row.
5. Skip creating a delivery at all for disabled channels — don't create one
   and mark it "skipped," that just adds noise to `delivery_events` that
   every analytics query then has to filter out.

**Mandatory notifications** (security alerts, password resets) should
bypass the preference check entirely — that's what `templates.is_mandatory`
is for. If true, skip the preference query and fan out to every channel the
user has a valid device/address for.

Centralize this logic in one function every fan-out path calls
(`resolvePreference(userId, category, channel)`). Letting preference logic
drift between the main notification service and, say, a bulk-export job is
a classic way to send someone something they explicitly opted out of.

---

## 4. Retry and backoff for failed deliveries

Retry state lives on `deliveries` itself, not a separate table — it's the
current mutable state a retry job needs to query quickly
(`WHERE status='failed' AND next_retry_at <= now()`), which is what an
indexed column gives you that scanning `delivery_events` history wouldn't.

**Retry loop (scheduled job, not inline in the request path):**
1. Select failed deliveries with `attempt_count < max_attempts` and
   `next_retry_at <= now()`.
2. Attempt the send.
3. Success → `status='sent'`, insert a `delivery_events` row.
4. Failure → increment `attempt_count`, set `last_error` /
   `last_error_type`, compute the next backoff, insert a `delivery_events`
   row.
5. If `attempt_count >= max_attempts` → `status='exhausted'`, stop.

**Backoff** — exponential with jitter, computed in application code rather
than a config table (don't build that abstraction until you have more than
one retry strategy):
```python
def next_retry_delay(attempt_count):
    base = min(2 ** attempt_count, 300)  # cap at 5 min
    jitter = random.uniform(0, base * 0.2)
    return base + jitter
```

**Don't retry blindly — branch on failure type:**
- A rate-limit response should retry. An invalid/uninstalled push token
  should not — it should immediately flip `devices.is_valid = false`
  instead of burning attempts on a token that will never work.
- Email/SMS providers distinguish soft bounces (retry) from hard bounces
  (don't retry, consider suppressing future sends). `last_error_type`
  (`transient` vs `permanent`) lets the retry job branch on cause instead of
  retrying uniformly for `max_attempts` regardless of why it failed.

---

## 5. Queue integration — optional, but very important

**This layer is optional in the sense that the schema above works without
it** — a synchronous call path (create notification → create deliveries →
call provider directly) is a valid starting point. **It is very important
in the sense that it's usually the first thing that breaks under real
load or a provider outage**, and retrofitting it later means reworking
your write path, not just adding infrastructure. If you're building for
any real traffic, treat this as part of the design from day one rather
than a later optimization.

**Why it matters:** without a queue, the request that triggers a
notification (checkout completing, password reset) waits synchronously on
an external provider call. If that provider is slow or down, the triggering
request — not just the notification — goes down with it.

**The flow:**

1. **App event** — application code doesn't call a provider directly. It
   publishes a small message (`user_id`, `template_key`, `context`). This
   is the only synchronous part, and it's fast — just an enqueue.
2. **Queue** — durable storage for the message until a worker is free.
   If every worker crashes, nothing is lost — it's still there when they
   recover.
3. **Worker pool** — pulls messages, does the domain work: creates the
   `notifications` row, runs the preference check (§3), creates one
   `deliveries` row per enabled channel.
4. **Provider dispatch** — for each delivery, the worker calls out to
   whatever actually sends the email/SMS/push. This is the slow,
   failure-prone part you don't want blocking the original request.
5. **Webhook receiver** — most providers report final status
   asynchronously after the initial call returns. A small endpoint
   receives these callbacks and writes them as `delivery_events` rows —
   the same delivery can get multiple callbacks over time (sent →
   delivered → opened), each just another event.

**Failure path — dead-letter queue.** When a worker exhausts its retry
budget on a message (not to be confused with the delivery-level retries in
§4 — see distinction below), route it to a separate holding queue for
manual inspection rather than dropping it or looping forever.

**Ordering matters.** Route messages so everything for the same user lands
on the same processing lane (most queue systems support routing/grouping by
a key). Without this, two workers can process two notifications for the
same user concurrently, both reading stale preference state or writing
overlapping `deliveries` rows out of order. `user_id` is a natural
grouping key.

**Two retry mechanisms, not one — keep them distinct:**
- *Queue-level redelivery*: the worker didn't finish processing the
  message in time (crash, timeout) — the queue itself redelivers it.
- *Application-level retry* (§4): the worker finished, created the
  delivery, and the provider call failed — that's tracked on the
  `deliveries` row and handled by a separate scheduled retry job.

Conflating these two leads to double-processing bugs — a message retried
by the queue *and* independently retried by the delivery-retry job for the
same underlying send.
