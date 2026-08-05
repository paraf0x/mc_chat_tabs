# Per-Channel Notifications & Tab Shortcuts — Design (Sub-project B)

**Date:** 2026-08-05
**Status:** Approved design, spec for implementation planning
**Series:** B of three sub-projects — A: configurable channel model (done, PR #3),
B: notifications & keyboard shortcuts (this spec), C: settings page UI.

## Goal

Give each channel its own notification settings (sound and unread badge, both
independently switchable) and let the user cycle through tabs from the keyboard
while the chat is open.

## Background

After sub-project A, channels are config entries (`ChannelDefinition`) inside
server profiles, and `TabManager` builds tabs from the active profile.

Current notification behavior, all hardcoded in `TabManager.onMessageReceived`:

- Every tab except All counts unread (`ChatTab.shouldTrackUnread()` = `!isAllTab()`).
- The first non-active tab that receives a message plays a ping; a mention plays
  a louder ping even when no tab counted unread.
- Single-window mode suppresses both.

There are no keyboard shortcuts: `TabManager.setActiveTabById` exists but nothing
calls it, and `ChatScreenMixin.keyPressed` currently only intercepts Up/Down for
chat history.

## Design

### 1. Per-channel notification flags

`ChannelDefinition` gains two booleans, both defaulting to `true`:

```json
{ "id": "global", "displayName": "Global", "tag": "[G]",
  "joinCommand": "g", "alwaysVisible": true, "peek": true,
  "sound": true, "badge": true }
```

- `sound: false` — messages of this channel never play a notification ping.
- `badge: false` — this channel's tab never shows an unread count. The tab stays
  visible and still collects messages.

The two are independent: a channel can badge silently (`sound: false, badge: true`)
or ping without a counter (`sound: true, badge: false`).

Existing configs are unaffected: absent fields keep the `true` defaults through
Gson's no-arg constructor, so behavior stays as it is today.

**Muted means muted.** In a channel with `sound: false`, a mention of the player's
name does not ping either. Mentions still set the mention flag on the message
(used for the louder ping variant in unmuted channels).

Scope: the flags apply to channel tabs. DM tabs and the All tab keep today's
behavior — DM tabs always ping and badge, the All tab never badges.

### 2. Notification logic

`ChatTab` carries the two flags (`isSoundEnabled()`, `isBadgeEnabled()`), taken
from the `ChannelDefinition` in `createChannelTab`; `createDmTab` and
`createAllTab` set both `true`.

In `TabManager.onMessageReceived`, unread counting and sound become independent
gates instead of today's coupled ones:

- Unread: `tab != activeTab && tab.shouldTrackUnread() && !isSingleWindowMode()`,
  where `shouldTrackUnread()` becomes `!isAllTab() && isBadgeEnabled()`.
- Sound: at most one ping per message, played for the first non-active,
  non-All tab with `isSoundEnabled()` — no longer conditional on the unread
  increment.
- The mention fallback (message landed in the active tab) plays only if the
  message's channel allows sound.

Single-window mode keeps suppressing both, as today.

### 3. Tab cycling shortcuts

While the chat screen is open: **Ctrl+Tab** switches to the next visible tab,
**Ctrl+Shift+Tab** to the previous one, wrapping around at both ends. The
shortcuts consume the key event so the chat input does not receive a tab
character, and chat input focus is preserved (same `ChatFocusHelper.refocusImmediate()`
path the Up/Down history shortcuts already use).

Because they are bound to the chat screen only, they never conflict with hotbar
or inventory keys. Modifier detection uses `KeyEvent.hasControlDown()` /
`hasShiftDown()` (available in 26.2 via `InputWithModifiers`).

Switching a tab this way behaves exactly like clicking it: the channel's join
command is sent, so typed messages go to the right channel.

`TabManager` gets `cycleActiveTab(int direction)` operating on `getVisibleTabs()`
(All, visible channels, DMs — the same order the tab bar renders). The unused
`setActiveTabById` is removed.

### 4. Editing

Until the settings page (sub-project C), the flags are edited in
`chattabs.json` and picked up by `/chattabs profile reload` — the same path
sub-project A established for channels. No new commands.

### 5. Testing

Unit tests: badge flag suppresses unread counting; sound flag suppresses the
ping; the two flags are independent; muted channel suppresses the mention ping;
cycling wraps forward and backward over visible tabs and skips hidden ones.

Sound playback itself is not unit-testable (needs a client player), so the tests
assert on a seam: `TabManager` exposes a package-private counter of ping requests
(`soundRequestsForTesting()`), incremented where `playNotificationSound` is
called; the actual playback stays behind the existing null-guard.

Keyboard handling in `ChatScreenMixin` is verified by build plus manual test.

## Out of scope

- Custom mention keywords and a separate mention-ping toggle (considered, not
  requested).
- Per-DM notification settings.
- Direct-jump shortcuts (Ctrl+1…9) — cycling only, by decision.
