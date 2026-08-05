# Kingdom Channel Tab — Design

**Date:** 2026-08-05
**Status:** Draft for review

## Goal

Support the server-side "kingdom" chat channel (joined via `/channel join kingdom`,
messages tagged `[K]`) as an optional chat tab. The channel does not exist on every
server, so the tab must stay hidden until there is actual kingdom activity.

## Background

The mod currently knows three channels, hardcoded in `ChatChannel`: GLOBAL (`[G]`,
`/g`), LOCAL (`[L]`, `/l`), and DM (`✉`, `/tell`). Fixed tabs are All and Local;
DM tabs are created dynamically per player and can be closed. Switching tabs sends
the matching channel command so typed messages land in the right channel
(`TabManager.sendContextSwitchCommand`, deduplicated via `lastServerChannel`).

Observed server format for kingdom messages:

```
[K] [CrimsonHollow] [Lord of the Hollow] UnicornsBane > test
```

Server feedback when switching:

```
[ỉ] Switched mode for channel kingdom from read to write.
```

## Design

### 1. Channel model (`ChatChannel`)

Add `KINGDOM` with chat tag `[K]`. `TabManager.sendContextSwitchCommand` sends
`channel join kingdom` when switching to the Kingdom tab (All → `g`, Local → `l`,
unchanged). The existing `lastServerChannel` deduplication applies.

### 2. Parsing (`ChatParser`)

Add a kingdom pattern analogous to the global one:

```
^\[K\]\s+(.+?)\s*>\s*(.*)$
```

For the sample line above, the sender group captures
`[CrimsonHollow] [Lord of the Hollow] UnicornsBane`. Mention detection (own player
name appears in the message content → ping) is applied to kingdom messages the
same way it is for global messages.

### 3. Kingdom tab — optional and closable

The tab object exists internally at all times and always collects `[K]` messages
(history is preserved), but it is **hidden by default** and absent from the tab
bar. It becomes visible when:

1. a `[K]` message arrives — with unread badge and notification sound, or
2. the user manually types `/channel join kingdom` — the tab appears immediately
   on switch, before any message round-trips.

Closing: the tab gets a close button like DM tabs. Closing hides it (history is
kept); the next `[K]` message brings it back. If the tab is closed while active,
the mod switches to the All tab and sends `/g`, so the user speaks in global
again.

On servers without the kingdom channel neither trigger ever fires, so the tab bar
stays exactly as today (All, Local, DMs). No config option, nothing to set up per
server.

When visible, the tab is ordered after Local and before the DM tabs. Keyboard
shortcuts (`setActiveTabByIndex`) and the tab bar pick it up through the existing
tab list mechanisms.

Behavior mirrors the Local tab: own message history, unread badge, and
notification sound when inactive (`shouldTrackUnread` includes the kingdom tab).

### 4. Manual command detection (side-effect fix)

The existing `handleChatInput` injection in `ChatScreenMixin` (which currently
passes commands through untouched) detects manually typed channel-switch
commands — `/channel join kingdom`, `/g`, `/l` — and updates
`TabManager.lastServerChannel` accordingly. For `/channel join kingdom` it also
reveals the Kingdom tab (trigger 2 above).

This fixes an existing bug: today a manual `/g` or `/l` leaves
`lastServerChannel` stale, so the next tab click wrongly skips sending the switch
command.

### 5. Unchanged behavior

- Peek overlay remains global-only; `[K]` messages never appear in it.
- The server feedback line (`[ỉ] Switched mode …`) stays SYSTEM and shows only in
  the All tab.
- Idle timeout still switches back to All (sending `/g`).
- DM handling, single-window mode, and layout editor are untouched.

## Error handling

- Unknown `[X]` tags other than `[K]` keep falling through to SYSTEM as today.
- If the server rejects `channel join kingdom` (channel missing), the error line
  is SYSTEM and shows in the All tab; the mod does not retry.

## Testing

- `./gradlew build` (JDK 25) must pass.
- Manual verification on the target server: `[K]` lines reveal the tab and land in
  it; clicking the tab sends `/channel join kingdom`; closing hides it and `/g` is
  sent when it was active; a new `[K]` message re-reveals it; on a server without
  the channel the tab never appears.
