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
import java.util.function.Consumer;

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
                           Consumer<Boolean> setter) {
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

        String title = getTitle().getString();
        context.text(font, title, width / 2 - font.width(title) / 2, 14, 0xFFFFFFFF, true);

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
