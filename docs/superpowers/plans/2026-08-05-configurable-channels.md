# Configurable Channel Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hardcoded channel set (Global/Local/Kingdom) with config-driven server profiles whose channels define tag, join command, visibility, and peek behavior.

**Architecture:** New config POJOs (`ChannelDefinition`, `ServerProfile`) live in a new `com.chattabs.config` package and are stored in the existing `chattabs.json` via Gson. The `ChatChannel` enum is removed: `ChatMessage` carries a `MessageKind` (CHANNEL/DM/SYSTEM) plus a `channelId` string; `ChatParser` compiles per-channel patterns from tag+separator at profile-activation time. `TabManager` builds tabs from the active profile and generalizes the kingdom show/hide mechanism to every `alwaysVisible: false` channel. A Fabric JOIN hook selects the profile by server address.

**Tech Stack:** Java 25, Fabric (Minecraft 26.2, loader 0.19.3, Fabric API 0.156.0+26.2), Gson (bundled), JUnit 5 + fabric-loader-junit (already set up).

**Spec:** `docs/superpowers/specs/2026-08-05-configurable-channels-design.md`

## Global Constraints

- All Gradle commands need JDK 25: prefix with `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home` (system default is JDK 21).
- Minecraft 26.2 is unobfuscated — no mappings; use `net.minecraft.*` names directly.
- The shipped default profile must reproduce today's behavior exactly: global (`[G]`, `g`, always visible, peek), local (`[L]`, `l`, always visible), kingdom (`[K]`, `channel join kingdom`, activity-revealed).
- Channel patterns are built from structured fields only (tag + separator, both `Pattern.quote`d) — no user-facing regex.
- DM and SYSTEM parsing stay exactly as they are today.
- Channel id `"all"` is reserved (the All tab) and must be rejected by validation.
- Commit messages end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Work on a feature branch (e.g. `configurable-channels`), not on main.

---

### Task 1: Config POJOs — ChannelDefinition and ServerProfile

Pure data classes, no Minecraft dependencies. Validation lives here.

**Files:**
- Create: `src/main/java/com/chattabs/config/ChannelDefinition.java`
- Create: `src/main/java/com/chattabs/config/ServerProfile.java`
- Test: `src/test/java/com/chattabs/config/ServerProfileTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces (used by Tasks 2–6):
  - `ChannelDefinition(String id, String displayName, String tag, String joinCommand, boolean alwaysVisible, boolean peek)` + no-arg ctor for Gson; getters `getId()`, `getDisplayName()`, `getTag()`, `getJoinCommand()` (never null, may be empty), `isAlwaysVisible()`, `isPeek()`; `boolean isValid()`.
  - `ServerProfile` with getters `getName()`, `getServerAddress()`, `getSeparator()`; `boolean isDefaultProfile()`, `boolean matchesAddress(String address)`, `List<ChannelDefinition> validChannels()`, `ChannelDefinition channelById(String id)` (null if absent), `static ServerProfile builtInDefault()`, and a ctor `ServerProfile(String name, String serverAddress, String separator, List<ChannelDefinition> channels)` + no-arg ctor for Gson.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/chattabs/config/ServerProfileTest.java`:

```java
package com.chattabs.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerProfileTest {

    @Test
    void builtInDefaultReproducesTodaysChannels() {
        ServerProfile profile = ServerProfile.builtInDefault();
        assertTrue(profile.isDefaultProfile());
        assertEquals(">", profile.getSeparator());

        List<ChannelDefinition> channels = profile.validChannels();
        assertEquals(3, channels.size());

        ChannelDefinition global = channels.get(0);
        assertEquals("global", global.getId());
        assertEquals("[G]", global.getTag());
        assertEquals("g", global.getJoinCommand());
        assertTrue(global.isAlwaysVisible());
        assertTrue(global.isPeek());

        ChannelDefinition local = channels.get(1);
        assertEquals("local", local.getId());
        assertEquals("l", local.getJoinCommand());
        assertTrue(local.isAlwaysVisible());
        assertFalse(local.isPeek());

        ChannelDefinition kingdom = channels.get(2);
        assertEquals("kingdom", kingdom.getId());
        assertEquals("[K]", kingdom.getTag());
        assertEquals("channel join kingdom", kingdom.getJoinCommand());
        assertFalse(kingdom.isAlwaysVisible());
    }

    @Test
    void validChannelsSkipsInvalidAndDuplicateEntries() {
        ServerProfile profile = new ServerProfile("test", "", ">", List.of(
                new ChannelDefinition("trade", "Trade", "[T]", "", true, false),
                new ChannelDefinition("", "NoId", "[X]", "", true, false),          // blank id
                new ChannelDefinition("notag", "NoTag", "", "", true, false),        // blank tag
                new ChannelDefinition("all", "Reserved", "[A]", "", true, false),    // reserved id
                new ChannelDefinition("trade", "Duplicate", "[T2]", "", true, false) // duplicate id
        ));
        List<ChannelDefinition> valid = profile.validChannels();
        assertEquals(1, valid.size());
        assertEquals("trade", valid.get(0).getId());
    }

    @Test
    void matchesAddressIsExactAndCaseInsensitive() {
        ServerProfile profile = new ServerProfile("srv", "Play.Example.net", ">", List.of());
        assertTrue(profile.matchesAddress("play.example.net"));
        assertFalse(profile.matchesAddress("play.example.net:25565"));
        assertFalse(profile.matchesAddress("other.example.net"));
        assertFalse(profile.matchesAddress(null));
        assertFalse(profile.isDefaultProfile());
    }

    @Test
    void channelByIdFindsValidChannelsOnly() {
        ServerProfile profile = ServerProfile.builtInDefault();
        assertNotNull(profile.channelById("kingdom"));
        assertNull(profile.channelById("nope"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -5`
Expected: compile error — `ServerProfile`/`ChannelDefinition` do not exist.

- [ ] **Step 3: Implement the POJOs**

Create `src/main/java/com/chattabs/config/ChannelDefinition.java`:

```java
package com.chattabs.config;

public class ChannelDefinition {
    private String id = "";
    private String displayName = "";
    private String tag = "";
    private String joinCommand = "";
    private boolean alwaysVisible = true;
    private boolean peek = false;

    public ChannelDefinition() {
    }

    public ChannelDefinition(String id, String displayName, String tag, String joinCommand,
                             boolean alwaysVisible, boolean peek) {
        this.id = id;
        this.displayName = displayName;
        this.tag = tag;
        this.joinCommand = joinCommand;
        this.alwaysVisible = alwaysVisible;
        this.peek = peek;
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public String getDisplayName() {
        return displayName == null || displayName.isBlank() ? getId() : displayName;
    }

    public String getTag() {
        return tag == null ? "" : tag;
    }

    public String getJoinCommand() {
        return joinCommand == null ? "" : joinCommand;
    }

    public boolean isAlwaysVisible() {
        return alwaysVisible;
    }

    public boolean isPeek() {
        return peek;
    }

    public boolean isValid() {
        return !getId().isBlank() && !getTag().isBlank() && !getId().equalsIgnoreCase("all");
    }
}
```

Create `src/main/java/com/chattabs/config/ServerProfile.java`:

```java
package com.chattabs.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ServerProfile {
    private String name = "default";
    private String serverAddress = "";
    private String separator = ">";
    private List<ChannelDefinition> channels = new ArrayList<>();

    public ServerProfile() {
    }

    public ServerProfile(String name, String serverAddress, String separator, List<ChannelDefinition> channels) {
        this.name = name;
        this.serverAddress = serverAddress;
        this.separator = separator;
        this.channels = new ArrayList<>(channels);
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public String getServerAddress() {
        return serverAddress == null ? "" : serverAddress;
    }

    public String getSeparator() {
        return separator == null || separator.isBlank() ? ">" : separator;
    }

    public boolean isDefaultProfile() {
        return getServerAddress().isBlank();
    }

    public boolean matchesAddress(String address) {
        if (address == null || isDefaultProfile()) {
            return false;
        }
        return getServerAddress().toLowerCase(Locale.ROOT).equals(address.toLowerCase(Locale.ROOT));
    }

    /** Valid channels in config order; invalid entries and duplicate ids are skipped. */
    public List<ChannelDefinition> validChannels() {
        List<ChannelDefinition> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (channels == null) {
            return result;
        }
        for (ChannelDefinition channel : channels) {
            if (channel == null || !channel.isValid()) {
                continue;
            }
            if (!seen.add(channel.getId().toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(channel);
        }
        return result;
    }

    public ChannelDefinition channelById(String id) {
        if (id == null) {
            return null;
        }
        for (ChannelDefinition channel : validChannels()) {
            if (channel.getId().equalsIgnoreCase(id)) {
                return channel;
            }
        }
        return null;
    }

    public static ServerProfile builtInDefault() {
        return new ServerProfile("default", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, true),
                new ChannelDefinition("local", "Local", "[L]", "l", true, false),
                new ChannelDefinition("kingdom", "Kingdom", "[K]", "channel join kingdom", false, false)
        ));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/chattabs/config/ src/test/java/com/chattabs/config/
git commit -m "Add channel definition and server profile config model

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Profiles in ChatTabsConfig — storage, migration, address lookup

**Files:**
- Modify: `src/main/java/com/chattabs/ChatTabsConfig.java`
- Test: `src/test/java/com/chattabs/ChatTabsConfigTest.java` (create)

**Interfaces:**
- Consumes: `ServerProfile.builtInDefault()`, `ServerProfile.matchesAddress(String)`, `ServerProfile.isDefaultProfile()` (Task 1).
- Produces (used by Tasks 3, 5):
  - `public List<ServerProfile> getProfiles()`
  - `public ServerProfile profileForAddress(String addressOrNull)` — first profile matching the address, else first default profile, else `ServerProfile.builtInDefault()`.
  - `public static void reload()` — re-reads the config file into the singleton.
  - `normalize()` becomes package-private and returns `boolean` (true if it changed anything) so migration is testable and `load()` can save back migrated configs.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/chattabs/ChatTabsConfigTest.java`:

```java
package com.chattabs;

import com.chattabs.config.ChannelDefinition;
import com.chattabs.config.ServerProfile;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatTabsConfigTest {

    private static final Gson GSON = new Gson();

    @Test
    void legacyConfigWithoutProfilesGetsDefaultProfile() {
        ChatTabsConfig config = GSON.fromJson("{\"idleSwitchSeconds\": 30}", ChatTabsConfig.class);
        boolean changed = config.normalize();

        assertTrue(changed);
        assertEquals(30, config.getIdleSwitchSeconds());
        assertEquals(1, config.getProfiles().size());
        assertEquals("global", config.getProfiles().get(0).validChannels().get(0).getId());
    }

    @Test
    void normalizeIsIdempotentOnMigratedConfig() {
        ChatTabsConfig config = GSON.fromJson("{}", ChatTabsConfig.class);
        config.normalize();
        assertFalse(config.normalize());
    }

    @Test
    void profileForAddressPrefersExactMatchThenDefault() {
        ChatTabsConfig config = GSON.fromJson("{}", ChatTabsConfig.class);
        config.normalize();
        config.getProfiles().add(new ServerProfile("srv", "play.example.net", ">",
                java.util.List.of(new ChannelDefinition("trade", "Trade", "[T]", "", true, false))));

        assertEquals("srv", config.profileForAddress("PLAY.EXAMPLE.NET").getName());
        assertEquals("default", config.profileForAddress("other.server").getName());
        assertEquals("default", config.profileForAddress(null).getName());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -5`
Expected: compile error — `getProfiles`/`profileForAddress` not defined, `normalize` not visible.

- [ ] **Step 3: Implement in ChatTabsConfig**

In `src/main/java/com/chattabs/ChatTabsConfig.java`:

Add imports and field:

```java
import com.chattabs.config.ServerProfile;
import java.util.ArrayList;
import java.util.List;
```

```java
    private List<ServerProfile> profiles = new ArrayList<>();
```

Add accessors (below `setChatMode`):

```java
    public List<ServerProfile> getProfiles() {
        if (profiles == null) {
            profiles = new ArrayList<>();
        }
        return profiles;
    }

    public ServerProfile profileForAddress(String address) {
        for (ServerProfile profile : getProfiles()) {
            if (profile != null && profile.matchesAddress(address)) {
                return profile;
            }
        }
        for (ServerProfile profile : getProfiles()) {
            if (profile != null && profile.isDefaultProfile()) {
                return profile;
            }
        }
        return ServerProfile.builtInDefault();
    }

    public static void reload() {
        instance = load();
    }
```

Change `normalize()` to package-private with change tracking. Replace the existing method:

```java
    boolean normalize() {
        boolean changed = false;
        int clampedLines = Math.max(1, globalPeekLines);
        if (clampedLines != globalPeekLines) {
            globalPeekLines = clampedLines;
            changed = true;
        }
        if (peekWidth != -1 && peekWidth < 60) {
            peekWidth = 60;
            changed = true;
        }
        if (chatMode == null) {
            chatMode = ChatMode.FILTERED;
            changed = true;
        }
        if (profiles == null) {
            profiles = new ArrayList<>();
        }
        boolean hasDefault = profiles.stream()
                .anyMatch(p -> p != null && p.isDefaultProfile());
        if (!hasDefault) {
            profiles.add(ServerProfile.builtInDefault());
            changed = true;
        }
        return changed;
    }
```

In `load()`, save back migrated configs — replace the body of the `if (loaded != null)` branch:

```java
                if (loaded != null) {
                    if (loaded.normalize()) {
                        loaded.save();
                    }
                    return loaded;
                }
```

Note: `chatMode == null` after `fromJson("{}")` is expected — Gson bypasses field initializers for absent fields is NOT true (Gson keeps initializer defaults), but `normalize()` must stay robust either way; the test asserts observable behavior only.

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/chattabs/ChatTabsConfig.java src/test/java/com/chattabs/ChatTabsConfigTest.java
git commit -m "Store server profiles in config with legacy migration

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Core refactor — MessageKind, profile-driven parser, id-based tabs

The big one: remove `ChatChannel`, switch `ChatMessage`/`ChatParser`/`ChatTab`/`TabManager` to channel ids, and generalize the kingdom mechanism. The build must be green at the end of this task; all existing tests are ported here.

**Files:**
- Create: `src/main/java/com/chattabs/chat/MessageKind.java`
- Delete: `src/main/java/com/chattabs/chat/ChatChannel.java`
- Modify: `src/main/java/com/chattabs/chat/ChatMessage.java`
- Modify: `src/main/java/com/chattabs/chat/ChatParser.java`
- Modify: `src/main/java/com/chattabs/tab/ChatTab.java`
- Modify: `src/main/java/com/chattabs/tab/TabManager.java`
- Test: `src/test/java/com/chattabs/chat/ChatParserTest.java` (rewrite)
- Test: `src/test/java/com/chattabs/tab/ChatTabTest.java` (rewrite)
- Test: `src/test/java/com/chattabs/tab/TabManagerTest.java` (rewrite)

**Interfaces:**
- Consumes: `ServerProfile`, `ChannelDefinition` (Task 1), `ChatTabsConfig.profileForAddress(null)` (Task 2).
- Produces (used by Tasks 4–6):
  - `enum MessageKind { CHANNEL, DM, SYSTEM }`
  - `ChatMessage(Component original, MessageKind kind, String channelId, String senderName, String content)`; getters `getKind()`, `getChannelId()` (null for DM/SYSTEM), `isDM()`; `getSenderName()`, `getContent()`, outgoing/mention unchanged.
  - `ChatParser.setActiveProfile(ServerProfile)` (static), `ChatParser.parse(Component)` unchanged signature; package-private `static Pattern buildChannelPattern(String tag, String separator)`.
  - `ChatTab`: factories `createAllTab()`, `createChannelTab(ChannelDefinition)`, `createDmTab(String)`; `getChannelId()` (null for all/dm), `isAllTab()`, `isChannelTab()`, `isDmTab()`, `isClosable()`; `shouldShowMessage(ChatMessage)`, `shouldTrackUnread()` (= `!isAllTab()`); `getOutgoingPrefix()`/`getChannel()` are removed (dead code).
  - `TabManager`: `applyProfile(ServerProfile)`, `getActiveProfile()`, `getVisibleTabs()`, `getChannelTab(String id)`, `isChannelTabVisible(String id)`, `revealChannelTab(String id)`, `closeChannelTab(String id)`, `noteOutgoingCommand(String)`; `getAllTabs()` keeps returning every tab (All + all channel tabs + DMs). `getFixedTabs()`, `getKingdomTab()`, `isKingdomTabVisible()`, `revealKingdomTab()`, `closeKingdomTab()` are removed.

- [ ] **Step 1: Rewrite the three test files (failing state)**

Replace `src/test/java/com/chattabs/chat/ChatParserTest.java`:

```java
package com.chattabs.chat;

import com.chattabs.config.ServerProfile;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatParserTest {

    @BeforeEach
    void useDefaultProfile() {
        ChatParser.setActiveProfile(ServerProfile.builtInDefault());
    }

    @Test
    void kingdomLineParsesWithChannelId() {
        Component line = Component.literal("[K] [CrimsonHollow] [Lord of the Hollow] UnicornsBane > test");
        ChatMessage parsed = ChatParser.parse(line);
        assertEquals(MessageKind.CHANNEL, parsed.getKind());
        assertEquals("kingdom", parsed.getChannelId());
        assertEquals("[CrimsonHollow] [Lord of the Hollow] UnicornsBane", parsed.getSenderName());
        assertEquals("test", parsed.getContent());
    }

    @Test
    void globalLineParsesWithChannelId() {
        Component line = Component.literal("[G] [Deepstone] [Member] labsami > crimson planks! :D");
        ChatMessage parsed = ChatParser.parse(line);
        assertEquals(MessageKind.CHANNEL, parsed.getKind());
        assertEquals("global", parsed.getChannelId());
    }

    @Test
    void unknownTagAndFeedbackLinesStaySystem() {
        assertEquals(MessageKind.SYSTEM,
                ChatParser.parse(Component.literal("[X] Somebody > hello")).getKind());
        assertEquals(MessageKind.SYSTEM,
                ChatParser.parse(Component.literal("[ỉ] Switched mode for channel kingdom from read to write.")).getKind());
    }

    @Test
    void customProfileChannelIsRecognized() {
        ChatParser.setActiveProfile(new ServerProfile("custom", "", "»", java.util.List.of(
                new com.chattabs.config.ChannelDefinition("trade", "Trade", "«T»", "", true, false))));
        ChatMessage parsed = ChatParser.parse(Component.literal("«T» Trader » selling dirt"));
        assertEquals(MessageKind.CHANNEL, parsed.getKind());
        assertEquals("trade", parsed.getChannelId());
        assertEquals("Trader", parsed.getSenderName());
        assertEquals("selling dirt", parsed.getContent());
    }

    @Test
    void patternQuotesRegexMetacharacters() {
        // Tag containing regex metachars must be treated literally
        assertTrue(ChatParser.buildChannelPattern("[G]", ">")
                .matcher("[G] Player > hi").find());
        assertFalse(ChatParser.buildChannelPattern("[G]", ">")
                .matcher("xGx Player > hi").find());
    }

    @Test
    void dmParsingStillWorks() {
        ChatMessage parsed = ChatParser.parse(Component.literal("✉ you → SomePlayer > hello there"));
        assertTrue(parsed.isDM());
        assertEquals(MessageKind.DM, parsed.getKind());
        assertEquals("SomePlayer", parsed.getSenderName());
    }
}
```

Replace `src/test/java/com/chattabs/tab/ChatTabTest.java`:

```java
package com.chattabs.tab;

import com.chattabs.chat.ChatMessage;
import com.chattabs.chat.MessageKind;
import com.chattabs.config.ChannelDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatTabTest {

    private static final ChannelDefinition KINGDOM =
            new ChannelDefinition("kingdom", "Kingdom", "[K]", "channel join kingdom", false, false);

    @Test
    void channelTabShowsOnlyItsChannel() {
        ChatTab tab = ChatTab.createChannelTab(KINGDOM);
        assertEquals("kingdom", tab.getId());
        assertEquals("kingdom", tab.getChannelId());
        assertTrue(tab.isChannelTab());
        assertTrue(tab.isClosable());

        ChatMessage kingdomMsg = new ChatMessage(null, MessageKind.CHANNEL, "kingdom", "UnicornsBane", "test");
        ChatMessage globalMsg = new ChatMessage(null, MessageKind.CHANNEL, "global", "labsami", "hi");
        ChatMessage system = new ChatMessage(null, MessageKind.SYSTEM, null, null, "note");
        assertTrue(tab.shouldShowMessage(kingdomMsg));
        assertFalse(tab.shouldShowMessage(globalMsg));
        assertFalse(tab.shouldShowMessage(system));
    }

    @Test
    void alwaysVisibleChannelTabIsNotClosable() {
        ChatTab tab = ChatTab.createChannelTab(
                new ChannelDefinition("global", "Global", "[G]", "g", true, true));
        assertFalse(tab.isClosable());
    }

    @Test
    void allTabShowsEverythingAndNeverCountsUnread() {
        ChatTab all = ChatTab.createAllTab();
        assertTrue(all.isAllTab());
        assertFalse(all.isClosable());
        assertTrue(all.shouldShowMessage(new ChatMessage(null, MessageKind.SYSTEM, null, null, "x")));
        assertFalse(all.shouldTrackUnread());
    }

    @Test
    void dmTabMatchesSenderAndTracksUnread() {
        ChatTab dm = ChatTab.createDmTab("SomePlayer");
        assertTrue(dm.isDmTab());
        assertTrue(dm.isClosable());
        assertTrue(dm.shouldTrackUnread());
        ChatMessage msg = new ChatMessage(null, MessageKind.DM, null, "someplayer", "hi");
        assertTrue(dm.shouldShowMessage(msg));
    }
}
```

Replace `src/test/java/com/chattabs/tab/TabManagerTest.java`:

```java
package com.chattabs.tab;

import com.chattabs.chat.ChatParser;
import com.chattabs.config.ChannelDefinition;
import com.chattabs.config.ServerProfile;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TabManagerTest {

    @BeforeEach
    void freshManager() {
        TabManager.resetForTesting();
        TabManager.getInstance().applyProfile(ServerProfile.builtInDefault());
    }

    private static GuiMessage line(String text) {
        return new GuiMessage(0, Component.literal(text), null, null, null);
    }

    @Test
    void profileTabsAreBuiltInOrderWithActivityChannelsHidden() {
        TabManager manager = TabManager.getInstance();
        List<ChatTab> visible = manager.getVisibleTabs();
        assertEquals(List.of("all", "global", "local"),
                visible.stream().map(ChatTab::getId).toList());
        assertFalse(manager.isChannelTabVisible("kingdom"));
        assertNotNull(manager.getChannelTab("kingdom"));
    }

    @Test
    void incomingActivityChannelMessageRevealsTabAndCountsUnread() {
        TabManager manager = TabManager.getInstance();
        Component text = Component.literal("[K] [CrimsonHollow] [Lord] UnicornsBane > test");
        manager.onMessageReceived(text, line("[K] [CrimsonHollow] [Lord] UnicornsBane > test"));

        assertTrue(manager.isChannelTabVisible("kingdom"));
        assertEquals(1, manager.getChannelTab("kingdom").getMessages().size());
        assertEquals(1, manager.getChannelTab("kingdom").getUnreadCount());
    }

    @Test
    void closingActiveChannelTabFallsBackToAll() {
        TabManager manager = TabManager.getInstance();
        manager.revealChannelTab("kingdom");
        manager.setActiveTab(manager.getChannelTab("kingdom"), false);

        manager.closeChannelTab("kingdom");

        assertFalse(manager.isChannelTabVisible("kingdom"));
        assertEquals("all", manager.getActiveTab().getId());
    }

    @Test
    void closingHiddenChannelTabKeepsHistory() {
        TabManager manager = TabManager.getInstance();
        manager.onMessageReceived(Component.literal("[K] [C] [L] U > brb"), line("[K] [C] [L] U > brb"));
        manager.closeChannelTab("kingdom");

        assertFalse(manager.isChannelTabVisible("kingdom"));
        assertEquals(1, manager.getChannelTab("kingdom").getMessages().size());
    }

    @Test
    void typedJoinCommandRevealsActivityChannel() {
        TabManager manager = TabManager.getInstance();
        manager.noteOutgoingCommand("/Channel  Join   KINGDOM ");
        assertTrue(manager.isChannelTabVisible("kingdom"));
    }

    @Test
    void otherCommandsDoNotRevealChannels() {
        TabManager manager = TabManager.getInstance();
        manager.noteOutgoingCommand("/g");
        manager.noteOutgoingCommand("/gift someone");
        manager.noteOutgoingCommand("/channel join global");
        assertFalse(manager.isChannelTabVisible("kingdom"));
    }

    @Test
    void applyProfilePreservesMessagesForSurvivingIdsAndDropsRemoved() {
        TabManager manager = TabManager.getInstance();
        manager.onMessageReceived(Component.literal("[K] [C] [L] U > hi"), line("[K] [C] [L] U > hi"));
        assertEquals(1, manager.getChannelTab("kingdom").getMessages().size());

        manager.applyProfile(new ServerProfile("other", "", ">", List.of(
                new ChannelDefinition("kingdom", "Kingdom", "[K]", "", false, false),
                new ChannelDefinition("trade", "Trade", "[T]", "", true, false))));

        assertEquals(1, manager.getChannelTab("kingdom").getMessages().size());
        assertNotNull(manager.getChannelTab("trade"));
        assertNull(manager.getChannelTab("global"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -5`
Expected: compile errors (MessageKind, setActiveProfile, createChannelTab, applyProfile … not defined).

- [ ] **Step 3: Implement the chat package**

Create `src/main/java/com/chattabs/chat/MessageKind.java`:

```java
package com.chattabs.chat;

public enum MessageKind {
    CHANNEL,
    DM,
    SYSTEM
}
```

Delete `src/main/java/com/chattabs/chat/ChatChannel.java`.

Rework `ChatMessage`: replace the `ChatChannel channel` field with `MessageKind kind` + `String channelId`; constructor becomes `ChatMessage(Component originalMessage, MessageKind kind, String channelId, String senderName, String content)`; `getChannel()` is replaced by `getKind()` and `getChannelId()`; `isDM()` returns `kind == MessageKind.DM`. Everything else (outgoing, mention, timestamp) stays.

Rework `ChatParser`:
- Remove the GLOBAL/LOCAL/KINGDOM pattern constants.
- Add:

```java
    private record ChannelPattern(String channelId, Pattern pattern) {
    }

    private static volatile List<ChannelPattern> channelPatterns = buildPatterns(ServerProfile.builtInDefault());

    public static void setActiveProfile(ServerProfile profile) {
        channelPatterns = buildPatterns(profile == null ? ServerProfile.builtInDefault() : profile);
    }

    private static List<ChannelPattern> buildPatterns(ServerProfile profile) {
        List<ChannelPattern> patterns = new ArrayList<>();
        for (ChannelDefinition channel : profile.validChannels()) {
            patterns.add(new ChannelPattern(channel.getId(),
                    buildChannelPattern(channel.getTag(), profile.getSeparator())));
        }
        return patterns;
    }

    static Pattern buildChannelPattern(String tag, String separator) {
        return Pattern.compile("^" + Pattern.quote(tag) + "\\s+(.+?)\\s*"
                + Pattern.quote(separator) + "\\s*(.*)$");
    }
```

- In `parse(...)`: DM detection first (unchanged, but construct `new ChatMessage(message, MessageKind.DM, null, otherPlayer, content)`), then loop over `channelPatterns` (replaces the three hardcoded blocks — mention detection applies to every channel match), then the DM fallback (unchanged, `MessageKind.DM`), then `new ChatMessage(message, MessageKind.SYSTEM, null, null, text)`.

The channel-match block inside the loop:

```java
        for (ChannelPattern channelPattern : channelPatterns) {
            matcher = channelPattern.pattern().matcher(text);
            if (matcher.find()) {
                String sender = matcher.group(1).trim();
                String content = matcher.group(2).trim();
                ChatMessage msg = new ChatMessage(message, MessageKind.CHANNEL,
                        channelPattern.channelId(), sender, content);
                String ownName = getOwnPlayerName();
                if (ownName != null && content.toLowerCase().contains(ownName.toLowerCase())) {
                    msg.setMention(true);
                }
                return msg;
            }
        }
```

- [ ] **Step 4: Implement ChatTab**

Rework `src/main/java/com/chattabs/tab/ChatTab.java`: fields become `id`, `displayName`, `channelId` (nullable), `dmPlayerName` (nullable), `closable` (boolean), plus the existing message/unread state. Remove `getChannel()`, `getOutgoingPrefix()` (dead code — `TabManager.getActivePrefix()` has no callers and is removed in Step 5).

```java
    private ChatTab(String id, String displayName, String channelId, String dmPlayerName, boolean closable) {
        this.id = id;
        this.displayName = displayName;
        this.channelId = channelId;
        this.dmPlayerName = dmPlayerName;
        this.closable = closable;
        this.messages = new ArrayList<>();
    }

    public static ChatTab createAllTab() {
        return new ChatTab("all", "All", null, null, false);
    }

    public static ChatTab createChannelTab(ChannelDefinition definition) {
        return new ChatTab(definition.getId(), definition.getDisplayName(),
                definition.getId(), null, !definition.isAlwaysVisible());
    }

    public static ChatTab createDmTab(String playerName) {
        return new ChatTab("dm_" + playerName.toLowerCase(), playerName, null, playerName, true);
    }

    public String getChannelId() { return channelId; }
    public boolean isAllTab() { return "all".equals(id); }
    public boolean isChannelTab() { return channelId != null; }
    public boolean isDmTab() { return dmPlayerName != null; }
    public boolean isClosable() { return closable; }

    public boolean shouldShowMessage(ChatMessage parsedMessage) {
        if (isAllTab()) {
            return true;
        }
        if (isDmTab()) {
            return parsedMessage.isDM()
                    && parsedMessage.getSenderName() != null
                    && parsedMessage.getSenderName().equalsIgnoreCase(dmPlayerName);
        }
        return parsedMessage.getKind() == MessageKind.CHANNEL
                && channelId.equalsIgnoreCase(parsedMessage.getChannelId());
    }

    public boolean shouldTrackUnread() {
        return !isAllTab();
    }
```

- [ ] **Step 5: Implement TabManager**

Rework `src/main/java/com/chattabs/tab/TabManager.java`. Structure:

```java
    private ServerProfile activeProfile;
    private ChatTab allTab;
    private final Map<String, ChatTab> channelTabs = new LinkedHashMap<>(); // config order, key = channel id lowercase
    private final Set<String> revealedChannels = new HashSet<>();
    private final Map<String, ChatTab> dmTabs;                              // unchanged
    private String lastServerChannelId;
```

Constructor: build `allTab = ChatTab.createAllTab()`, then `applyProfile(ChatTabsConfig.getInstance().profileForAddress(null))`, `activeTab = allTab`.

```java
    public void applyProfile(ServerProfile profile) {
        ServerProfile effective = profile == null ? ServerProfile.builtInDefault() : profile;
        Map<String, ChatTab> oldTabs = new LinkedHashMap<>(channelTabs);
        channelTabs.clear();
        revealedChannels.clear();
        for (ChannelDefinition channel : effective.validChannels()) {
            String key = channel.getId().toLowerCase(Locale.ROOT);
            ChatTab tab = ChatTab.createChannelTab(channel);
            ChatTab old = oldTabs.get(key);
            if (old != null) {
                tab.getMessages().addAll(old.getMessages());
            }
            channelTabs.put(key, tab);
        }
        activeProfile = effective;
        lastServerChannelId = firstChannelId();
        if (activeTab == null || (activeTab.isChannelTab()
                && !channelTabs.containsKey(activeTab.getChannelId().toLowerCase(Locale.ROOT)))) {
            activeTab = allTab;
        }
        ChatParser.setActiveProfile(effective);
        refreshChatDisplay();
    }

    public ServerProfile getActiveProfile() { return activeProfile; }

    private String firstChannelId() {
        return channelTabs.isEmpty() ? null : channelTabs.keySet().iterator().next();
    }

    public ChatTab getChannelTab(String id) {
        return id == null ? null : channelTabs.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean isChannelTabVisible(String id) {
        ChatTab tab = getChannelTab(id);
        if (tab == null) return false;
        return !tab.isClosable() || revealedChannels.contains(id.toLowerCase(Locale.ROOT));
    }

    public void revealChannelTab(String id) {
        if (getChannelTab(id) != null) {
            revealedChannels.add(id.toLowerCase(Locale.ROOT));
        }
    }

    public void closeChannelTab(String id) {
        ChatTab tab = getChannelTab(id);
        if (tab == null || !tab.isClosable()) return;
        revealedChannels.remove(id.toLowerCase(Locale.ROOT));
        if (activeTab == tab) {
            setActiveTab(allTab, true);
        }
    }

    public List<ChatTab> getVisibleTabs() {
        List<ChatTab> visible = new ArrayList<>();
        visible.add(allTab);
        for (ChatTab tab : channelTabs.values()) {
            if (!tab.isClosable() || revealedChannels.contains(tab.getId().toLowerCase(Locale.ROOT))) {
                visible.add(tab);
            }
        }
        visible.addAll(dmTabs.values());
        return visible;
    }

    public List<ChatTab> getAllTabs() {
        List<ChatTab> all = new ArrayList<>();
        all.add(allTab);
        all.addAll(channelTabs.values());
        all.addAll(dmTabs.values());
        return all;
    }
```

`setActiveTab` guard: `tab == allTab || channelTabs.containsValue(tab) || dmTabs.containsValue(tab)`.

`sendContextSwitchCommand(ChatTab tab)`:

```java
        String targetChannelId = tab.isAllTab() ? firstChannelId() : tab.getChannelId();
        if (targetChannelId == null || targetChannelId.equalsIgnoreCase(lastServerChannelId)) return;
        ChannelDefinition definition = activeProfile.channelById(targetChannelId);
        if (definition == null || definition.getJoinCommand().isBlank()) return;
        client.player.connection.sendCommand(definition.getJoinCommand());
        lastServerChannelId = targetChannelId;
```

(DM tabs have `getChannelId() == null` → no command, as today.)

`noteOutgoingCommand(String rawInput)` — same normalization as today, then:

```java
        for (ChannelDefinition channel : activeProfile.validChannels()) {
            String join = channel.getJoinCommand();
            if (!join.isBlank() && normalized.equals(join.toLowerCase(Locale.ROOT))) {
                lastServerChannelId = channel.getId();
                if (!channel.isAlwaysVisible()) {
                    revealChannelTab(channel.getId());
                }
                return;
            }
        }
```

`onMessageReceived`: replace the kingdom-reveal block with

```java
        if (parsed.getKind() == MessageKind.CHANNEL) {
            revealChannelTab(parsed.getChannelId());
        }
```

Other required edits in TabManager:
- All `ChatChannel` imports/usages go away; `recordGlobalPeekMessage` temporarily keys on `"global".equalsIgnoreCase(parsed.getChannelId())` (Task 6 replaces this with the peek flag — leave a `// Task 6 replaces this with the peek flag` marker out; just write the interim check plainly).
- `mirrorLikelyDmFailure` checks `parsed.getKind() != MessageKind.SYSTEM`.
- `checkIdleTimeout`/`refreshChatDisplay`/`shouldDisplayMessage`/`isAllTabActive` switch from `fixedTabs.get(0)`/`"all".equals(...)` to `allTab`/`activeTab.isAllTab()`.
- Remove: `fixedTabs` field and all uses, `getFixedTabs()`, `getKingdomTab()`, `isKingdomTabVisible()`, `revealKingdomTab()`, `closeKingdomTab()`, `getActivePrefix()`, `setActiveTabByIndex(int)` (dead code, superseded by `getVisibleTabs()` — sub-project B reintroduces shortcuts properly).
- Keep: `resetForTesting()`, `getDmTabs()`, DM logic, `getOutgoingMessagePrefix()`, `noteOutgoingMessage()`, single-window logic (`refreshChatDisplay` shows `allTab` in single-window mode).

- [ ] **Step 6: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -5`
Expected: FAIL — `TabBar` still calls removed methods (`getFixedTabs`, kingdom API). That's Task 4; if only TabBar errors remain, proceed to Task 4 and run the suite there. If chat/tab tests fail, fix here first.

- [ ] **Step 7: Commit (together with Task 4 if the build is only green after both)**

Prefer committing Tasks 3+4 as one green commit:

```bash
git add -A src/main/java/com/chattabs src/test/java
git commit -m "Replace hardcoded channels with profile-driven channel model

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: TabBar on getVisibleTabs

**Files:**
- Modify: `src/main/java/com/chattabs/ui/TabBar.java`

**Interfaces:**
- Consumes: `TabManager.getVisibleTabs()`, `ChatTab.isClosable()`, `ChatTab.isDmTab()`, `TabManager.closeDmTab(String)`, `TabManager.closeChannelTab(String)` (Task 3).
- Produces: nothing consumed later.

- [ ] **Step 1: Rewrite buildTabSlots**

Replace the two loops (fixed + kingdom + DM) in `buildTabSlots` with one:

```java
    private List<TabSlot> buildTabSlots(TabManager tabManager, Font textRenderer) {
        List<TabSlot> slots = new ArrayList<>();
        int currentX = getX();

        for (ChatTab tab : tabManager.getVisibleTabs()) {
            int textWidth = textRenderer.width(tab.getDisplayName());
            int closeWidth = tab.isClosable() ? CLOSE_BUTTON_WIDTH : 0;
            int tabWidth = textWidth + TAB_PADDING * 2 + closeWidth;
            int closeX = tab.isClosable() ? currentX + tabWidth - CLOSE_BUTTON_WIDTH : -1;
            slots.add(new TabSlot(tab, currentX, tabWidth, tab.isClosable(), closeX));
            currentX += tabWidth + TAB_MARGIN;
        }

        return slots;
    }
```

- [ ] **Step 2: Route the close click generically**

In `mouseClicked`, the close branch becomes:

```java
                if (slot.closable() && mouseX >= slot.closeX() && mouseX < slot.closeX() + CLOSE_BUTTON_WIDTH) {
                    if (slot.tab().isDmTab()) {
                        tabManager.closeDmTab(slot.tab().getDmPlayerName());
                    } else {
                        tabManager.closeChannelTab(slot.tab().getChannelId());
                    }
                } else {
                    tabManager.setActiveTab(slot.tab());
                }
```

- [ ] **Step 3: Run the full test suite and build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew build --console=plain 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit (see Task 3 Step 7 — one commit covering Tasks 3+4)**

---

### Task 5: Profile selection on connect + reload command

**Files:**
- Modify: `src/main/java/com/chattabs/ChatTabsMod.java`

**Interfaces:**
- Consumes: `ClientPlayConnectionEvents.JOIN` (Fabric API `net.fabricmc.fabric.api.client.networking.v1`), `Minecraft.getCurrentServer()` → `ServerData` (field `ip`), `ChatTabsConfig.profileForAddress(String)`, `ChatTabsConfig.reload()`, `TabManager.applyProfile(ServerProfile)`.
- Produces: `/chattabs profile` (shows active profile) and `/chattabs profile reload` commands.

No unit test — event registration and client commands need a running client; the logic they call (`profileForAddress`, `applyProfile`) is covered by Tasks 2–3 tests. Verification is a clean build plus the manual checklist in Task 7.

- [ ] **Step 1: Register the JOIN hook**

In `onInitializeClient()` (after `GlobalPeekOverlay.register();`):

```java
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            TabManager.getInstance().applyProfile(
                    ChatTabsConfig.getInstance().profileForAddress(currentServerAddress(client)));
        });
```

Add the helper and imports:

```java
    private static String currentServerAddress(Minecraft client) {
        net.minecraft.client.multiplayer.ServerData server = client.getCurrentServer();
        return server == null ? null : server.ip;
    }
```

```java
import com.chattabs.config.ServerProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
```

If `client.getCurrentServer()` does not exist under that name in 26.2, check `javap -cp <minecraft-jar> -public net.minecraft.client.Minecraft | grep -i server` and use the matching accessor; `ServerData` has the public `ip` field (verified).

- [ ] **Step 2: Add the profile commands**

Inside `registerClientCommands`, add a new literal branch alongside `idle`/`mode`/`peek`/`layout`:

```java
                        .then(ClientCommands.literal("profile")
                                .executes(context -> {
                                    ServerProfile profile = TabManager.getInstance().getActiveProfile();
                                    context.getSource().sendFeedback(Component.literal(
                                            "ChatTabs profile: " + profile.getName() + " ("
                                                    + profile.validChannels().size() + " channels)"));
                                    return 1;
                                })
                                .then(ClientCommands.literal("reload")
                                        .executes(context -> {
                                            ChatTabsConfig.reload();
                                            Minecraft client = Minecraft.getInstance();
                                            TabManager.getInstance().applyProfile(
                                                    ChatTabsConfig.getInstance().profileForAddress(currentServerAddress(client)));
                                            context.getSource().sendFeedback(Component.literal(
                                                    "ChatTabs config reloaded."));
                                            return 1;
                                        })))
```

- [ ] **Step 3: Build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew build --console=plain 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/chattabs/ChatTabsMod.java
git commit -m "Select server profile on join and add profile reload command

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Peek driven by the channel flag

**Files:**
- Modify: `src/main/java/com/chattabs/tab/TabManager.java` (recordGlobalPeekMessage)
- Test: `src/test/java/com/chattabs/tab/TabManagerTest.java` (extend)

**Interfaces:**
- Consumes: `ChannelDefinition.isPeek()`, `ChannelDefinition.getTag()`, `ServerProfile.channelById(String)` (Task 1).
- Produces: nothing new — behavior change only.

- [ ] **Step 1: Write the failing test**

Append to `TabManagerTest`:

```java
    @Test
    void peekCollectsOnlyFlaggedChannelsAndStripsTag() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(new ServerProfile("peeky", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, true),
                new ChannelDefinition("trade", "Trade", "[T]", "", true, false))));

        manager.onMessageReceived(Component.literal("[G] Player > hello world"),
                line("[G] Player > hello world"));
        manager.onMessageReceived(Component.literal("[T] Trader > selling dirt"),
                line("[T] Trader > selling dirt"));

        assertEquals(1, manager.getGlobalPeekMessages().size());
        assertEquals("Player > hello world", manager.getGlobalPeekMessages().get(0).getString());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -5`
Expected: FAIL — the interim implementation keys on channel id "global" and strips the hardcoded `[G]` prefix; either the trade/global filtering or the tag stripping assertion breaks depending on the interim code — any failure here is acceptable as long as it is this test.

(If the interim Task 3 code happens to pass this test, inspect `recordGlobalPeekMessage`: it must not reference the literal strings `"global"` or `"[G]"` after this task.)

- [ ] **Step 3: Implement**

Replace `recordGlobalPeekMessage` in `TabManager`:

```java
    private void recordGlobalPeekMessage(ChatMessage parsed, Component message) {
        if (parsed.getKind() != MessageKind.CHANNEL || activeProfile == null) {
            return;
        }
        ChannelDefinition definition = activeProfile.channelById(parsed.getChannelId());
        if (definition == null || !definition.isPeek()) {
            return;
        }

        // Strip "[PlayerName head]" placeholders from Chat Heads mod and the channel tag
        String raw = CHAT_HEADS_PATTERN.matcher(message.getString()).replaceAll("").trim();
        String display = raw.replaceFirst("^" + Pattern.quote(definition.getTag()) + "\\s*", "");

        int maxMessages = Math.max(1, ChatTabsConfig.getInstance().getGlobalPeekLines());
        globalPeekMessages.add(Component.literal(display));
        while (globalPeekMessages.size() > maxMessages) {
            globalPeekMessages.remove(0);
        }
        lastPeekMessageAt = System.currentTimeMillis();
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/chattabs/tab/TabManager.java src/test/java/com/chattabs/tab/TabManagerTest.java
git commit -m "Drive peek overlay from per-channel peek flag

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Full clean build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew clean build --console=plain 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL; jar in `build/libs/`.

- [ ] **Step 2: Config sanity check**

Delete/rename any `config/chattabs.json` created by test runs in the repo working dir (test pollution), and confirm `git status` shows no unintended files. The repo's `.gitignore` should cover `config/` and `logs/` from test runs — if `git status` shows them untracked, add both to `.gitignore` in this commit.

- [ ] **Step 3: Report the manual test checklist**

Only verifiable on a real server (user's job):

1. First launch migrates the existing `chattabs.json` (a `profiles` block appears; old settings intact).
2. Behavior is identical to before on the default profile: `[G]`/`[L]` tabs, kingdom appears on `[K]` activity, tab clicks switch channels, peek shows global lines.
3. Adding a new channel to the JSON (e.g. trade `[T]` with a join command) + `/chattabs profile reload` shows the new tab without restart.
4. A profile with a `serverAddress` is picked on join of that server; other servers fall back to default.
5. `/chattabs profile` shows the active profile name and channel count.
