# Nico OS GitHub Bridge V2

ChatGPT sends encrypted commands to Nico OS Windows through GitHub.
Nico OS polls `nicoos_bridge/inbox.json` about every 15 seconds and applies new commands to its local SQLite database.

Public key: `nicoos_bridge/public_key.json`.
Encryption: RSA-OAEP SHA-256, message split into chunks of at most 160 UTF-8 bytes before RSA encryption.

Plain payload schema:
```json
{
  "bridge_version": 2,
  "expires_at": "YYYY-MM-DDTHH:MM:SSZ",
  "actions": [ ... ]
}
```

Allowed actions:
- `task_reschedule`: `{type, project_name, selector, due}`
- `task_complete`: `{type, project_name, selector}`
- `task_reopen`: `{type, project_name, selector}`
- `task_create`: `{type, project_name, title, due?, priority?, duration?, notes?}`
- `task_update`: `{type, project_name, selector, fields}`
- `task_delete`: `{type, project_name, selector, explicit_delete:true}`
- `project_update`: `{type, project_name, fields}`
- `budget_update`: `{type, project_name, fields}`

Task selectors:
- current: `{ "mode":"current" }` = earliest open task using Nico OS next-step ordering.
- id: `{ "mode":"id", "id":123 }`
- title: `{ "mode":"title", "title":"Exact title" }`

## Preferred ChatGPT write path: direct append for low latency

For normal ChatGPT use, append the encrypted command directly to `nicoos_bridge/inbox.json` so Nico OS can see it immediately on its next local poll.

Before every direct write:
1. Fetch `nicoos_bridge/public_key.json` immediately before encryption.
2. Build one payload containing all related actions that should be applied together.
3. Use a fresh globally unique command `id`. Never reuse an old ID or old ciphertext.
4. Set `expires_at` far enough in the future for normal PC downtime: default at least 7 days from creation, in UTC.
5. Encrypt with the current public key and strict RSA-OAEP SHA-256 chunking.
6. Fetch the latest `nicoos_bridge/inbox.json` and its current blob SHA immediately before writing.
7. Append only the new envelope, preserving every existing command.
8. Update the inbox with the SHA just fetched.
9. If GitHub reports a SHA/conflict error, fetch the newest inbox, merge again, and retry. Never overwrite a newer queue.
10. Deduplicate only by exact command ID.

This direct append path avoids the GitHub Actions merge delay while remaining multi-tab safe when SHA conflict retry is respected.

### Pending-file fallback

Use a unique one-shot file under `nicoos_bridge/pending/` only when direct inbox update is unavailable or repeatedly conflicts. The inbox guard validates and merges pending files and then removes them.

## Task rollover / duplicate safety

For project task rollovers, do not send cleanup and creation as separate command IDs.

- The local bridge title selector can see historical completed rows as well as open rows. Reused titles can therefore become ambiguous.
- When rolling a project from old open tasks to one new next step, prefer `selector.mode = "current"` (or an exact task ID when known) for the old open tasks.
- Put all required `task_complete` / `task_delete` actions and the single `task_create` action in ONE encrypted payload so the SQLite transaction is atomic.
- Never issue a standalone `task_create` retry after a cleanup failure without first checking whether the new task already exists locally.
- For LE POSTE specifically, do not create another copy of "Trouver et contacter 5 nouveaux producteurs pour LE POSTE" while one open copy already exists.

## Task targeting safety

Wrong task targeting and accidental duplicates are more damaging than a delayed update.

- Prefer selector `id` when a task ID is known.
- Otherwise prefer selector `title` when the task title is known.
- Use selector `current` only when the user explicitly means the current task AND there is no ambiguity about multiple open tasks in that project.
- Never use repeated `current` selectors to clean up suspected duplicates unless the user has confirmed exactly what those open tasks are.
- For a reschedule/correction of an existing task, update or reschedule the existing task instead of creating a second copy.
- When the user says “finish this task and create the next one”, put both actions in the SAME encrypted payload, in that order.
- After a successful GitHub enqueue, do not immediately enqueue the same requested change again just because Nico OS has not refreshed yet. Give the local poller time to consume it first.
- A successful GitHub write proves only that the command reached the mailbox; it does not prove the local SQLite mutation has completed.

## Direct inbox writes: emergency fallback only

If a direct write to `nicoos_bridge/inbox.json` is unavoidable, it is append-only from ChatGPT's point of view.

Before every direct write:
1. Fetch the latest `nicoos_bridge/inbox.json` and its current blob SHA.
2. Parse the existing `commands` array.
3. Append only the new envelope(s), keeping every existing envelope unchanged.
4. Deduplicate only by exact command `id`; never drop a different existing ID.
5. Update the file using the SHA just fetched.
6. If GitHub reports a SHA/conflict error, fetch the newest file again, merge again, and retry. Never retry with a stale copy.

A ChatGPT tab MUST NOT write a fresh `{ "version":2, "commands":[new_command] }` file that discards the existing queue.

## Inbox guard invariants

The GitHub Actions guard is the safety net:
- validates strict Base64 and RSA-2048 ciphertext block length;
- merges one-shot pending commands;
- restores valid previously queued command IDs if a direct inbox write accidentally drops them;
- deduplicates by exact command ID;
- consumes pending files once;
- rejects malformed newly submitted envelopes instead of passing them to Nico OS.

Commands intentionally remain in GitHub after local application; Nico OS tracks applied IDs locally and ignores them on later polls.

Do not place SQL, executable code, secrets, or arbitrary filesystem commands in the inbox.
