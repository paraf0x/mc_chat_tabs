# Kingdom Channel Tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional, closable "Kingdom" chat tab that appears only when the server's kingdom channel (`[K]` messages, joined via `/channel join kingdom`) is actually used.

**Architecture:** The kingdom tab is a fourth channel wired through the existing enum/parser/tab pipeline (`ChatChannel` → `ChatParser` → `ChatTab` → `TabManager` → `TabBar`), but unlike All/Local it is hidden by default and revealed by activity, and closable like DM tabs. Manually typed switch commands (`/g`, `/l`, `/channel join kingdom`) are detected in the existing `handleChatInput` mixin hook to keep the mod's channel state in sync.

**Tech Stack:** Java 25, Fabric (Minecraft 26.2, loader 0.19.3, Fabric API 0.156.0+26.2), Gradle 9.5, JUnit 5 + fabric-loader-junit (new).

**Spec:** `docs/superpowers/specs/2026-08-05-kingdom-channel-tab-design.md`

## Global Constraints

- All Gradle commands need JDK 25: prefix with `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home` (system default is JDK 21 and fails with "Releaseversion 25 nicht unterstützt").
- Minecraft 26.2 ships unobfuscated; there are no mappings. Class names like `net.minecraft.client.gui.components.ChatComponent` are used directly.
- The kingdom channel command is exactly `channel join kingdom` (sent without leading slash via `sendCommand`). The chat tag is `[K]`.
- No config option for the kingdom tab — visibility is driven purely by activity.
- Peek overlay stays global-only. Unknown `[X]` tags other than `[K]` keep falling through to SYSTEM.
- Commit messages end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 0: Commit the pending Minecraft 26.2 port

The working tree contains uncommitted changes from the 26.2 port (gradle.properties, gradle wrapper, fabric.mod.json, ChatTabsMod.java, ChatHudMixin.java, TabManager.java). This feature builds on top of them; they must be committed first so feature commits stay clean.

**Files:**
- Commit (already modified): `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `src/main/resources/fabric.mod.json`, `src/main/java/com/chattabs/ChatTabsMod.java`, `src/main/java/com/chattabs/mixin/ChatHudMixin.java`, `src/main/java/com/chattabs/tab/TabManager.java`

- [ ] **Step 1: Verify the port still builds**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew build --console=plain -q`
Expected: BUILD SUCCESSFUL (no output with -q)

- [ ] **Step 2: Commit**

```bash
git add gradle.properties gradle/wrapper/gradle-wrapper.properties src/main/resources/fabric.mod.json src/main/java/com/chattabs/ChatTabsMod.java src/main/java/com/chattabs/mixin/ChatHudMixin.java src/main/java/com/chattabs/tab/TabManager.java
git commit -m "Port to Minecraft 26.2

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 1: Test infrastructure + kingdom channel parsing

Set up JUnit with fabric-loader-junit (the repo has no tests yet), then add the KINGDOM channel to `ChatChannel` and `[K]` parsing to `ChatParser`, test-first.

**Files:**
- Modify: `build.gradle` (dependencies block, new test block)
- Modify: `src/main/java/com/chattabs/chat/ChatChannel.java`
- Modify: `src/main/java/com/chattabs/chat/ChatParser.java`
- Test: `src/test/java/com/chattabs/chat/ChatParserTest.java` (create)

**Interfaces:**
- Consumes: existing `ChatParser.parse(Component)`, `ChatMessage` getters.
- Produces: `ChatChannel.KINGDOM` enum constant (command string `"channel join kingdom"`, indicator `"[K]"`). Tasks 2, 3, 5 reference `ChatChannel.KINGDOM`.

- [ ] **Step 1: Add test dependencies and test task to build.gradle**

In `build.gradle`, extend the `dependencies` block and add a `test` block after it:

```gradle
dependencies {
    // Minecraft 26.x ships unobfuscated — no mappings dependency needed.
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

    testImplementation platform("org.junit:junit-bom:5.11.4")
    testImplementation "org.junit.jupiter:junit-jupiter"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher"
    testImplementation "net.fabricmc:fabric-loader-junit:${project.loader_version}"
}

test {
    useJUnitPlatform()
}
```

`fabric-loader-junit` provides the FabricLoader runtime environment in unit tests — required because `ChatTabsConfig` resolves `FabricLoader.getInstance().getConfigDir()` in a static initializer (needed by Task 3's tests).

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/chattabs/chat/ChatParserTest.java`:

```java
package com.chattabs.chat;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatParserTest {

    @Test
    void kingdomLineParsesAsKingdomChannel() {
        Component line = Component.literal("[K] [CrimsonHollow] [Lord of the Hollow] UnicornsBane > test");
        ChatMessage parsed = ChatParser.parse(line);
        assertEquals(ChatChannel.KINGDOM, parsed.getChannel());
        assertEquals("[CrimsonHollow] [Lord of the Hollow] UnicornsBane", parsed.getSenderName());
        assertEquals("test", parsed.getContent());
    }

    @Test
    void globalLineStillParsesAsGlobal() {
        Component line = Component.literal("[G] [Deepstone] [Member] labsami > crimson planks! :D");
        assertEquals(ChatChannel.GLOBAL, ChatParser.parse(line).getChannel());
    }

    @Test
    void channelSwitchFeedbackStaysSystem() {
        Component line = Component.literal("[ỉ] Switched mode for channel kingdom from read to write.");
        assertEquals(ChatChannel.SYSTEM, ChatParser.parse(line).getChannel());
    }

    @Test
    void unknownBracketTagStaysSystem() {
        Component line = Component.literal("[X] Somebody > hello");
        assertEquals(ChatChannel.SYSTEM, ChatParser.parse(line).getChannel());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -20`
Expected: FAIL — `kingdomLineParsesAsKingdomChannel` cannot compile/resolve `ChatChannel.KINGDOM` (compile error) — that is the expected failure mode.

If instead the test JVM fails to initialize `Component` (registry/bootstrap errors), report back before proceeding — do not silently delete tests.

- [ ] **Step 4: Add KINGDOM to ChatChannel**

In `src/main/java/com/chattabs/chat/ChatChannel.java`, extend the enum constants:

```java
public enum ChatChannel {
    GLOBAL("g", "[G]"),
    LOCAL("l", "[L]"),
    KINGDOM("channel join kingdom", "[K]"),
    DM("tell", "✉"),
    SYSTEM(null, null);  // System messages, notifications, etc.
```

No other changes in this file.

- [ ] **Step 5: Add kingdom pattern and null-guard to ChatParser**

In `src/main/java/com/chattabs/chat/ChatParser.java`:

Add the pattern next to the existing ones (after `LOCAL_PATTERN`):

```java
    private static final Pattern KINGDOM_PATTERN = Pattern.compile("^\\[K\\]\\s+(.+?)\\s*>\\s*(.*)$");
```

Harden `getOwnPlayerName` (also makes it safe in the test JVM where `Minecraft.getInstance()` is null):

```java
    private static String getOwnPlayerName() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            return client.player.getName().getString();
        }
        return null;
    }
```

In `parse(...)`, after the Local block (`// Local chat [L]` … return) and before the DM fallback, insert:

```java
        // Kingdom chat [K]
        matcher = KINGDOM_PATTERN.matcher(text);
        if (matcher.find()) {
            String sender = matcher.group(1).trim();
            String content = matcher.group(2).trim();
            ChatMessage msg = new ChatMessage(message, ChatChannel.KINGDOM, sender, content);
            String ownName = getOwnPlayerName();
            if (ownName != null && content.toLowerCase().contains(ownName.toLowerCase())) {
                msg.setMention(true);
            }
            return msg;
        }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL, 4 tests pass

- [ ] **Step 7: Commit**

```bash
git add build.gradle src/main/java/com/chattabs/chat/ChatChannel.java src/main/java/com/chattabs/chat/ChatParser.java src/test/java/com/chattabs/chat/ChatParserTest.java
git commit -m "Add kingdom channel parsing and JUnit test setup

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Kingdom tab type

Add the kingdom tab factory and unread tracking to `ChatTab`, test-first.

**Files:**
- Modify: `src/main/java/com/chattabs/tab/ChatTab.java`
- Test: `src/test/java/com/chattabs/tab/ChatTabTest.java` (create)

**Interfaces:**
- Consumes: `ChatChannel.KINGDOM` (Task 1).
- Produces: `ChatTab.createKingdomTab()` → `ChatTab` with id `"kingdom"`, display name `"Kingdom"`, channel `KINGDOM`, no dmPlayerName. `shouldTrackUnread()` returns true for it. Task 3 calls `createKingdomTab()`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/chattabs/tab/ChatTabTest.java`. Note: `ChatMessage`'s `Component` parameter is never touched by `shouldShowMessage`, so `null` is fine here.

```java
package com.chattabs.tab;

import com.chattabs.chat.ChatChannel;
import com.chattabs.chat.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatTabTest {

    @Test
    void kingdomTabShowsOnlyKingdomMessages() {
        ChatTab tab = ChatTab.createKingdomTab();
        assertEquals("kingdom", tab.getId());
        assertEquals("Kingdom", tab.getDisplayName());
        assertFalse(tab.isDmTab());

        ChatMessage kingdomMsg = new ChatMessage(null, ChatChannel.KINGDOM, "UnicornsBane", "test");
        ChatMessage globalMsg = new ChatMessage(null, ChatChannel.GLOBAL, "labsami", "hi");
        assertTrue(tab.shouldShowMessage(kingdomMsg));
        assertFalse(tab.shouldShowMessage(globalMsg));
    }

    @Test
    void kingdomTabTracksUnread() {
        assertTrue(ChatTab.createKingdomTab().shouldTrackUnread());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -20`
Expected: compile error — `createKingdomTab` not defined.

- [ ] **Step 3: Implement in ChatTab**

In `src/main/java/com/chattabs/tab/ChatTab.java`:

Extend `shouldTrackUnread`:

```java
    public boolean shouldTrackUnread() {
        return id.equals("local") || id.equals("kingdom") || isDmTab();
    }
```

Add the factory next to the existing ones:

```java
    public static ChatTab createKingdomTab() {
        return new ChatTab("kingdom", "Kingdom", ChatChannel.KINGDOM);
    }
```

No change needed in `shouldShowMessage` — the generic `parsedMessage.getChannel() == channel` fallthrough covers the kingdom tab.

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/chattabs/tab/ChatTab.java src/test/java/com/chattabs/tab/ChatTabTest.java
git commit -m "Add kingdom chat tab type with unread tracking

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Kingdom tab lifecycle in TabManager

Wire the kingdom tab into `TabManager`: always collects messages, hidden by default, revealed by incoming `[K]` messages, closable, and switching to it sends `channel join kingdom`.

**Files:**
- Modify: `src/main/java/com/chattabs/tab/TabManager.java`
- Test: `src/test/java/com/chattabs/tab/TabManagerTest.java` (create)

**Interfaces:**
- Consumes: `ChatTab.createKingdomTab()` (Task 2), `ChatChannel.KINGDOM` (Task 1).
- Produces (used by Tasks 4 and 5):
  - `public ChatTab getKingdomTab()`
  - `public boolean isKingdomTabVisible()`
  - `public void revealKingdomTab()`
  - `public void closeKingdomTab()`
  - `static void resetForTesting()` (package-private, tests only)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/chattabs/tab/TabManagerTest.java`:

```java
package com.chattabs.tab;

import com.chattabs.chat.ChatChannel;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TabManagerTest {

    @BeforeEach
    void freshManager() {
        TabManager.resetForTesting();
    }

    @Test
    void kingdomTabHiddenByDefaultButAlwaysCollects() {
        TabManager manager = TabManager.getInstance();
        assertFalse(manager.isKingdomTabVisible());
        assertTrue(manager.getAllTabs().contains(manager.getKingdomTab()));
        assertFalse(manager.getFixedTabs().contains(manager.getKingdomTab()));
    }

    @Test
    void incomingKingdomMessageRevealsTabAndCountsUnread() {
        TabManager manager = TabManager.getInstance();
        Component line = Component.literal("[K] [CrimsonHollow] [Lord of the Hollow] UnicornsBane > test");
        GuiMessage hudLine = new GuiMessage(0, line, null, null, null);

        manager.onMessageReceived(line, hudLine);

        assertTrue(manager.isKingdomTabVisible());
        assertEquals(1, manager.getKingdomTab().getMessages().size());
        assertEquals(1, manager.getKingdomTab().getUnreadCount());
    }

    @Test
    void closingActiveKingdomTabFallsBackToAllTab() {
        TabManager manager = TabManager.getInstance();
        manager.revealKingdomTab();
        manager.setActiveTab(manager.getKingdomTab(), false);
        assertEquals(manager.getKingdomTab(), manager.getActiveTab());

        manager.closeKingdomTab();

        assertFalse(manager.isKingdomTabVisible());
        assertEquals("all", manager.getActiveTab().getId());
    }

    @Test
    void closingHiddenKingdomTabKeepsHistory() {
        TabManager manager = TabManager.getInstance();
        Component line = Component.literal("[K] [CrimsonHollow] [Lord of the Hollow] UnicornsBane > brb");
        manager.onMessageReceived(line, new GuiMessage(0, line, null, null, null));
        manager.closeKingdomTab();

        assertFalse(manager.isKingdomTabVisible());
        assertEquals(1, manager.getKingdomTab().getMessages().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -20`
Expected: compile error — `resetForTesting`, `getKingdomTab`, `isKingdomTabVisible` not defined.

- [ ] **Step 3: Implement in TabManager**

All changes in `src/main/java/com/chattabs/tab/TabManager.java`:

**a)** Fields + constructor — add after `private final Map<String, ChatTab> dmTabs;`:

```java
    private final ChatTab kingdomTab;
    private boolean kingdomTabVisible = false;
```

In the constructor, after the `fixedTabs.add(...)` lines:

```java
        kingdomTab = ChatTab.createKingdomTab();
```

**b)** Test reset hook — add below `getInstance()`:

```java
    static void resetForTesting() {
        instance = null;
    }
```

**c)** Accessors + lifecycle — add below `getDmTabs()`:

```java
    public ChatTab getKingdomTab() {
        return kingdomTab;
    }

    public boolean isKingdomTabVisible() {
        return kingdomTabVisible;
    }

    public void revealKingdomTab() {
        kingdomTabVisible = true;
    }

    public void closeKingdomTab() {
        kingdomTabVisible = false;
        if (activeTab == kingdomTab) {
            setActiveTab(fixedTabs.get(0), true);
        }
    }
```

`closeKingdomTab` with an active kingdom tab goes through `setActiveTab(all, true)` → `sendContextSwitchCommand` → sends `g` (since `lastServerChannel` is KINGDOM), fulfilling the spec's "closing the active tab returns you to global speech".

**d)** `getAllTabs()` — kingdom always collects (order: fixed, kingdom, DMs):

```java
    public List<ChatTab> getAllTabs() {
        List<ChatTab> all = new ArrayList<>(fixedTabs);
        all.add(kingdomTab);
        all.addAll(dmTabs.values());
        return all;
    }
```

**e)** `setActiveTab(ChatTab, boolean)` — accept the kingdom tab. Replace the guard condition:

```java
        if (tab != null && (fixedTabs.contains(tab) || tab == kingdomTab || dmTabs.containsValue(tab))) {
```

**f)** `sendContextSwitchCommand` — add the kingdom case and null-guard the client:

```java
    private void sendContextSwitchCommand(ChatTab tab) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
```

and in the switch:

```java
        String command = switch (targetChannel) {
            case GLOBAL -> "g";
            case LOCAL -> "l";
            case KINGDOM -> "channel join kingdom";
            case DM -> null;
            case SYSTEM -> null;
        };
```

**g)** `onMessageReceived` — reveal on kingdom traffic. After `ChatMessage parsed = ChatParser.parse(message);` and the existing DM block, add:

```java
        if (parsed.getChannel() == ChatChannel.KINGDOM) {
            revealKingdomTab();
        }
```

**h)** Null-guard the two remaining `Minecraft.getInstance()` consumers so the test JVM (no client instance) can exercise these paths:

In `playNotificationSound`:

```java
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
```

In `refreshChatDisplay`:

```java
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui == null) return;
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL, all tests pass. (These tests touch `ChatTabsConfig`, which needs the fabric-loader-junit environment from Task 1. If `FabricLoader.getInstance()` fails here, report back — do not weaken the tests.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/chattabs/tab/TabManager.java src/test/java/com/chattabs/tab/TabManagerTest.java
git commit -m "Add hidden-by-default kingdom tab lifecycle to TabManager

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Render the kingdom tab in the TabBar

Show the kingdom tab (when visible) between the fixed tabs and the DM tabs, with a close button like DM tabs.

**Files:**
- Modify: `src/main/java/com/chattabs/ui/TabBar.java`

**Interfaces:**
- Consumes: `TabManager.isKingdomTabVisible()`, `getKingdomTab()`, `closeKingdomTab()` (Task 3); `ChatTab.isDmTab()`.
- Produces: nothing consumed by later tasks.

No unit test — `TabBar` is pure rendering against live `Minecraft`/`Font` instances; covered by the manual test in Task 6. Verification for this task is a clean build.

- [ ] **Step 1: Generalize the TabSlot record**

In `src/main/java/com/chattabs/ui/TabBar.java`, the `dmTab` flag really means "closable". Rename it (record at the bottom of the file):

```java
    private record TabSlot(ChatTab tab, int x, int width, boolean closable, int closeX) {
    }
```

Update the two readers:
- In `renderTab`: `if (slot.dmTab())` → `if (slot.closable())`
- In `mouseClicked`: `if (slot.dmTab() && ...)` → `if (slot.closable() && ...)`

- [ ] **Step 2: Add the kingdom slot in buildTabSlots**

Between the fixed-tabs loop and the DM-tabs loop, insert:

```java
        if (tabManager.isKingdomTabVisible()) {
            ChatTab kingdomTab = tabManager.getKingdomTab();
            int textWidth = textRenderer.width(kingdomTab.getDisplayName());
            int tabWidth = textWidth + TAB_PADDING * 2 + CLOSE_BUTTON_WIDTH;
            int closeX = currentX + tabWidth - CLOSE_BUTTON_WIDTH;
            slots.add(new TabSlot(kingdomTab, currentX, tabWidth, true, closeX));
            currentX += tabWidth + TAB_MARGIN;
        }
```

- [ ] **Step 3: Route the close click**

In `mouseClicked`, the close branch currently assumes DM. Replace:

```java
                if (slot.closable() && mouseX >= slot.closeX() && mouseX < slot.closeX() + CLOSE_BUTTON_WIDTH) {
                    if (slot.tab().isDmTab()) {
                        tabManager.closeDmTab(slot.tab().getDmPlayerName());
                    } else {
                        tabManager.closeKingdomTab();
                    }
                } else {
                    tabManager.setActiveTab(slot.tab());
                }
```

- [ ] **Step 4: Verify build and tests**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew build --console=plain 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/chattabs/ui/TabBar.java
git commit -m "Render closable kingdom tab in tab bar

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Detect manually typed channel commands

When the user types `/g`, `/l`, or `/channel join kingdom` themselves, update `lastServerChannel` (fixes the existing stale-state bug) and reveal the kingdom tab for the kingdom command.

**Files:**
- Modify: `src/main/java/com/chattabs/tab/TabManager.java`
- Modify: `src/main/java/com/chattabs/mixin/ChatScreenMixin.java:76-90` (the `prefixDmMessage` method)
- Test: `src/test/java/com/chattabs/tab/TabManagerTest.java` (extend)

**Interfaces:**
- Consumes: `revealKingdomTab()`, `isKingdomTabVisible()` (Task 3).
- Produces: `public void noteOutgoingCommand(String rawInput)` on `TabManager`, called from `ChatScreenMixin`.

- [ ] **Step 1: Write the failing tests**

Append to `src/test/java/com/chattabs/tab/TabManagerTest.java`:

```java
    @Test
    void typedKingdomJoinCommandRevealsTab() {
        TabManager manager = TabManager.getInstance();
        manager.noteOutgoingCommand("/channel join kingdom");
        assertTrue(manager.isKingdomTabVisible());
    }

    @Test
    void typedKingdomJoinCommandIsCaseAndWhitespaceInsensitive() {
        TabManager manager = TabManager.getInstance();
        manager.noteOutgoingCommand("/Channel  Join   KINGDOM ");
        assertTrue(manager.isKingdomTabVisible());
    }

    @Test
    void otherCommandsDoNotRevealKingdomTab() {
        TabManager manager = TabManager.getInstance();
        manager.noteOutgoingCommand("/g");
        manager.noteOutgoingCommand("/gift someone");
        manager.noteOutgoingCommand("/channel join global");
        manager.noteOutgoingCommand("hello no command");
        assertFalse(manager.isKingdomTabVisible());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -20`
Expected: compile error — `noteOutgoingCommand` not defined.

- [ ] **Step 3: Implement noteOutgoingCommand in TabManager**

Add below `noteOutgoingMessage()`:

```java
    /**
     * Tracks channel-switch commands the user types manually so the mod's
     * channel state stays in sync (a bare "/g" or "/l" with arguments may
     * just send a one-off message on some servers, so only exact matches
     * count as switches).
     */
    public void noteOutgoingCommand(String rawInput) {
        if (rawInput == null || !rawInput.startsWith("/")) return;

        String normalized = rawInput.substring(1).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        switch (normalized) {
            case "g" -> lastServerChannel = ChatChannel.GLOBAL;
            case "l" -> lastServerChannel = ChatChannel.LOCAL;
            case "channel join kingdom" -> {
                lastServerChannel = ChatChannel.KINGDOM;
                revealKingdomTab();
            }
            default -> { }
        }
    }
```

(`java.util.Locale` is already imported via the `java.util.*` wildcard.)

- [ ] **Step 4: Hook it into ChatScreenMixin**

In `src/main/java/com/chattabs/mixin/ChatScreenMixin.java`, `prefixDmMessage` currently starts with:

```java
        if (message.startsWith("/") || message.trim().isEmpty()) {
            return message;
        }
```

Replace with:

```java
        if (message.startsWith("/")) {
            TabManager.getInstance().noteOutgoingCommand(message);
            return message;
        }
        if (message.trim().isEmpty()) {
            return message;
        }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/chattabs/tab/TabManager.java src/main/java/com/chattabs/mixin/ChatScreenMixin.java src/test/java/com/chattabs/tab/TabManagerTest.java
git commit -m "Sync channel state with manually typed switch commands

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Full build and test run**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew clean build --console=plain 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL; jar appears in `build/libs/chat-tabs-mod-1.4.0+*.jar`

- [ ] **Step 2: Report manual test checklist to the user**

The following can only be verified on the target server (user's job, with the built jar):

1. Kingdom tab is absent on connect.
2. An incoming `[K]` message reveals the tab with unread badge + sound; the message is in the tab.
3. Clicking the Kingdom tab sends `/channel join kingdom` (server confirms "Switched mode …"); typed messages land in kingdom.
4. Clicking All afterwards sends `/g`.
5. Typing `/channel join kingdom` manually reveals the tab immediately.
6. Closing the tab (x) hides it; if it was active, chat switches to All and `/g` is sent.
7. The next `[K]` message brings the tab back with history intact.
8. On a server without the kingdom channel, the tab never appears.
