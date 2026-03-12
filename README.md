# Chat Tabs

A client-side Fabric mod that adds tabbed chat to Minecraft. Automatically sorts messages into **All**, **Local**, and per-player **DM** tabs based on server chat prefixes (`[G]`, `[L]`, `/tell`).

Built for servers that use `[G]`/`[L]` channel prefixes and `/tell` for direct messages. Tested on Lunar Client (1.21.11).

## Features

- **All / Local tabs** — filter chat by `[G]` global and `[L]` local channels
- **Auto DM tabs** — new tabs appear automatically when you send or receive a `/tell` message, with a close button to dismiss them
- **Unread indicators** — orange badge with count on inactive tabs
- **Sound notifications** — pling sound when messages arrive in inactive tabs; louder distinct pling for name mentions
- **Name mention detection** — detects your player name in `[G]` messages and plays an alert
- **Auto channel switching** — selecting a tab sends the appropriate `/g` or `/l` command to the server
- **Chat Heads compatibility** — strips `[PlayerName head]` placeholders before parsing

## Installation

Requires [Fabric Loader](https://fabricmc.net/) and [Fabric API](https://modrinth.com/mod/fabric-api).

1. Download the latest JAR from [Releases](https://github.com/paraf0x/mc_chat_tabs/releases)
2. Drop it into your `mods/` folder
3. Launch the game

## Building

```
./gradlew build
```

The output JAR is in `build/libs/`.

## License

[MIT](LICENSE)
