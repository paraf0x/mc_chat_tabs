# Per-Channel Notifications & Tab Shortcuts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Per-channel sound and unread-badge switches plus Ctrl+Tab / Ctrl+Shift+Tab tab cycling in the chat screen.

**Architecture:** Two booleans on `ChannelDefinition` flow into `ChatTab` at tab-construction time; `TabManager.onMessageReceived` turns today's coupled unread+sound branch into two independent gates. Cycling is a new `TabManager.cycleActiveTab(int)` over `getVisibleTabs()`, driven from the existing `ChatScreenMixin.keyPressed` injection.

**Tech Stack:** Java 25, Fabric (Minecraft 26.2, loader 0.19.3, Fabric API 0.156.0+26.2), JUnit 5 + fabric-loader-junit.

**Spec:** `docs/superpowers/specs/2026-08-05-notifications-shortcuts-design.md`

## Global Constraints

- All Gradle commands need JDK 25: prefix with `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home`.
- Both new flags default to `true` — existing configs must behave exactly as today.
- A channel with `sound: false` is fully silent, including mentions.
- The flags apply to channel tabs only: DM tabs always ping and badge, the All tab never badges.
- Single-window mode keeps suppressing unread counts and pings.
- Shortcuts only exist while the chat screen is open; they must consume the key event.
- Commit messages end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Branch: `notifications-shortcuts` (already created, carries the spec commit).

---

### Task 1: Notification flags on ChannelDefinition and ChatTab

**Files:**
- Modify: `src/main/java/com/chattabs/config/ChannelDefinition.java`
- Modify: `src/main/java/com/chattabs/tab/ChatTab.java`
- Test: `src/test/java/com/chattabs/tab/ChatTabTest.java` (extend)

**Interfaces:**
- Consumes: existing `ChannelDefinition` ctor.
- Produces (used by Tasks 2–3):
  - `ChannelDefinition.isSound()`, `ChannelDefinition.isBadge()` — both default `true`.
  - New 8-arg ctor `ChannelDefinition(String id, String displayName, String tag, String joinCommand, boolean alwaysVisible, boolean peek, boolean sound, boolean badge)`; the existing 6-arg ctor stays and delegates with `sound = true, badge = true` (all current call sites and tests keep working).
  - `ChatTab.isSoundEnabled()`, `ChatTab.isBadgeEnabled()`; `shouldTrackUnread()` becomes `!isAllTab() && isBadgeEnabled()`.

- [ ] **Step 1: Write the failing test**

Append to `src/test/java/com/chattabs/tab/ChatTabTest.java`:

```java
    @Test
    void channelFlagsDefaultToEnabled() {
        ChatTab tab = ChatTab.createChannelTab(
                new ChannelDefinition("trade", "Trade", "[T]", "", true, false));
        assertTrue(tab.isSoundEnabled());
        assertTrue(tab.isBadgeEnabled());
        assertTrue(tab.shouldTrackUnread());
    }

    @Test
    void badgeFlagControlsUnreadTracking() {
        ChatTab silentBadge = ChatTab.createChannelTab(
                new ChannelDefinition("a", "A", "[A]", "", true, false, false, true));
        assertFalse(silentBadge.isSoundEnabled());
        assertTrue(silentBadge.shouldTrackUnread());

        ChatTab noBadge = ChatTab.createChannelTab(
                new ChannelDefinition("b", "B", "[B]", "", true, false, true, false));
        assertTrue(noBadge.isSoundEnabled());
        assertFalse(noBadge.shouldTrackUnread());
    }

    @Test
    void dmAndAllTabsKeepDefaultNotificationBehavior() {
        ChatTab dm = ChatTab.createDmTab("SomePlayer");
        assertTrue(dm.isSoundEnabled());
        assertTrue(dm.shouldTrackUnread());

        ChatTab all = ChatTab.createAllTab();
        assertTrue(all.isSoundEnabled());
        assertFalse(all.shouldTrackUnread());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | grep -E "Fehler|FAILED|BUILD" | head -5`
Expected: compile error — the 8-arg ctor and `isSoundEnabled`/`isBadgeEnabled` do not exist.

- [ ] **Step 3: Add the flags to ChannelDefinition**

In `src/main/java/com/chattabs/config/ChannelDefinition.java`, add the fields next to the existing ones:

```java
    private boolean sound = true;
    private boolean badge = true;
```

Keep the existing 6-arg constructor as a delegate and add the full one:

```java
    public ChannelDefinition(String id, String displayName, String tag, String joinCommand,
                             boolean alwaysVisible, boolean peek) {
        this(id, displayName, tag, joinCommand, alwaysVisible, peek, true, true);
    }

    public ChannelDefinition(String id, String displayName, String tag, String joinCommand,
                             boolean alwaysVisible, boolean peek, boolean sound, boolean badge) {
        this.id = id;
        this.displayName = displayName;
        this.tag = tag;
        this.joinCommand = joinCommand;
        this.alwaysVisible = alwaysVisible;
        this.peek = peek;
        this.sound = sound;
        this.badge = badge;
    }
```

Add the getters:

```java
    public boolean isSound() {
        return sound;
    }

    public boolean isBadge() {
        return badge;
    }
```

- [ ] **Step 4: Carry the flags into ChatTab**

In `src/main/java/com/chattabs/tab/ChatTab.java`, add fields and extend the private constructor:

```java
    private final boolean soundEnabled;
    private final boolean badgeEnabled;

    private ChatTab(String id, String displayName, String channelId, String dmPlayerName,
                    boolean closable, boolean soundEnabled, boolean badgeEnabled) {
        this.id = id;
        this.displayName = displayName;
        this.channelId = channelId;
        this.dmPlayerName = dmPlayerName;
        this.closable = closable;
        this.soundEnabled = soundEnabled;
        this.badgeEnabled = badgeEnabled;
        this.messages = new ArrayList<>();
        this.unreadCount = 0;
    }
```

Accessors and the changed unread rule:

```java
    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public boolean isBadgeEnabled() {
        return badgeEnabled;
    }

    /**
     * Returns true if this tab should track unread message counts.
     * Every tab except All does, unless its channel disabled the badge.
     */
    public boolean shouldTrackUnread() {
        return !isAllTab() && badgeEnabled;
    }
```

Update the three factories:

```java
    public static ChatTab createAllTab() {
        return new ChatTab("all", "All", null, null, false, true, true);
    }

    public static ChatTab createChannelTab(ChannelDefinition definition) {
        return new ChatTab(definition.getId(), definition.getDisplayName(),
                definition.getId(), null, !definition.isAlwaysVisible(),
                definition.isSound(), definition.isBadge());
    }

    public static ChatTab createDmTab(String playerName) {
        return new ChatTab("dm_" + playerName.toLowerCase(), playerName, null, playerName,
                true, true, true);
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | grep -E "FAILED|BUILD" | head -3`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/chattabs/config/ChannelDefinition.java src/main/java/com/chattabs/tab/ChatTab.java src/test/java/com/chattabs/tab/ChatTabTest.java
git commit -m "Add per-channel sound and badge notification flags

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Independent sound and badge gates in TabManager

**Files:**
- Modify: `src/main/java/com/chattabs/tab/TabManager.java` (`onMessageReceived`, new test seam)
- Test: `src/test/java/com/chattabs/tab/TabManagerTest.java` (extend)

**Interfaces:**
- Consumes: `ChatTab.isSoundEnabled()`, `ChatTab.isBadgeEnabled()`, `ChatTab.shouldTrackUnread()` (Task 1).
- Produces (used by tests only): `int soundRequestsForTesting()` and `void resetSoundRequestsForTesting()` — package-private counter incremented wherever a ping is requested.

- [ ] **Step 1: Write the failing test**

Append to `src/test/java/com/chattabs/tab/TabManagerTest.java`:

```java
    @Test
    void mutedChannelNeitherPingsNorBadgesWhenBothFlagsOff() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(new ServerProfile("quiet", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, false),
                new ChannelDefinition("spam", "Spam", "[S]", "", true, false, false, false))));
        manager.resetSoundRequestsForTesting();

        manager.onMessageReceived(Component.literal("[S] Bot > buy gold"), line("[S] Bot > buy gold"));

        assertEquals(0, manager.getChannelTab("spam").getUnreadCount());
        assertEquals(0, manager.soundRequestsForTesting());
        assertEquals(1, manager.getChannelTab("spam").getMessages().size());
    }

    @Test
    void badgeWithoutSoundCountsSilently() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(new ServerProfile("silent", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, false),
                new ChannelDefinition("quiet", "Quiet", "[Q]", "", true, false, false, true))));
        manager.resetSoundRequestsForTesting();

        manager.onMessageReceived(Component.literal("[Q] Player > hi"), line("[Q] Player > hi"));

        assertEquals(1, manager.getChannelTab("quiet").getUnreadCount());
        assertEquals(0, manager.soundRequestsForTesting());
    }

    @Test
    void soundWithoutBadgePingsWithoutCounting() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(new ServerProfile("loud", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, false),
                new ChannelDefinition("loud", "Loud", "[L2]", "", true, false, true, false))));
        manager.resetSoundRequestsForTesting();

        manager.onMessageReceived(Component.literal("[L2] Player > hi"), line("[L2] Player > hi"));

        assertEquals(0, manager.getChannelTab("loud").getUnreadCount());
        assertEquals(1, manager.soundRequestsForTesting());
    }

    @Test
    void defaultChannelsStillPingOncePerMessage() {
        TabManager manager = TabManager.getInstance();
        manager.resetSoundRequestsForTesting();

        manager.onMessageReceived(Component.literal("[K] [C] [L] U > hi"), line("[K] [C] [L] U > hi"));

        assertEquals(1, manager.soundRequestsForTesting());
        assertEquals(1, manager.getChannelTab("kingdom").getUnreadCount());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | grep -E "Fehler|FAILED|BUILD" | head -5`
Expected: compile error — `soundRequestsForTesting`/`resetSoundRequestsForTesting` not defined.

- [ ] **Step 3: Add the test seam**

In `src/main/java/com/chattabs/tab/TabManager.java`, add the field next to the other counters:

```java
    private int soundRequests;
```

and the package-private accessors below `resetForTesting()`:

```java
    int soundRequestsForTesting() {
        return soundRequests;
    }

    void resetSoundRequestsForTesting() {
        soundRequests = 0;
    }
```

Increment it at the top of `playNotificationSound`, before the client null-guard:

```java
    private void playNotificationSound(ChatMessage parsed) {
        soundRequests++;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        // … unchanged body
```

- [ ] **Step 4: Split the coupled branch into two gates**

Replace the message loop and the mention fallback in `onMessageReceived`:

```java
        boolean playedSound = false;
        for (ChatTab tab : getAllTabs()) {
            if (!tab.shouldShowMessage(parsed)) {
                continue;
            }

            tab.addMessage(hudLine);

            if (tab == activeTab) {
                touchActiveTabActivity();
                continue;
            }

            if (isSingleWindowMode()) {
                continue;
            }

            if (tab.shouldTrackUnread()) {
                tab.incrementUnread();
            }

            if (!playedSound && !tab.isAllTab() && tab.isSoundEnabled()) {
                playNotificationSound(parsed);
                playedSound = true;
            }
        }

        if (!playedSound && parsed.isMention() && soundAllowedFor(parsed)) {
            playNotificationSound(parsed);
        }
```

Add the helper below `playNotificationSound`:

```java
    /** A channel with sound disabled stays silent, mentions included. */
    private boolean soundAllowedFor(ChatMessage parsed) {
        if (parsed.getKind() != MessageKind.CHANNEL) {
            return true;
        }
        ChatTab tab = getChannelTab(parsed.getChannelId());
        return tab == null || tab.isSoundEnabled();
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | grep -E "FAILED|BUILD" | head -3`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/chattabs/tab/TabManager.java src/test/java/com/chattabs/tab/TabManagerTest.java
git commit -m "Gate unread badges and pings on per-channel flags

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Tab cycling in TabManager

**Files:**
- Modify: `src/main/java/com/chattabs/tab/TabManager.java` (add `cycleActiveTab`, remove `setActiveTabById`)
- Test: `src/test/java/com/chattabs/tab/TabManagerTest.java` (extend)

**Interfaces:**
- Consumes: `getVisibleTabs()`, `setActiveTab(ChatTab, boolean)`.
- Produces (used by Task 4): `public void cycleActiveTab(int direction)` — `+1` next, `-1` previous, wrapping; no-op when only one tab is visible. Sends the channel's join command like a tab click.

- [ ] **Step 1: Write the failing test**

Append to `src/test/java/com/chattabs/tab/TabManagerTest.java`:

```java
    @Test
    void cyclingMovesForwardAndWrapsOverVisibleTabs() {
        TabManager manager = TabManager.getInstance();
        // visible: all, global, local (kingdom hidden until activity)
        assertEquals("all", manager.getActiveTab().getId());

        manager.cycleActiveTab(1);
        assertEquals("global", manager.getActiveTab().getId());
        manager.cycleActiveTab(1);
        assertEquals("local", manager.getActiveTab().getId());
        manager.cycleActiveTab(1);
        assertEquals("all", manager.getActiveTab().getId());
    }

    @Test
    void cyclingBackwardsWrapsToLastVisibleTab() {
        TabManager manager = TabManager.getInstance();
        manager.cycleActiveTab(-1);
        assertEquals("local", manager.getActiveTab().getId());
        manager.cycleActiveTab(-1);
        assertEquals("global", manager.getActiveTab().getId());
    }

    @Test
    void cyclingIncludesRevealedChannelsOnly() {
        TabManager manager = TabManager.getInstance();
        manager.revealChannelTab("kingdom");

        manager.cycleActiveTab(-1);
        assertEquals("kingdom", manager.getActiveTab().getId());

        manager.closeChannelTab("kingdom");
        manager.setActiveTab(manager.getChannelTab("local"), false);
        manager.cycleActiveTab(1);
        assertEquals("all", manager.getActiveTab().getId());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | grep -E "Fehler|FAILED|BUILD" | head -5`
Expected: compile error — `cycleActiveTab` not defined.

- [ ] **Step 3: Implement cycleActiveTab and drop setActiveTabById**

In `src/main/java/com/chattabs/tab/TabManager.java`, replace the `setActiveTabById` method with:

```java
    /**
     * Switches to the next ({@code +1}) or previous ({@code -1}) visible tab,
     * wrapping around at both ends.
     */
    public void cycleActiveTab(int direction) {
        List<ChatTab> visible = getVisibleTabs();
        if (visible.size() < 2) {
            return;
        }

        int currentIndex = visible.indexOf(activeTab);
        if (currentIndex < 0) {
            currentIndex = 0;
        }

        int step = direction < 0 ? -1 : 1;
        int nextIndex = Math.floorMod(currentIndex + step, visible.size());
        setActiveTab(visible.get(nextIndex), true);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | grep -E "FAILED|BUILD" | head -3`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/chattabs/tab/TabManager.java src/test/java/com/chattabs/tab/TabManagerTest.java
git commit -m "Add wrapping tab cycling to TabManager

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Ctrl+Tab shortcuts in the chat screen

**Files:**
- Modify: `src/main/java/com/chattabs/mixin/ChatScreenMixin.java` (`onKeyPressed`)

**Interfaces:**
- Consumes: `TabManager.cycleActiveTab(int)` (Task 3), `ChatFocusHelper.refocusImmediate()`, `KeyEvent.hasControlDown()` / `hasShiftDown()` (verified present in 26.2 via `InputWithModifiers`).
- Produces: nothing consumed later.

No unit test — key handling needs a live chat screen; verified by build plus the manual checklist in Task 5.

- [ ] **Step 1: Handle Ctrl+Tab before the history shortcuts**

In `src/main/java/com/chattabs/mixin/ChatScreenMixin.java`, extend `onKeyPressed`; the new block goes first so Ctrl+Tab is consumed before anything else:

```java
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        int key = input.key();

        if (key == GLFW.GLFW_KEY_TAB && input.hasControlDown()) {
            TabManager.getInstance().cycleActiveTab(input.hasShiftDown() ? -1 : 1);
            ChatFocusHelper.refocusImmediate();
            cir.setReturnValue(true);
            return;
        }

        if (key == GLFW.GLFW_KEY_UP) {
            moveInHistory(-1);
            ChatFocusHelper.refocusImmediate();
            cir.setReturnValue(true);
            return;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            moveInHistory(1);
            ChatFocusHelper.refocusImmediate();
            cir.setReturnValue(true);
        }
    }
```

- [ ] **Step 2: Build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew build --console=plain 2>&1 | grep -E "Fehler|FAILED|BUILD" | head -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/chattabs/mixin/ChatScreenMixin.java
git commit -m "Cycle tabs with Ctrl+Tab in the chat screen

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Full clean build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew clean build --console=plain 2>&1 | grep -E "FAILED|BUILD"`
Expected: BUILD SUCCESSFUL; jar in `build/libs/`.

- [ ] **Step 2: Verify defaults are untouched**

Confirm that a config without the new fields keeps today's behavior: the
`defaultChannelsStillPingOncePerMessage` test covers it, and
`ServerProfile.builtInDefault()` must still construct channels via the 6-arg
constructor (sound and badge both `true`).

Run: `grep -n "builtInDefault" -A6 src/main/java/com/chattabs/config/ServerProfile.java`
Expected: three `new ChannelDefinition(...)` calls with six arguments.

- [ ] **Step 3: Report the manual test checklist**

Only verifiable in-game (user's job):

1. Default behavior unchanged: pings and badges as before.
2. `"sound": false` on a channel + `/chattabs profile reload` → that channel is silent, badge still counts; a mention in it stays silent too.
3. `"badge": false` → no unread counter on that tab, ping still plays.
4. Ctrl+Tab cycles forward through the visible tabs, Ctrl+Shift+Tab backward, wrapping at both ends; chat input keeps focus and no tab character is inserted.
5. Cycling to a channel tab sends its join command (typed messages land in that channel).
