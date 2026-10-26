package com.chattabs.ui.settings;

import com.chattabs.ChatTabsConfig;
import com.chattabs.chat.MentionMatcher;
import com.chattabs.config.ChannelDefinition;
import com.chattabs.config.ServerProfile;
import com.chattabs.config.SettingsDraft;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChatTabsSettingsScreen extends Screen {
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int TAG_COLOR = 0xFF4CAF50;
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE_ROWS = 5;
    private static final int ROW_STEP = 24;

    private static final List<Integer> IDLE_CHOICES = List.of(-1, 15, 30, 60, 120, 300);
    private static final List<Integer> PEEK_LINE_CHOICES = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    private final Screen parent;
    private final SettingsDraft draft;
    private int profileIndex;
    private int scrollOffset;
    private int listTop;
    private int visibleRows = MAX_VISIBLE_ROWS;

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
                .displayOnlyValue()
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

        y += ROW_STEP;

        // --- General ---
        addRenderableWidget(CycleButton.<ChatTabsConfig.ChatMode>builder(
                        mode -> Component.literal(mode == ChatTabsConfig.ChatMode.FILTERED
                                ? "Chat mode: Filtered tabs" : "Chat mode: Single window"),
                        draft.chatMode())
                .withValues(List.of(ChatTabsConfig.ChatMode.FILTERED, ChatTabsConfig.ChatMode.SINGLE_WINDOW))
                .withTooltip(mode -> Tooltip.create(Component.literal(
                        "Filtered: each tab shows only its own messages. Single window: every message stays visible and tabs only steer where you speak.")))
                .displayOnlyValue()
                .create(left, y, 176, 20, Component.literal("Chat mode"),
                        (button, value) -> draft.setChatMode(value)));

        addRenderableWidget(CycleButton.<Integer>builder(
                        seconds -> Component.literal("Idle switch: " + (seconds <= 0 ? "Never" : seconds + "s")),
                        nearestIdleChoice(draft.idleSwitchSeconds()))
                .withValues(IDLE_CHOICES)
                .withTooltip(seconds -> Tooltip.create(Component.literal(
                        "Switch back to the All tab after this much silence in the active tab.")))
                .displayOnlyValue()
                .create(right, y, 176, 20, Component.literal("Idle switch"),
                        (button, value) -> draft.setIdleSwitchSeconds(value)));

        y += ROW_STEP;

        addRenderableWidget(CycleButton.onOffBuilder(draft.peekEnabled())
                .withTooltip(value -> Tooltip.create(Component.literal(
                        "Show recent messages of peek-enabled channels in a small overlay while another tab is active.")))
                .create(left, y, 176, 20, Component.literal("Peek overlay"),
                        (button, value) -> draft.setPeekEnabled(value)));

        addRenderableWidget(CycleButton.<Integer>builder(lines -> Component.literal("Peek lines: " + lines),
                        Math.max(1, Math.min(10, draft.peekLines())))
                .withValues(PEEK_LINE_CHOICES)
                .withTooltip(lines -> Tooltip.create(Component.literal(
                        "How many message lines the peek overlay keeps.")))
                .displayOnlyValue()
                .create(right, y, 176, 20, Component.literal("Peek lines"),
                        (button, value) -> draft.setPeekLines(value)));

        y += ROW_STEP;

        EditBox separatorBox = new EditBox(font, left, y, 86, 20, Component.literal("Separator"));
        separatorBox.setMaxLength(8);
        separatorBox.setValue(draft.profile(profileIndex).getSeparator());
        separatorBox.setResponder(text -> draft.setSeparator(profileIndex, text));
        separatorBox.setTooltip(Tooltip.create(Component.literal(
                "Character between sender and message in this server's chat lines, usually >.")));
        addRenderableWidget(separatorBox);

        addRenderableWidget(CycleButton.onOffBuilder(draft.profile(profileIndex).isSystemTab())
                .withTooltip(value -> Tooltip.create(Component.literal(
                        "A System tab with server messages (joins, shop notices, command replies). They stay in All too.")))
                .create(left + 90, y, 86, 20, Component.literal("System"),
                        (button, value) -> draft.setSystemTab(profileIndex, value)));

        addRenderableWidget(Button.builder(Component.literal("Layout editor"),
                        button -> minecraft.gui.setScreen(new PeekPositionScreen()))
                .bounds(right, y, 176, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Drag the chat window and the peek overlay to where you want them.")))
                .build());

        y += ROW_STEP;

        addRenderableWidget(CycleButton.onOffBuilder(draft.profile(profileIndex).isMentionAlert())
                .withTooltip(value -> Tooltip.create(Component.literal(
                        "When someone writes one of your names: unread badge and a ping, even in quiet channels. "
                                + "Lines without their own tab (e.g. Global) badge the All tab.")))
                .create(left, y, 176, 20, Component.literal("Mention alert"),
                        (button, value) -> draft.setMentionAlert(profileIndex, value)));

        EditBox namesBox = new EditBox(font, right, y, 176, 20, Component.literal("My names"));
        namesBox.setMaxLength(256);
        namesBox.setHint(Component.literal("My names, e.g. Unicorn, UB"));
        namesBox.setValue(MentionMatcher.joinNames(draft.profile(profileIndex).getMentionNames()));
        namesBox.setResponder(text -> draft.setMentionNames(profileIndex, text));
        namesBox.setTooltip(Tooltip.create(Component.literal(
                "Other names people call you, comma-separated. Your player name always counts. "
                        + "Whole words only, case does not matter.")));
        addRenderableWidget(namesBox);

        // --- Channel rows ---
        listTop = y + 34;
        // Fit the list between the settings and the footer; scroll the rest.
        visibleRows = Math.max(2, Math.min(MAX_VISIBLE_ROWS, (height - 34 - listTop) / ROW_HEIGHT));
        List<ChannelDefinition> channels = draft.profile(profileIndex).channelsForEditing();
        int maxOffset = Math.max(0, channels.size() - visibleRows);
        scrollOffset = Math.min(scrollOffset, maxOffset);

        int shownRows = Math.min(visibleRows, channels.size() - scrollOffset);
        for (int row = 0; row < shownRows; row++) {
            addChannelRow(left, listTop + row * ROW_HEIGHT, scrollOffset + row);
        }

        if (channels.size() > visibleRows) {
            addRenderableWidget(Button.builder(Component.literal("^"), button -> {
                        scrollOffset = Math.max(0, scrollOffset - 1);
                        rebuild();
                    }).bounds(left + 284, listTop, 20, 20)
                    .tooltip(Tooltip.create(Component.literal("Scroll up"))).build());
            addRenderableWidget(Button.builder(Component.literal("v"), button -> {
                        scrollOffset = Math.min(maxOffset, scrollOffset + 1);
                        rebuild();
                    }).bounds(left + 284, listTop + 22, 20, 20)
                    .tooltip(Tooltip.create(Component.literal("Scroll down"))).build());
        }

        // --- Footer ---
        addRenderableWidget(Button.builder(Component.literal("Add channel"),
                        button -> minecraft.gui.setScreen(new ChannelEditScreen(this, draft, profileIndex, -1)))
                .bounds(width / 2 - 178, height - 28, 116, 20)
                .tooltip(Tooltip.create(Component.literal("Define another chat channel for this profile.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> saveAndClose())
                .bounds(width / 2 - 58, height - 28, 116, 20)
                .tooltip(Tooltip.create(Component.literal("Save all changes and apply them right away.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(width / 2 + 62, height - 28, 116, 20)
                .tooltip(Tooltip.create(Component.literal("Discard every change made in this screen.")))
                .build());
    }

    private void addChannelRow(int left, int rowY, int channelIndex) {
        ChannelDefinition channel = draft.profile(profileIndex).channelsForEditing().get(channelIndex);

        addRenderableWidget(Button.builder(Component.literal(channel.getDisplayName()),
                        button -> minecraft.gui.setScreen(
                                new ChannelEditScreen(this, draft, profileIndex, channelIndex)))
                .bounds(left, rowY, 110, 20)
                .tooltip(Tooltip.create(Component.literal("Edit " + channel.getDisplayName()
                        + " (tag " + channel.getTag() + ")")))
                .build());

        addToggleCell(left + 114, rowY, "V", channel.isAlwaysVisible(),
                "Always visible. Off: the tab appears on activity and can be closed.",
                value -> replaceChannel(channelIndex,
                        withFlags(channel, value, channel.isSound(), channel.isBadge(), channel.isPeek())));
        addToggleCell(left + 156, rowY, "S", channel.isSound(),
                "Play a notification sound for this channel.",
                value -> replaceChannel(channelIndex,
                        withFlags(channel, channel.isAlwaysVisible(), value, channel.isBadge(), channel.isPeek())));
        addToggleCell(left + 198, rowY, "B", channel.isBadge(),
                "Show an unread counter on this tab.",
                value -> replaceChannel(channelIndex,
                        withFlags(channel, channel.isAlwaysVisible(), channel.isSound(), value, channel.isPeek())));
        addToggleCell(left + 240, rowY, "P", channel.isPeek(),
                "Include this channel in the peek overlay.",
                value -> replaceChannel(channelIndex,
                        withFlags(channel, channel.isAlwaysVisible(), channel.isSound(), channel.isBadge(), value)));

        addRenderableWidget(Button.builder(Component.literal("x"), button -> {
                    draft.removeChannel(profileIndex, channelIndex);
                    rebuild();
                })
                .bounds(left + 350, rowY, 20, 20)
                .tooltip(Tooltip.create(Component.literal("Remove this channel from the profile.")))
                .build());
    }

    private void addToggleCell(int x, int y, String label, boolean initial, String tooltip,
                               Consumer<Boolean> setter) {
        addRenderableWidget(CycleButton.onOffBuilder(initial)
                .displayOnlyValue()
                .withTooltip(value -> Tooltip.create(Component.literal(label + " — " + tooltip)))
                .create(x, y, 38, 20, Component.literal(label), (button, value) -> setter.accept(value)));
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
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < draft.profileCount(); i++) {
            indices.add(i);
        }
        return indices;
    }

    private String profileLabel(int index) {
        ServerProfile profile = draft.profile(index);
        return "Profile: " + (profile.isDefaultProfile() ? "default (all servers)" : profile.getServerAddress());
    }

    private int nearestIdleChoice(int seconds) {
        return IDLE_CHOICES.contains(seconds) ? seconds : -1;
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
        context.text(font, title, width / 2 - font.width(title) / 2, 12, 0xFFFFFFFF, true);

        int left = width / 2 - 180;
        context.text(font, "Channels of this profile", left, listTop - 12, LABEL_COLOR, false);

        List<ChannelDefinition> channels = draft.profile(profileIndex).channelsForEditing();
        if (channels.isEmpty()) {
            context.text(font, "No channels yet — add one below.", left, listTop + 6, LABEL_COLOR, false);
            return;
        }

        int shownRows = Math.min(visibleRows, channels.size() - scrollOffset);
        for (int row = 0; row < shownRows; row++) {
            ChannelDefinition channel = channels.get(scrollOffset + row);
            context.text(font, channel.getTag(), left + 312, listTop + row * ROW_HEIGHT + 6, TAG_COLOR, false);
        }
    }
}
