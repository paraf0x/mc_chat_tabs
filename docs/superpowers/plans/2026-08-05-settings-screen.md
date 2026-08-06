# Settings Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An in-game settings screen with live previews and tooltips that makes every stored setting — global options and per-channel profiles — editable without touching JSON.

**Architecture:** All logic that can live without a client is extracted into three small units (`ChannelValidation`, `ChannelPreview`, `SettingsDraft`) and unit-tested; the two screens (`ChatTabsSettingsScreen`, `ChannelEditScreen`) are thin views over a `SettingsDraft` working copy that is committed to `ChatTabsConfig` on Done. Entry points are `/chattabs settings` and an optional ModMenu entrypoint.

**Tech Stack:** Java 25, Fabric (Minecraft 26.2, loader 0.19.3, Fabric API 0.156.0+26.2), ModMenu 20.0.1 (compileOnly), JUnit 5 + fabric-loader-junit.

**Spec:** `docs/superpowers/specs/2026-08-05-settings-screen-design.md`

## Global Constraints

- All Gradle commands need JDK 25: prefix with `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home`.
- Editing must never mutate live config objects — the screens work on a `SettingsDraft` and commit on Done only.
- Preview and runtime parsing must share one pattern definition (`ChatParser.buildChannelPattern`); no second regex anywhere.
- The channel id `all` is reserved; ids must be unique per profile and non-blank; tags must be non-blank.
- ModMenu stays optional: `compileOnly` dependency plus `suggests` in `fabric.mod.json`; the mod must run without it.
- Verified 26.2 APIs to use: `Button.builder`, `CycleButton.onOffBuilder`, `EditBox`, `Tooltip.create`, `AbstractWidget.setTooltip`, `ContainerObjectSelectionList`, `Screen.extractRenderState(GuiGraphicsExtractor, int, int, float)`.
- Commit messages end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Branch: `settings-screen` (already created, carries the spec commit).

---

### Task 1: Validation and preview units

**Files:**
- Create: `src/main/java/com/chattabs/config/ChannelValidation.java`
- Create: `src/main/java/com/chattabs/config/ChannelPreview.java`
- Modify: `src/main/java/com/chattabs/chat/ChatParser.java` (make `buildChannelPattern` public)
- Test: `src/test/java/com/chattabs/config/ChannelValidationTest.java`
- Test: `src/test/java/com/chattabs/config/ChannelPreviewTest.java`

**Interfaces:**
- Consumes: `ChannelDefinition` getters, `ChatParser.buildChannelPattern(String tag, String separator)`.
- Produces (used by Tasks 2–4):
  - `Optional<String> ChannelValidation.validate(ChannelDefinition candidate, List<ChannelDefinition> others)` — empty when valid, else a user-facing message.
  - `record ChannelPreview(boolean matched, String sender, String content)` with
    `static ChannelPreview of(String tag, String separator, String sampleLine)` and
    `static String sampleLineFor(String tag)` returning `<tag> [Rank] PlayerName > message text`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/chattabs/config/ChannelValidationTest.java`:

```java
package com.chattabs.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChannelValidationTest {

    private static ChannelDefinition channel(String id, String tag) {
        return new ChannelDefinition(id, id, tag, "", true, false);
    }

    @Test
    void validChannelPassesAgainstOthers() {
        Optional<String> error = ChannelValidation.validate(
                channel("trade", "[T]"), List.of(channel("global", "[G]")));
        assertTrue(error.isEmpty());
    }

    @Test
    void blankIdAndBlankTagAreRejected() {
        assertTrue(ChannelValidation.validate(channel("", "[T]"), List.of()).isPresent());
        assertTrue(ChannelValidation.validate(channel("trade", " "), List.of()).isPresent());
    }

    @Test
    void reservedIdIsRejectedCaseInsensitively() {
        assertTrue(ChannelValidation.validate(channel("all", "[A]"), List.of()).isPresent());
        assertTrue(ChannelValidation.validate(channel("ALL", "[A]"), List.of()).isPresent());
    }

    @Test
    void duplicateIdIsRejectedCaseInsensitively() {
        List<ChannelDefinition> others = List.of(channel("trade", "[T]"));
        assertTrue(ChannelValidation.validate(channel("TRADE", "[X]"), others).isPresent());
        assertTrue(ChannelValidation.validate(channel("other", "[X]"), others).isEmpty());
    }
}
```

Create `src/test/java/com/chattabs/config/ChannelPreviewTest.java`:

```java
package com.chattabs.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelPreviewTest {

    @Test
    void matchingLineSplitsSenderAndContent() {
        ChannelPreview preview = ChannelPreview.of("[K]", ">",
                "[K] [CrimsonHollow] [Lord] UnicornsBane > test message");
        assertTrue(preview.matched());
        assertEquals("[CrimsonHollow] [Lord] UnicornsBane", preview.sender());
        assertEquals("test message", preview.content());
    }

    @Test
    void nonMatchingLineReportsNoMatch() {
        ChannelPreview preview = ChannelPreview.of("[K]", ">", "[G] Player > hello");
        assertFalse(preview.matched());
        assertEquals("", preview.sender());
        assertEquals("", preview.content());
    }

    @Test
    void tagsAndSeparatorsWithMetacharactersAreLiteral() {
        ChannelPreview preview = ChannelPreview.of("(T)", "|", "(T) Trader | selling dirt");
        assertTrue(preview.matched());
        assertEquals("Trader", preview.sender());
        assertEquals("selling dirt", preview.content());
    }

    @Test
    void blankTagNeverMatches() {
        assertFalse(ChannelPreview.of("", ">", "Player > hi").matched());
    }

    @Test
    void sampleLineStartsWithTheTag() {
        String sample = ChannelPreview.sampleLineFor("[T]");
        assertTrue(sample.startsWith("[T] "));
        assertTrue(ChannelPreview.of("[T]", ">", sample).matched());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | grep -E "Fehler|FAILED|BUILD" | head -5`
Expected: compile errors — `ChannelValidation` and `ChannelPreview` do not exist.

- [ ] **Step 3: Make the pattern builder public**

In `src/main/java/com/chattabs/chat/ChatParser.java`, change the visibility of the existing method (body unchanged):

```java
    public static Pattern buildChannelPattern(String tag, String separator) {
```

- [ ] **Step 4: Implement ChannelValidation**

Create `src/main/java/com/chattabs/config/ChannelValidation.java`:

```java
package com.chattabs.config;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Rules a channel must satisfy before it can be saved. */
public final class ChannelValidation {

    private ChannelValidation() {
    }

    public static Optional<String> validate(ChannelDefinition candidate, List<ChannelDefinition> others) {
        String id = candidate.getId().trim();
        if (id.isBlank()) {
            return Optional.of("Channel id must not be empty.");
        }
        if (id.equalsIgnoreCase("all")) {
            return Optional.of("\"all\" is reserved for the All tab.");
        }
        if (candidate.getTag().isBlank()) {
            return Optional.of("Chat tag must not be empty.");
        }
        for (ChannelDefinition other : others) {
            if (other != null && other.getId().equalsIgnoreCase(id)) {
                return Optional.of("Another channel already uses the id \"" + id.toLowerCase(Locale.ROOT) + "\".");
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 5: Implement ChannelPreview**

Create `src/main/java/com/chattabs/config/ChannelPreview.java`:

```java
package com.chattabs.config;

import com.chattabs.chat.ChatParser;

import java.util.regex.Matcher;

/**
 * How a sample chat line parses with a given tag and separator. Uses the very
 * same pattern builder the runtime parser uses, so the preview cannot drift.
 */
public record ChannelPreview(boolean matched, String sender, String content) {

    private static final ChannelPreview NO_MATCH = new ChannelPreview(false, "", "");

    public static ChannelPreview of(String tag, String separator, String sampleLine) {
        if (tag == null || tag.isBlank() || sampleLine == null) {
            return NO_MATCH;
        }
        String effectiveSeparator = separator == null || separator.isBlank() ? ">" : separator;
        Matcher matcher = ChatParser.buildChannelPattern(tag, effectiveSeparator).matcher(sampleLine.trim());
        if (!matcher.find()) {
            return NO_MATCH;
        }
        return new ChannelPreview(true, matcher.group(1).trim(), matcher.group(2).trim());
    }

    public static String sampleLineFor(String tag) {
        String effectiveTag = tag == null || tag.isBlank() ? "[G]" : tag;
        return effectiveTag + " [Rank] PlayerName > message text";
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | grep -E "FAILED|BUILD" | head -3`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/chattabs/config/ChannelValidation.java src/main/java/com/chattabs/config/ChannelPreview.java src/main/java/com/chattabs/chat/ChatParser.java src/test/java/com/chattabs/config/
git commit -m "Add channel validation and preview units

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: SettingsDraft working copy

**Files:**
- Create: `src/main/java/com/chattabs/config/SettingsDraft.java`
- Test: `src/test/java/com/chattabs/config/SettingsDraftTest.java`

**Interfaces:**
- Consumes: `ChatTabsConfig` getters/setters (`getIdleSwitchSeconds`, `setIdleSwitchSeconds`, `isGlobalPeekEnabled`, `setGlobalPeekEnabled`, `getGlobalPeekLines`, `setGlobalPeekLines`, `getChatMode`, `setChatMode`, `getProfiles`, `save`), `ServerProfile`, `ChannelDefinition`.
- Produces (used by Tasks 3–4):
  - `static SettingsDraft from(ChatTabsConfig config)`
  - `List<ServerProfile> profiles()`, `ServerProfile profile(int index)`, `int profileCount()`
  - `int idleSwitchSeconds()` / `setIdleSwitchSeconds(int)`
  - `boolean peekEnabled()` / `setPeekEnabled(boolean)`
  - `int peekLines()` / `setPeekLines(int)`
  - `ChatTabsConfig.ChatMode chatMode()` / `setChatMode(ChatMode)`
  - `void setSeparator(int profileIndex, String separator)`
  - `void putChannel(int profileIndex, int channelIndex, ChannelDefinition channel)` — `channelIndex < 0` appends
  - `void removeChannel(int profileIndex, int channelIndex)`
  - `int addProfileForAddress(String address, int copyFromIndex)` → index of the new profile
  - `void commit(ChatTabsConfig config)` — writes everything back and saves

`ServerProfile` needs a mutable channel view for the draft: add
`List<ChannelDefinition> channelsForEditing()` returning the live backing list
(creating it when null), plus `void setSeparator(String)`. `ChannelDefinition`
stays immutable — edits replace the entry via `putChannel`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/chattabs/config/SettingsDraftTest.java`:

```java
package com.chattabs.config;

import com.chattabs.ChatTabsConfig;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettingsDraftTest {

    private ChatTabsConfig config;

    @BeforeEach
    void freshConfig() {
        config = new Gson().fromJson("{}", ChatTabsConfig.class);
        config.normalize();
    }

    @Test
    void editingTheDraftLeavesTheConfigUntouchedUntilCommit() {
        SettingsDraft draft = SettingsDraft.from(config);
        draft.setIdleSwitchSeconds(42);
        draft.putChannel(0, 0, new ChannelDefinition("global", "Welt", "[W]", "g", true, true));

        assertEquals(-1, config.getIdleSwitchSeconds());
        assertEquals("[G]", config.getProfiles().get(0).validChannels().get(0).getTag());

        draft.commit(config);

        assertEquals(42, config.getIdleSwitchSeconds());
        assertEquals("[W]", config.getProfiles().get(0).validChannels().get(0).getTag());
        assertEquals("Welt", config.getProfiles().get(0).validChannels().get(0).getDisplayName());
    }

    @Test
    void addingAndRemovingChannelsRoundTrips() {
        SettingsDraft draft = SettingsDraft.from(config);
        int before = draft.profile(0).validChannels().size();

        draft.putChannel(0, -1, new ChannelDefinition("trade", "Trade", "[T]", "", true, false));
        assertEquals(before + 1, draft.profile(0).validChannels().size());

        draft.removeChannel(0, 0);
        draft.commit(config);

        var channels = config.getProfiles().get(0).validChannels();
        assertEquals(before, channels.size());
        assertEquals("trade", channels.get(channels.size() - 1).getId());
        assertFalse(channels.stream().anyMatch(c -> c.getId().equals("global")));
    }

    @Test
    void addProfileForAddressCopiesChannelsOfTheSourceProfile() {
        SettingsDraft draft = SettingsDraft.from(config);
        int index = draft.addProfileForAddress("play.example.net", 0);

        assertEquals(1, index);
        assertEquals("play.example.net", draft.profile(index).getServerAddress());
        assertEquals(draft.profile(0).validChannels().size(), draft.profile(index).validChannels().size());

        draft.putChannel(index, 0, new ChannelDefinition("global", "Global", "[GG]", "g", true, true));
        assertEquals("[G]", draft.profile(0).validChannels().get(0).getTag());

        draft.commit(config);
        assertEquals(2, config.getProfiles().size());
    }

    @Test
    void separatorIsEditablePerProfile() {
        SettingsDraft draft = SettingsDraft.from(config);
        draft.setSeparator(0, "»");
        draft.commit(config);
        assertEquals("»", config.getProfiles().get(0).getSeparator());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | grep -E "Fehler|FAILED|BUILD" | head -5`
Expected: compile error — `SettingsDraft` does not exist.

- [ ] **Step 3: Add the mutable hooks to ServerProfile**

In `src/main/java/com/chattabs/config/ServerProfile.java`, add:

```java
    /** Live backing list for the settings draft; config order, unvalidated. */
    public List<ChannelDefinition> channelsForEditing() {
        if (channels == null) {
            channels = new ArrayList<>();
        }
        return channels;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public void setName(String name) {
        this.name = name;
    }
```

- [ ] **Step 4: Implement SettingsDraft**

Create `src/main/java/com/chattabs/config/SettingsDraft.java`:

```java
package com.chattabs.config;

import com.chattabs.ChatTabsConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Working copy of everything the settings screen can edit. Screens mutate the
 * draft; nothing reaches the live config until {@link #commit(ChatTabsConfig)}.
 */
public class SettingsDraft {

    private final List<ServerProfile> profiles = new ArrayList<>();
    private int idleSwitchSeconds;
    private boolean peekEnabled;
    private int peekLines;
    private ChatTabsConfig.ChatMode chatMode;

    private SettingsDraft() {
    }

    public static SettingsDraft from(ChatTabsConfig config) {
        SettingsDraft draft = new SettingsDraft();
        draft.idleSwitchSeconds = config.getIdleSwitchSeconds();
        draft.peekEnabled = config.isGlobalPeekEnabled();
        draft.peekLines = config.getGlobalPeekLines();
        draft.chatMode = config.getChatMode();
        for (ServerProfile profile : config.getProfiles()) {
            if (profile != null) {
                draft.profiles.add(copyOf(profile));
            }
        }
        if (draft.profiles.isEmpty()) {
            draft.profiles.add(ServerProfile.builtInDefault());
        }
        return draft;
    }

    private static ServerProfile copyOf(ServerProfile source) {
        List<ChannelDefinition> channels = new ArrayList<>();
        for (ChannelDefinition channel : source.channelsForEditing()) {
            if (channel == null) {
                continue;
            }
            channels.add(new ChannelDefinition(channel.getId(), channel.getDisplayName(), channel.getTag(),
                    channel.getJoinCommand(), channel.isAlwaysVisible(), channel.isPeek(),
                    channel.isSound(), channel.isBadge()));
        }
        return new ServerProfile(source.getName(), source.getServerAddress(), source.getSeparator(), channels);
    }

    public List<ServerProfile> profiles() {
        return profiles;
    }

    public ServerProfile profile(int index) {
        return profiles.get(index);
    }

    public int profileCount() {
        return profiles.size();
    }

    public int idleSwitchSeconds() {
        return idleSwitchSeconds;
    }

    public void setIdleSwitchSeconds(int seconds) {
        this.idleSwitchSeconds = seconds;
    }

    public boolean peekEnabled() {
        return peekEnabled;
    }

    public void setPeekEnabled(boolean enabled) {
        this.peekEnabled = enabled;
    }

    public int peekLines() {
        return peekLines;
    }

    public void setPeekLines(int lines) {
        this.peekLines = Math.max(1, Math.min(10, lines));
    }

    public ChatTabsConfig.ChatMode chatMode() {
        return chatMode;
    }

    public void setChatMode(ChatTabsConfig.ChatMode mode) {
        this.chatMode = mode;
    }

    public void setSeparator(int profileIndex, String separator) {
        profile(profileIndex).setSeparator(separator);
    }

    /** Replaces the channel at {@code channelIndex}, or appends when it is negative. */
    public void putChannel(int profileIndex, int channelIndex, ChannelDefinition channel) {
        List<ChannelDefinition> channels = profile(profileIndex).channelsForEditing();
        if (channelIndex < 0 || channelIndex >= channels.size()) {
            channels.add(channel);
        } else {
            channels.set(channelIndex, channel);
        }
    }

    public void removeChannel(int profileIndex, int channelIndex) {
        List<ChannelDefinition> channels = profile(profileIndex).channelsForEditing();
        if (channelIndex >= 0 && channelIndex < channels.size()) {
            channels.remove(channelIndex);
        }
    }

    public int addProfileForAddress(String address, int copyFromIndex) {
        ServerProfile source = profile(copyFromIndex);
        ServerProfile copy = copyOf(source);
        copy.setName(address);
        profiles.add(new ServerProfile(address, address, copy.getSeparator(), copy.channelsForEditing()));
        return profiles.size() - 1;
    }

    public void commit(ChatTabsConfig config) {
        config.setIdleSwitchSeconds(idleSwitchSeconds);
        config.setGlobalPeekEnabled(peekEnabled);
        config.setGlobalPeekLines(peekLines);
        config.setChatMode(chatMode);
        config.getProfiles().clear();
        for (ServerProfile profile : profiles) {
            config.getProfiles().add(copyOf(profile));
        }
        config.save();
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew test --console=plain 2>&1 | grep -E "FAILED|BUILD" | head -3`
Expected: BUILD SUCCESSFUL, all tests pass.

Note: `commit` calls `config.save()`, which writes to the Fabric config dir — in
tests that lands in the repo's ignored `config/` directory, which is expected.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/chattabs/config/SettingsDraft.java src/main/java/com/chattabs/config/ServerProfile.java src/test/java/com/chattabs/config/SettingsDraftTest.java
git commit -m "Add settings draft working copy

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Channel edit screen with live preview

**Files:**
- Create: `src/main/java/com/chattabs/ui/settings/ChannelEditScreen.java`

**Interfaces:**
- Consumes: `ChannelPreview`, `ChannelValidation`, `ChannelDefinition`, `SettingsDraft` (Tasks 1–2).
- Produces (used by Task 4): constructor
  `ChannelEditScreen(Screen parent, SettingsDraft draft, int profileIndex, int channelIndex)`
  where `channelIndex < 0` means "new channel". On Done it writes the edited
  channel into the draft via `putChannel` and returns to `parent`.

No unit test — screen code needs a client; the logic it drives is covered by
Tasks 1–2. Verification is a clean build plus the manual checklist in Task 6.

- [ ] **Step 1: Create the screen**

Create `src/main/java/com/chattabs/ui/settings/ChannelEditScreen.java`:

```java
package com.chattabs.ui.settings;

import com.chattabs.config.ChannelDefinition;
import com.chattabs.config.ChannelPreview;
import com.chattabs.config.ChannelValidation;
import com.chattabs.config.SettingsDraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChannelEditScreen extends Screen {
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int TAG_COLOR = 0xFF4CAF50;
    private static final int SENDER_COLOR = 0xFF64B5F6;
    private static final int CONTENT_COLOR = 0xFFFFFFFF;
    private static final int ERROR_COLOR = 0xFFFF5252;

    private final Screen parent;
    private final SettingsDraft draft;
    private final int profileIndex;
    private final int channelIndex;

    private EditBox idBox;
    private EditBox nameBox;
    private EditBox tagBox;
    private EditBox joinBox;
    private EditBox sampleBox;
    private Button doneButton;

    private boolean alwaysVisible;
    private boolean sound;
    private boolean badge;
    private boolean peek;

    private String errorMessage;

    public ChannelEditScreen(Screen parent, SettingsDraft draft, int profileIndex, int channelIndex) {
        super(Component.literal(channelIndex < 0 ? "Add Channel" : "Edit Channel"));
        this.parent = parent;
        this.draft = draft;
        this.profileIndex = profileIndex;
        this.channelIndex = channelIndex;

        ChannelDefinition existing = existingChannel();
        this.alwaysVisible = existing == null || existing.isAlwaysVisible();
        this.sound = existing == null || existing.isSound();
        this.badge = existing == null || existing.isBadge();
        this.peek = existing != null && existing.isPeek();
    }

    private ChannelDefinition existingChannel() {
        if (channelIndex < 0) {
            return null;
        }
        List<ChannelDefinition> channels = draft.profile(profileIndex).channelsForEditing();
        return channelIndex < channels.size() ? channels.get(channelIndex) : null;
    }

    @Override
    protected void init() {
        ChannelDefinition existing = existingChannel();
        int left = width / 2 - 150;
        int y = 40;

        idBox = addField(left, y, "kingdom", existing == null ? "" : existing.getId(),
                "Internal id, unique per profile. Used to match messages to this tab.");
        y += 34;
        nameBox = addField(left, y, "Kingdom", existing == null ? "" : existing.getDisplayName(),
                "Name shown on the tab.");
        y += 34;
        tagBox = addField(left, y, "[K]", existing == null ? "" : existing.getTag(),
                "Text that prefixes messages of this channel, e.g. [K].");
        y += 34;
        joinBox = addField(left, y, "channel join kingdom", existing == null ? "" : existing.getJoinCommand(),
                "Command sent (without slash) when switching to this tab. Leave empty for none.");
        y += 34;
        sampleBox = addField(left, y, "sample line",
                ChannelPreview.sampleLineFor(existing == null ? "" : existing.getTag()),
                "Paste a real line from your server to check the settings.");

        int toggleY = 214;
        addToggle(left, toggleY, "Always visible", alwaysVisible,
                "On: the tab is always shown. Off: it appears only when the channel has activity and can be closed.",
                value -> alwaysVisible = value);
        addToggle(left + 152, toggleY, "Sound", sound,
                "Play a notification sound for messages of this channel.",
                value -> sound = value);
        addToggle(left, toggleY + 24, "Unread badge", badge,
                "Show an unread counter on this tab.",
                value -> badge = value);
        addToggle(left + 152, toggleY + 24, "Peek overlay", peek,
                "Show this channel in the peek overlay while another tab is active.",
                value -> peek = value);

        doneButton = addRenderableWidget(Button.builder(Component.literal("Done"), button -> save())
                .bounds(width / 2 - 154, height - 32, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(width / 2 + 4, height - 32, 150, 20).build());

        revalidate();
    }

    private EditBox addField(int x, int y, String hint, String value, String tooltip) {
        EditBox box = new EditBox(font, x, y + 10, 300, 20, Component.literal(hint));
        box.setMaxLength(128);
        box.setValue(value);
        box.setResponder(text -> revalidate());
        box.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return addRenderableWidget(box);
    }

    private void addToggle(int x, int y, String label, boolean initial, String tooltip,
                           java.util.function.Consumer<Boolean> setter) {
        addRenderableWidget(CycleButton.onOffBuilder(initial)
                .withTooltip(value -> Tooltip.create(Component.literal(tooltip)))
                .create(x, y, 146, 20, Component.literal(label), (button, value) -> setter.accept(value)));
    }

    private void revalidate() {
        ChannelDefinition candidate = buildChannel();
        Optional<String> error = ChannelValidation.validate(candidate, otherChannels());
        errorMessage = error.orElse(null);
        if (doneButton != null) {
            doneButton.active = errorMessage == null;
        }
    }

    private List<ChannelDefinition> otherChannels() {
        List<ChannelDefinition> others = new ArrayList<>(draft.profile(profileIndex).channelsForEditing());
        if (channelIndex >= 0 && channelIndex < others.size()) {
            others.remove(channelIndex);
        }
        return others;
    }

    private ChannelDefinition buildChannel() {
        return new ChannelDefinition(
                idBox == null ? "" : idBox.getValue().trim(),
                nameBox == null ? "" : nameBox.getValue().trim(),
                tagBox == null ? "" : tagBox.getValue().trim(),
                joinBox == null ? "" : joinBox.getValue().trim(),
                alwaysVisible, peek, sound, badge);
    }

    private void save() {
        if (errorMessage != null) {
            return;
        }
        draft.putChannel(profileIndex, channelIndex, buildChannel());
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        context.text(font, getTitle().getString(), width / 2 - font.width(getTitle().getString()) / 2, 14,
                0xFFFFFFFF, true);

        int left = width / 2 - 150;
        context.text(font, "Channel id", left, 42, LABEL_COLOR, false);
        context.text(font, "Display name", left, 76, LABEL_COLOR, false);
        context.text(font, "Chat tag", left, 110, LABEL_COLOR, false);
        context.text(font, "Join command", left, 144, LABEL_COLOR, false);
        context.text(font, "Sample line", left, 178, LABEL_COLOR, false);

        renderPreview(context, left, 268);

        if (errorMessage != null) {
            context.text(font, errorMessage, left, height - 48, ERROR_COLOR, false);
        }
    }

    private void renderPreview(GuiGraphicsExtractor context, int x, int y) {
        context.text(font, "Preview", x, y, LABEL_COLOR, false);

        String tag = tagBox == null ? "" : tagBox.getValue().trim();
        String separator = draft.profile(profileIndex).getSeparator();
        String sample = sampleBox == null ? "" : sampleBox.getValue();
        ChannelPreview preview = ChannelPreview.of(tag, separator, sample);

        int lineY = y + 12;
        if (!preview.matched()) {
            context.text(font, "Does not match — check tag and separator.", x, lineY, ERROR_COLOR, false);
            return;
        }

        int cursor = x;
        context.text(font, tag, cursor, lineY, TAG_COLOR, false);
        cursor += font.width(tag + " ");
        context.text(font, preview.sender(), cursor, lineY, SENDER_COLOR, false);
        cursor += font.width(preview.sender() + " ");
        context.text(font, separator, cursor, lineY, LABEL_COLOR, false);
        cursor += font.width(separator + " ");
        context.text(font, preview.content(), cursor, lineY, CONTENT_COLOR, false);

        context.text(font, "tag", x, lineY + 14, TAG_COLOR, false);
        context.text(font, "sender", x + 30, lineY + 14, SENDER_COLOR, false);
        context.text(font, "message", x + 80, lineY + 14, CONTENT_COLOR, false);
    }
}
```

- [ ] **Step 2: Build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew build --console=plain 2>&1 | grep -E "Fehler|FAILED|BUILD" | head -8`
Expected: BUILD SUCCESSFUL. If `context.text(...)` or `CycleButton.Builder.withTooltip/create` signatures differ, check them with
`javap -cp <minecraft-jar> -public net.minecraft.client.gui.GuiGraphicsExtractor | grep text` and
`javap -cp <minecraft-jar> -public net.minecraft.client.gui.components.CycleButton\$Builder`
and adjust the calls — the existing `TabBar`/`GlobalPeekOverlay` code shows the working `context.text` form.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/chattabs/ui/settings/ChannelEditScreen.java
git commit -m "Add channel edit screen with live parse preview

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Main settings screen

**Files:**
- Create: `src/main/java/com/chattabs/ui/settings/ChatTabsSettingsScreen.java`

**Interfaces:**
- Consumes: `SettingsDraft` (Task 2), `ChannelEditScreen` (Task 3), `ChatTabsConfig`, `TabManager.applyProfile`, `PeekPositionScreen`.
- Produces (used by Task 5): constructor `ChatTabsSettingsScreen(Screen parent)`.

- [ ] **Step 1: Create the screen**

Create `src/main/java/com/chattabs/ui/settings/ChatTabsSettingsScreen.java`:

```java
package com.chattabs.ui.settings;

import com.chattabs.ChatTabsConfig;
import com.chattabs.config.ChannelDefinition;
import com.chattabs.config.SettingsDraft;
import com.chattabs.config.ServerProfile;
import com.chattabs.tab.TabManager;
import com.chattabs.ui.PeekPositionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ChatTabsSettingsScreen extends Screen {
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int HEADER_COLOR = 0xFFFFFFFF;
    private static final int TAG_COLOR = 0xFF4CAF50;
    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 5;

    private static final int[] IDLE_CHOICES = {-1, 15, 30, 60, 120, 300};

    private final Screen parent;
    private SettingsDraft draft;
    private int profileIndex;
    private int scrollOffset;
    private EditBox separatorBox;

    public ChatTabsSettingsScreen(Screen parent) {
        super(Component.literal("Chat Tabs Settings"));
        this.parent = parent;
        this.draft = SettingsDraft.from(ChatTabsConfig.getInstance());
        this.profileIndex = 0;
    }

    @Override
    protected void init() {
        int left = width / 2 - 180;
        int right = width / 2 + 4;
        int y = 32;

        // --- Profile ---
        addRenderableWidget(CycleButton.<Integer>builder(index -> Component.literal(profileLabel(index)),
                        profileIndex)
                .withValues(profileIndices())
                .withTooltip(index -> Tooltip.create(Component.literal(
                        "Which server profile these channels belong to. The default profile applies to every server without its own entry.")))
                .create(left, y, 176, 20, Component.literal("Profile"), (button, value) -> {
                    profileIndex = value;
                    scrollOffset = 0;
                    rebuild();
                }));

        String address = currentServerAddress();
        if (address != null && !hasProfileFor(address)) {
            addRenderableWidget(Button.builder(Component.literal("Profile for this server"), button -> {
                        profileIndex = draft.addProfileForAddress(address, profileIndex);
                        scrollOffset = 0;
                        rebuild();
                    })
                    .bounds(right, y, 176, 20)
                    .tooltip(Tooltip.create(Component.literal(
                            "Create a profile for " + address + ", copied from the currently selected profile.")))
                    .build());
        }

        y += 26;

        // --- General ---
        addRenderableWidget(CycleButton.<ChatTabsConfig.ChatMode>builder(
                        mode -> Component.literal(mode == ChatTabsConfig.ChatMode.FILTERED
                                ? "Chat mode: Filtered tabs" : "Chat mode: Single window"),
                        draft.chatMode())
                .withValues(List.of(ChatTabsConfig.ChatMode.FILTERED, ChatTabsConfig.ChatMode.SINGLE_WINDOW))
                .withTooltip(mode -> Tooltip.create(Component.literal(
                        "Filtered: each tab shows only its own messages. Single window: every message stays visible, tabs only steer where you speak.")))
                .create(left, y, 176, 20, Component.literal("Chat mode"),
                        (button, value) -> draft.setChatMode(value)));

        addRenderableWidget(CycleButton.<Integer>builder(
                        seconds -> Component.literal("Idle switch: "
                                + (seconds <= 0 ? "Never" : seconds + "s")),
                        nearestIdleChoice(draft.idleSwitchSeconds()))
                .withValues(java.util.Arrays.stream(IDLE_CHOICES).boxed().toList())
                .withTooltip(seconds -> Tooltip.create(Component.literal(
                        "Switch back to the All tab after this much silence in the active tab.")))
                .create(right, y, 176, 20, Component.literal("Idle switch"),
                        (button, value) -> draft.setIdleSwitchSeconds(value)));

        y += 26;

        addRenderableWidget(CycleButton.onOffBuilder(draft.peekEnabled())
                .withTooltip(value -> Tooltip.create(Component.literal(
                        "Show recent messages of peek-enabled channels in a small overlay while another tab is active.")))
                .create(left, y, 176, 20, Component.literal("Peek overlay"),
                        (button, value) -> draft.setPeekEnabled(value)));

        addRenderableWidget(CycleButton.<Integer>builder(lines -> Component.literal("Peek lines: " + lines),
                        Math.max(1, Math.min(10, draft.peekLines())))
                .withValues(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
                .withTooltip(lines -> Tooltip.create(Component.literal(
                        "How many message lines the peek overlay keeps.")))
                .create(right, y, 176, 20, Component.literal("Peek lines"),
                        (button, value) -> draft.setPeekLines(value)));

        y += 26;

        separatorBox = new EditBox(font, left, y, 176, 20, Component.literal("Separator"));
        separatorBox.setMaxLength(8);
        separatorBox.setValue(draft.profile(profileIndex).getSeparator());
        separatorBox.setResponder(text -> draft.setSeparator(profileIndex, text));
        separatorBox.setTooltip(Tooltip.create(Component.literal(
                "Character between sender and message in this server's chat lines, usually >.")));
        addRenderableWidget(separatorBox);

        addRenderableWidget(Button.builder(Component.literal("Layout editor"),
                        button -> minecraft.gui.setScreen(new PeekPositionScreen()))
                .bounds(right, y, 176, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Drag the chat and the peek overlay to where you want them.")))
                .build());

        // --- Channel rows ---
        int listTop = y + 40;
        List<ChannelDefinition> channels = draft.profile(profileIndex).channelsForEditing();
        int maxOffset = Math.max(0, channels.size() - VISIBLE_ROWS);
        scrollOffset = Math.min(scrollOffset, maxOffset);

        for (int row = 0; row < Math.min(VISIBLE_ROWS, channels.size() - scrollOffset); row++) {
            int channelIndex = scrollOffset + row;
            int rowY = listTop + row * ROW_HEIGHT;
            addChannelRow(left, rowY, channelIndex);
        }

        int listBottom = listTop + Math.min(VISIBLE_ROWS, Math.max(1, channels.size())) * ROW_HEIGHT + 4;

        if (channels.size() > VISIBLE_ROWS) {
            addRenderableWidget(Button.builder(Component.literal("▲"), button -> {
                        scrollOffset = Math.max(0, scrollOffset - 1);
                        rebuild();
                    }).bounds(width / 2 + 184, listTop, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), button -> {
                        scrollOffset = Math.min(maxOffset, scrollOffset + 1);
                        rebuild();
                    }).bounds(width / 2 + 184, listTop + 22, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Add channel"),
                        button -> minecraft.gui.setScreen(
                                new ChannelEditScreen(this, draft, profileIndex, -1)))
                .bounds(left, listBottom, 176, 20)
                .tooltip(Tooltip.create(Component.literal("Define another chat channel for this profile.")))
                .build());

        // --- Footer ---
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> saveAndClose())
                .bounds(width / 2 - 154, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(width / 2 + 4, height - 28, 150, 20).build());
    }

    private void addChannelRow(int left, int rowY, int channelIndex) {
        ChannelDefinition channel = draft.profile(profileIndex).channelsForEditing().get(channelIndex);

        addRenderableWidget(Button.builder(Component.literal(channel.getDisplayName()),
                        button -> minecraft.gui.setScreen(
                                new ChannelEditScreen(this, draft, profileIndex, channelIndex)))
                .bounds(left, rowY, 120, 20)
                .tooltip(Tooltip.create(Component.literal("Edit " + channel.getDisplayName()
                        + " (tag " + channel.getTag() + ")")))
                .build());

        addToggleCell(left + 124, rowY, "V", channel.isAlwaysVisible(),
                "Always visible. Off: appears on activity and can be closed.",
                value -> replaceChannel(channelIndex, withFlags(channel, value, channel.isSound(),
                        channel.isBadge(), channel.isPeek())));
        addToggleCell(left + 156, rowY, "S", channel.isSound(),
                "Sound for messages of this channel.",
                value -> replaceChannel(channelIndex, withFlags(channel, channel.isAlwaysVisible(), value,
                        channel.isBadge(), channel.isPeek())));
        addToggleCell(left + 188, rowY, "B", channel.isBadge(),
                "Unread counter on this tab.",
                value -> replaceChannel(channelIndex, withFlags(channel, channel.isAlwaysVisible(),
                        channel.isSound(), value, channel.isPeek())));
        addToggleCell(left + 220, rowY, "P", channel.isPeek(),
                "Include in the peek overlay.",
                value -> replaceChannel(channelIndex, withFlags(channel, channel.isAlwaysVisible(),
                        channel.isSound(), channel.isBadge(), value)));

        addRenderableWidget(Button.builder(Component.literal("✕"), button -> {
                    draft.removeChannel(profileIndex, channelIndex);
                    rebuild();
                })
                .bounds(left + 252, rowY, 20, 20)
                .tooltip(Tooltip.create(Component.literal("Remove this channel.")))
                .build());
    }

    private void addToggleCell(int x, int y, String label, boolean initial, String tooltip,
                               java.util.function.Consumer<Boolean> setter) {
        addRenderableWidget(CycleButton.onOffBuilder(initial)
                .displayOnlyValue()
                .withTooltip(value -> Tooltip.create(Component.literal(tooltip)))
                .create(x, y, 28, 20, Component.literal(label), (button, value) -> setter.accept(value)));
    }

    private ChannelDefinition withFlags(ChannelDefinition source, boolean alwaysVisible, boolean sound,
                                        boolean badge, boolean peek) {
        return new ChannelDefinition(source.getId(), source.getDisplayName(), source.getTag(),
                source.getJoinCommand(), alwaysVisible, peek, sound, badge);
    }

    private void replaceChannel(int channelIndex, ChannelDefinition channel) {
        draft.putChannel(profileIndex, channelIndex, channel);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private List<Integer> profileIndices() {
        return java.util.stream.IntStream.range(0, draft.profileCount()).boxed().toList();
    }

    private String profileLabel(int index) {
        ServerProfile profile = draft.profile(index);
        return "Profile: " + (profile.isDefaultProfile() ? "default (all servers)" : profile.getServerAddress());
    }

    private int nearestIdleChoice(int seconds) {
        for (int choice : IDLE_CHOICES) {
            if (choice == seconds) {
                return choice;
            }
        }
        return -1;
    }

    private String currentServerAddress() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        return server == null ? null : server.ip;
    }

    private boolean hasProfileFor(String address) {
        for (ServerProfile profile : draft.profiles()) {
            if (profile.matchesAddress(address)) {
                return true;
            }
        }
        return false;
    }

    private void saveAndClose() {
        ChatTabsConfig config = ChatTabsConfig.getInstance();
        draft.commit(config);
        TabManager.getInstance().applyProfile(config.profileForAddress(currentServerAddress()));
        TabManager.getInstance().refreshForModeChange();
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        String title = getTitle().getString();
        context.text(font, title, width / 2 - font.width(title) / 2, 12, HEADER_COLOR, true);

        int left = width / 2 - 180;
        int listTop = 32 + 26 * 3 + 40;
        context.text(font, "Channels of this profile", left, listTop - 12, LABEL_COLOR, false);

        List<ChannelDefinition> channels = draft.profile(profileIndex).channelsForEditing();
        if (channels.isEmpty()) {
            context.text(font, "No channels yet — add one below.", left, listTop + 4, LABEL_COLOR, false);
            return;
        }

        for (int row = 0; row < Math.min(VISIBLE_ROWS, channels.size() - scrollOffset); row++) {
            ChannelDefinition channel = channels.get(scrollOffset + row);
            context.text(font, channel.getTag(), left + 280, listTop + row * ROW_HEIGHT + 6, TAG_COLOR, false);
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew build --console=plain 2>&1 | grep -E "Fehler|FAILED|BUILD" | head -8`
Expected: BUILD SUCCESSFUL. If `CycleButton.Builder` lacks `displayOnlyValue()` or `withValues(List)` under these names, inspect it with
`javap -cp <minecraft-jar> -public 'net.minecraft.client.gui.components.CycleButton$Builder'` and adjust.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/chattabs/ui/settings/ChatTabsSettingsScreen.java
git commit -m "Add main settings screen with channel list

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Entry points — command and ModMenu

**Files:**
- Modify: `build.gradle` (Terraformers maven + ModMenu compileOnly)
- Modify: `src/main/resources/fabric.mod.json` (modmenu entrypoint + suggests)
- Create: `src/main/java/com/chattabs/compat/ChatTabsModMenu.java`
- Modify: `src/main/java/com/chattabs/ChatTabsMod.java` (`/chattabs settings`)

**Interfaces:**
- Consumes: `ChatTabsSettingsScreen(Screen parent)` (Task 4).
- Produces: `/chattabs settings` command and the ModMenu configure button.

- [ ] **Step 1: Add the ModMenu dependency**

In `build.gradle`, extend `repositories`:

```gradle
repositories {
    mavenCentral()
    maven {
        name = "Terraformers"
        url = "https://maven.terraformersmc.com/releases"
    }
}
```

and add to `dependencies` (compile-time only — the mod runs without ModMenu):

```gradle
    compileOnly "com.terraformersmc:modmenu:20.0.1"
```

- [ ] **Step 2: Create the ModMenu entrypoint**

Create `src/main/java/com/chattabs/compat/ChatTabsModMenu.java`:

```java
package com.chattabs.compat;

import com.chattabs.ui.settings.ChatTabsSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Only loaded when ModMenu is installed. */
public class ChatTabsModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ChatTabsSettingsScreen::new;
    }
}
```

- [ ] **Step 3: Register the entrypoint**

In `src/main/resources/fabric.mod.json`, extend `entrypoints` and add `suggests`:

```json
  "entrypoints": {
    "client": ["com.chattabs.ChatTabsMod"],
    "modmenu": ["com.chattabs.compat.ChatTabsModMenu"]
  },
```

and after the `depends` block:

```json
  "suggests": {
    "modmenu": "*"
  }
```

- [ ] **Step 4: Add the settings command**

In `src/main/java/com/chattabs/ChatTabsMod.java`, add a branch next to `layout` (import `com.chattabs.ui.settings.ChatTabsSettingsScreen`):

```java
                        .then(ClientCommands.literal("settings")
                                .executes(context -> {
                                    Minecraft.getInstance().execute(() ->
                                            Minecraft.getInstance().gui.setScreen(
                                                    new ChatTabsSettingsScreen(null)));
                                    return 1;
                                }))
```

- [ ] **Step 5: Build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew build --console=plain 2>&1 | grep -E "Fehler|FAILED|BUILD" | head -8`
Expected: BUILD SUCCESSFUL. If the ModMenu artifact fails to resolve, verify the coordinates with
`curl -sI https://maven.terraformersmc.com/releases/com/terraformersmc/modmenu/20.0.1/modmenu-20.0.1.jar`.

- [ ] **Step 6: Commit**

```bash
git add build.gradle src/main/resources/fabric.mod.json src/main/java/com/chattabs/compat/ChatTabsModMenu.java src/main/java/com/chattabs/ChatTabsMod.java
git commit -m "Open settings from /chattabs settings and ModMenu

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Full clean build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home && ./gradlew clean build --console=plain 2>&1 | grep -E "FAILED|BUILD"`
Expected: BUILD SUCCESSFUL; jar in `build/libs/`.

- [ ] **Step 2: Confirm the mod loads without ModMenu**

Check that nothing outside `com.chattabs.compat` imports ModMenu classes:

Run: `grep -rn "terraformersmc" src/main/java --include="*.java" | grep -v "compat/"`
Expected: no output.

- [ ] **Step 3: Report the manual test checklist**

To verify in-game (this is the release the user wants to test):

1. `/chattabs settings` opens the screen; ModMenu (if installed) shows a configure button.
2. Tooltips appear on every control after a short hover.
3. Editing a channel shows the live preview: tag, sender, and message in distinct
   colors; a wrong tag turns the line red; pasting a real server line matches.
4. Validation blocks Done for a blank id/tag, the reserved id `all`, and duplicate ids.
5. Toggling V/S/B/P in a row and pressing Done changes tab behavior immediately
   (visibility, ping, badge, peek) without a game restart.
6. Cancel discards changes — reopening shows the previous values.
7. "Profile for this server" creates a server-specific profile that is picked on
   the next join, and the default profile stays untouched.
8. Adding a channel with a tag your server uses makes its tab work end to end
   (messages land there, clicking it sends the join command).
