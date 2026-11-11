package first.carbon.client;

import first.carbon.Carbon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class ItemConfigScreen extends Screen {
    private final Screen parent;

    public ItemConfigScreen(Screen parent) {
        super(net.minecraft.network.chat.Component.translatable("carbon.configuration.title", "Carbon"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var settings = java.util.List.of(Carbon.CONFIG.enabled,
                Carbon.CONFIG.optimizeDroppedItems, Carbon.CONFIG.physicalDroppedItems, Carbon.CONFIG.skipVisibilityChecks);
        String[] names = {"enabled", "optimizeDroppedItems", "physicalDroppedItems", "skipVisibilityChecks"};
        for (int i = 0; i < settings.size(); i++) {
            var setting = settings.get(i);
            String key = names[i];
            addRenderableWidget(CycleButton.onOffBuilder(setting.get())
                    .create(width / 2 - 100, height / 2 - 48 + i * 24, 200, 20,
                            net.minecraft.network.chat.Component.translatable("carbon.configuration." + key), (button, value) -> {
                                setting.set(value);
                                Carbon.CONFIG.spec.save();
                                Carbon.Client.RENDERER.invalidate();
                                Carbon.Client.DROPPED_RENDERER.invalidate();
                            }));
        }
        var budget = addRenderableWidget(new AbstractSliderButton(width / 2 - 100, height / 2 + 48, 200, 20,
                net.minecraft.network.chat.Component.translatable("carbon.configuration.cacheUpdateBudgetMs").append(": " + Carbon.CONFIG.cacheUpdateBudgetMs.get() + " ms"),
                Carbon.CONFIG.cacheUpdateBudgetMs.get() / 20) {
            @Override
            protected void updateMessage() {
                setMessage(net.minecraft.network.chat.Component.translatable("carbon.configuration.cacheUpdateBudgetMs").append(": " + Math.round(value * 200) / 10.0 + " ms"));
            }

            @Override
            protected void applyValue() {
                Carbon.CONFIG.cacheUpdateBudgetMs.set(Math.round(value * 200) / 10.0);
                Carbon.CONFIG.spec.save();
            }
        });
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(width / 2 - 100, height - 40, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 30, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
