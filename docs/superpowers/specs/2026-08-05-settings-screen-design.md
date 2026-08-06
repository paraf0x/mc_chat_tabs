# Settings Screen — Design (Sub-project C)

**Date:** 2026-08-05
**Status:** Approved design, spec for implementation planning
**Series:** C of three sub-projects — A: configurable channel model (merged),
B: notifications & shortcuts (merged), C: settings screen (this spec).

## Goal

Make everything the mod stores editable in-game through a settings screen with
previews and tooltips, replacing the JSON-file-plus-reload workflow that
sub-projects A and B left behind.

## Background

The mod currently stores: chat mode (filtered/single-window), idle auto-switch
seconds, peek on/off, peek lines, peek/chat geometry, and a list of server
profiles whose channels carry id, display name, tag, join command,
`alwaysVisible`, `peek`, `sound`, `badge`.

Editing today means opening `chattabs.json` in a text editor and running
`/chattabs profile reload`, or using the scattered `/chattabs idle|mode|peek`
subcommands. The layout editor (`PeekPositionScreen`) is already a graphical
screen and stays as it is.

Verified available in 26.2: `Screen` (with `extractRenderState`), `Button.builder`,
`Checkbox.builder`, `CycleButton.onOffBuilder`, `EditBox`, `Tooltip.create`,
`AbstractWidget.setTooltip`, `ContainerObjectSelectionList`. ModMenu 20.0.1
supports 26.2 and is on `maven.terraformersmc.com`.

## Design

### 1. Entry points

- `/chattabs settings` opens the screen (scheduled via `Minecraft.execute`, like
  the existing `/chattabs layout` command).
- **ModMenu**: a `modmenu` entrypoint returns the settings screen, so the mod
  shows a configure button in ModMenu's mod list. ModMenu is a `compileOnly`
  dependency and listed under `suggests` — the mod works without it, and the
  entrypoint class is only loaded when ModMenu is present.

The existing `/chattabs idle|mode|peek|profile|layout` subcommands stay for
scripting and muscle memory.

### 2. Screen structure

**`ChatTabsSettingsScreen`** (main), top to bottom:

- **Profile row**: a cycle button selecting which profile is being edited
  (`default`, plus any server-specific ones). When connected to a server that
  has no profile yet, a "Create profile for this server" button appears and
  seeds a copy of the currently selected profile with that address.
- **General section**: chat mode (Filtered / Single window), idle auto-switch
  (Never / 15s / 30s / 60s / 120s / 300s), peek overlay on/off, peek lines
  (1–10), and the profile's message separator (text field, default `>`).
- **Channels section**: a scrollable list, one row per channel:
  `Name  [tag]   V  S  B  P   Edit   ✕`
  where V/S/B/P are on-off toggles for always-visible, sound, badge, and peek.
  Below it: "Add channel", which opens the editor on a new channel.
- **Layout button**: opens the existing `PeekPositionScreen`.
- **Footer**: "Done" (save and apply) and "Cancel" (discard).

**`ChannelEditScreen`** (sub-screen), reached from Edit or Add:

- Text fields: display name, chat tag, join command, and channel id.
- Toggles: always visible, sound, badge, peek — same four as the list row.
- **Live preview**: a sample chat line, rebuilt on every keystroke, showing how
  the current tag and separator parse it. The matched tag, sender, and message
  parts are rendered in distinct colors; if the pattern does not match, the line
  turns red with a "does not match" note. The sample text itself is an editable
  field pre-filled with a realistic line (`[Rank] PlayerName > message text`), so
  a user can paste a real line from their server and see immediately whether the
  settings capture it.
- **Inline validation**: duplicate id, blank id or tag, and the reserved id
  `all` are reported under the fields; "Done" stays disabled while invalid.

Every control gets a `Tooltip` explaining what it does — for example, badge:
"Show an unread counter on this tab", peek: "Show this channel in the peek
overlay while another tab is active".

### 3. Working copy and saving

Editing never mutates live config objects. Opening the screen builds a
`SettingsDraft`: deep copies of all profiles plus the global values. The screens
mutate the draft; "Done" commits it into `ChatTabsConfig`, saves the file, and
re-applies the profile matching the connected server so tabs, parser, and peek
pick up the changes without a reload command. "Cancel" drops the draft.

Committing rebuilds tabs through the existing `TabManager.applyProfile`, which
preserves collected messages for channel ids that still exist.

### 4. Isolation of testable logic

The screens themselves need a running client, so the logic lives outside them in
three small units:

- **`ChannelValidation.validate(candidate, others)`** → an error message or
  empty. Rules: id and tag non-blank, id not `all`, id unique among the other
  channels of the same profile.
- **`ChannelPreview.of(tag, separator, sampleLine)`** → a record with `matched`,
  `sender`, `content`. Uses the same pattern builder the parser uses, so preview
  and runtime behavior cannot drift.
- **`SettingsDraft`** — holds the working copies, offers add/remove/update for
  channels and profiles, and `commit(ChatTabsConfig)`.

`ChatParser.buildChannelPattern` becomes visible to `ChannelPreview` (it is
already package-private for tests; it moves to public or gets a small public
wrapper) so there is exactly one pattern definition in the codebase.

### 5. Testing

Unit tests cover the three units above: validation rules including the reserved
id and duplicates; preview matching, non-matching, and tags containing regex
metacharacters; draft commit round-trip (edit a channel, commit, read it back
from the config) and that cancelling leaves the config untouched.

The screens are verified by build plus manual in-game testing.

## Out of scope

- Editing DM behavior (DM parsing stays hardcoded, as decided in A).
- Reordering channels by drag and drop — the list follows config order, and
  reordering can be added later if it turns out to matter.
- Localization: the screen ships English strings, matching the rest of the mod.
