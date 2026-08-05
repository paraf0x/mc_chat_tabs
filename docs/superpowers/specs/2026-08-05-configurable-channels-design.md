# Configurable Channel Model — Design (Sub-project A)

**Date:** 2026-08-05
**Status:** Approved design, spec for implementation planning
**Series:** A of three sub-projects — A: configurable channel model (this spec),
B: notifications & keyboard shortcuts, C: settings page UI.

## Goal

Replace the hardcoded channel set (Global/Local/Kingdom, fixed regexes, fixed
join commands) with a config-driven model: server profiles containing channel
definitions with structured chat formats. Users can add channels for their
server (trade, town, …) without code changes. Sub-project C will put a UI on
top; until then the config is edited as JSON.

## Background

Current state after the kingdom-tab feature:

- `ChatChannel` enum hardcodes GLOBAL (`[G]`, `/g`), LOCAL (`[L]`, `/l`),
  KINGDOM (`[K]`, `/channel join kingdom`), DM, SYSTEM.
- `ChatParser` has one compiled regex per channel, all of the shape
  `^\[X\]\s+(sender)\s*>\s*(content)$`, plus separate DM/system handling.
- `TabManager` builds fixed tabs (All, Local) plus a special-cased kingdom tab
  (hidden until activity, closable) and DM tabs.
- The peek overlay is hardcoded to GLOBAL messages.
- `noteOutgoingCommand` recognizes exactly `/g`, `/l`, `/channel join kingdom`.
- Config is a flat `chattabs.json` (idle switch, peek geometry, chat mode).

## Design

### 1. Data model

`chattabs.json` gains a `profiles` list. Schema (JSON, via the existing Gson
setup):

```json
{
  "profiles": [
    {
      "name": "default",
      "serverAddress": "",
      "separator": ">",
      "channels": [
        { "id": "global",  "displayName": "Global",  "tag": "[G]",
          "joinCommand": "g",                    "alwaysVisible": true,  "peek": true  },
        { "id": "local",   "displayName": "Local",   "tag": "[L]",
          "joinCommand": "l",                    "alwaysVisible": true,  "peek": false },
        { "id": "kingdom", "displayName": "Kingdom", "tag": "[K]",
          "joinCommand": "channel join kingdom", "alwaysVisible": false, "peek": false }
      ]
    }
  ]
}
```

- **Profile:** `name` (unique label), `serverAddress` (empty = default/fallback
  profile), `separator` (profile-wide, default `>`), `channels` (ordered list —
  order defines tab order).
- **Channel:** `id` (unique within profile, lowercase), `displayName`, `tag`
  (literal text that prefixes messages), `joinCommand` (sent without leading
  slash on tab switch; empty string = no auto-switch for this channel),
  `alwaysVisible` (true = fixed tab like Local; false = hidden until activity,
  closable, like today's kingdom tab), `peek` (message lines of this channel
  feed the peek overlay).
- The shipped default profile reproduces today's behavior exactly (see JSON
  above).
- Validation on load: duplicate ids, empty tags, and empty separator are
  rejected with a log warning; the invalid channel (or profile) is skipped.
  If no valid default profile remains, the built-in default is used.

### 2. Parsing

- `ChatParser` builds one pattern per channel at profile-activation time:
  `^\Q<tag>\E\s+(.+?)\s*\Q<separator>\E\s*(.*)$` (tag and separator quoted
  literally). No user-facing regex.
- DM and system detection are unchanged (hardcoded, as decided).
- Parse order: DM patterns first (unchanged), then channel patterns in profile
  order, then DM fallback, then SYSTEM — mirroring today's order.
- `ChatMessage` carries a `channelId` (String, e.g. `"global"`) plus a `kind`
  enum: `CHANNEL`, `DM`, `SYSTEM`. The old `ChatChannel` enum is removed;
  DM/SYSTEM semantics move to `kind`, channel identity moves to `channelId`
  (`null` for DM/SYSTEM).
- Mention detection (own name in content → ping) applies uniformly to all
  CHANNEL messages.

### 3. Tabs and profile switching

- On connect, `TabManager` selects the profile whose `serverAddress` matches
  the joined server address (exact match on host[:port], case-insensitive),
  falling back to the default profile, and (re)builds tabs: the special All
  tab, one tab per channel entry, plus DM tabs (unchanged special case).
- The kingdom show/hide mechanism becomes generic for every
  `alwaysVisible: false` channel: hidden by default, revealed by an incoming
  message of that channel or a manually typed join command, closable via the
  tab's x (history kept, next message re-reveals), closing the active tab
  falls back to All.
- Speech mapping of the All tab: the All tab speaks in the profile's **first
  channel entry** (today: global). Switching to All — including the fallback
  after closing an active tab and the idle timeout — sends that channel's join
  command, reproducing today's `/g` behavior.
- `sendContextSwitchCommand` uses the channel entry's `joinCommand`; the
  "which channel is the server currently in" state (`lastServerChannel`)
  becomes a channel-id string.
- `noteOutgoingCommand` matches typed commands against all join commands of
  the active profile (exact match after normalization, as today).
- The peek overlay collects lines from channels with `peek: true` instead of
  hardcoded GLOBAL; the tag-stripping for peek display uses the channel's
  configured tag.
- Unread/sound behavior in A stays as today: the All tab never counts unread;
  all other channel tabs and DM tabs do. (Per-channel notification settings
  are sub-project B.)

### 4. Migration and operation

- On load, a `chattabs.json` without `profiles` gets the default profile
  appended and is saved back; all existing settings are preserved.
- Editing is by JSON file until sub-project C ships the settings UI.
- New client command `/chattabs profile reload` re-reads the config file and
  rebuilds tabs without restarting the game. Messages already collected stay
  in tabs whose channel id still exists; tabs for removed ids are dropped.

### 5. Testing

- Existing tests are ported to the new model (ChatParserTest, ChatTabTest,
  TabManagerTest — the kingdom-specific tests become tests of the generic
  activity-revealed mechanism).
- New tests: pattern building from tag+separator (incl. regex metacharacters
  in tags), profile selection by address with default fallback, config
  migration from the pre-profile format, validation/skip of malformed entries,
  reveal/close lifecycle for a non-kingdom activity channel, peek collection
  driven by the `peek` flag.
- Build/test via `./gradlew build` (JDK 25); manual smoke test on the target
  server.

## Out of scope (later sub-projects)

- Per-channel notification/mute settings and keyboard shortcuts (B).
- Settings screen with previews and tooltips (C).
- Configurable DM formats (explicitly deferred, DM parsing stays hardcoded).
