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

Do not place SQL, executable code, secrets, or arbitrary filesystem commands in the inbox.