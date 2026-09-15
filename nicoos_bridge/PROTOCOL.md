# Nico OS GitHub Bridge V2

ChatGPT writes encrypted commands to `nicoos_bridge/inbox.json`.
Nico OS Windows polls that file about every 15 seconds and applies commands to its local SQLite database.

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

## CRITICAL: merge-safe inbox writes

`nicoos_bridge/inbox.json` is append-only from ChatGPT's point of view. Existing command envelopes MUST NOT be removed or replaced when another command is added.

Before every write:
1. Fetch the latest `nicoos_bridge/inbox.json` and its current blob SHA.
2. Parse the existing `commands` array.
3. Append only the new envelope(s), keeping every existing envelope byte-for-byte unchanged.
4. Deduplicate only by exact command `id`; never drop a different existing ID.
5. Update the file using the SHA just fetched.
6. If GitHub reports a SHA/conflict error, fetch the newest file again, merge again, and retry. Never retry with a stale copy.

A ChatGPT tab MUST NOT write a fresh `{ "version":2, "commands":[new_command] }` file that discards the existing queue. Commands intentionally remain in GitHub after local application; Nico OS tracks applied IDs locally and ignores them on later polls.

When several ChatGPT tabs are active, this merge rule is mandatory. A successful GitHub write only confirms that the envelope is in the mailbox; it does not by itself prove the local SQLite mutation has already happened.

Do not place SQL, executable code, secrets, or arbitrary filesystem commands in the inbox.