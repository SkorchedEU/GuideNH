package com.hfstudio.guidenh.guide.internal.editor.guide;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import com.hfstudio.guidenh.guide.internal.GuidebookText;

public class GuideScreenEditorUnsavedPrompt extends GuiScreen {

    public interface Callback {

        void save();

        void discard();

        void cancel();
    }

    private static final int PROMPT_WIDTH = 300;
    private static final int PROMPT_HEIGHT = 118;
    private static final int BUTTON_WIDTH = 82;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;

    private final GuiScreen parent;
    private final Callback callback;

    public GuideScreenEditorUnsavedPrompt(GuiScreen parent, Callback callback) {
        this.parent = parent;
        this.callback = callback;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int baseX = (this.width - PROMPT_WIDTH) / 2;
        int baseY = (this.height - PROMPT_HEIGHT) / 2;
        int buttonY = baseY + PROMPT_HEIGHT - 28;
        int totalWidth = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;
        int buttonX = baseX + (PROMPT_WIDTH - totalWidth) / 2;
        this.buttonList.add(
            new GuiButton(
                0,
                buttonX,
                buttonY,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                GuidebookText.GuideEditorUnsavedSave.text()));
        this.buttonList.add(
            new GuiButton(
                1,
                buttonX + BUTTON_WIDTH + BUTTON_GAP,
                buttonY,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                GuidebookText.GuideEditorUnsavedDiscard.text()));
        this.buttonList.add(
            new GuiButton(
                2,
                buttonX + (BUTTON_WIDTH + BUTTON_GAP) * 2,
                buttonY,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                GuidebookText.GuideEditorUnsavedCancel.text()));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            callback.save();
            return;
        }
        if (button.id == 1) {
            callback.discard();
            return;
        }
        cancel();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancel();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int baseX = (this.width - PROMPT_WIDTH) / 2;
        int baseY = (this.height - PROMPT_HEIGHT) / 2;
        drawRect(baseX, baseY, baseX + PROMPT_WIDTH, baseY + PROMPT_HEIGHT, 0xF0181C22);
        drawRect(baseX, baseY, baseX + PROMPT_WIDTH, baseY + 1, 0xFF4D5661);
        drawRect(baseX, baseY + PROMPT_HEIGHT - 1, baseX + PROMPT_WIDTH, baseY + PROMPT_HEIGHT, 0xFF4D5661);
        drawCenteredString(
            fontRendererObj,
            GuidebookText.GuideEditorUnsavedTitle.text(),
            this.width / 2,
            baseY + 12,
            0xFFF0F0F0);
        fontRendererObj.drawSplitString(
            GuidebookText.GuideEditorUnsavedMessage.text(),
            baseX + 12,
            baseY + 34,
            PROMPT_WIDTH - 24,
            0xFFD0D8E0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void cancel() {
        callback.cancel();
        mc.displayGuiScreen(parent);
    }
}
