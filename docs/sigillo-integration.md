# Sigillo receipts in 砚团

This integration adapts the mechanism from **Sigillo** by **Cu & Lunedì**:
https://github.com/29-Cu/sigillo

Original project license: **CC BY 4.0**.

## What is kept

- Human-written stars and notes are stored as observations, not converted into commands.
- The same `(dim, tag)` rated at least 4 stars in the latest two human-filled receipts is temporarily benched from the next receipt.
- The companion can pin a short `agent_note` to a sealed receipt for the next relevant context.
- Receipts are sealed: once submitted from the Android card they are read-only.
- Data is local to the Android app's SharedPreferences and capped at the latest 200 receipts.

## What is different

The official ChatGPT app does not route its chat requests through 砚团, so 砚团 cannot silently insert a new system message into an existing official ChatGPT conversation.

Instead this repository exposes MCP tools:

- `sigillo_create`
- `sigillo_recent`
- `sigillo_context`
- `sigillo_note`
- `sigillo_get`
- `sigillo_open`

The companion can open a receipt on the phone, read the local sealed receipts through MCP, request a compact context block, and write its private note back to the same local receipt.

No Sigillo receipt text is uploaded as part of the normal phone/life-state sync.
