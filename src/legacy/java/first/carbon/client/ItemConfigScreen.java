package first.carbon.client;
import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
public final class ItemConfigScreen extends Screen {
    private final Screen parent;
    public ItemConfigScreen(Screen parent) { super(new TextComponent("Carbon")); this.parent = parent; }
    @Override protected void init() {
        addButton(new Button(width / 2 - 110, height / 2 - 30, 220, 20,
                new TextComponent("Model batching: " + Carbon.CONFIG.enabled.get()), button -> {
                    Carbon.CONFIG.enabled.set(!Carbon.CONFIG.enabled.get()); Carbon.CONFIG.save();
                    button.setMessage(new TextComponent("Model batching: " + Carbon.CONFIG.enabled.get()));
                }));
        addButton(new Button(width / 2 - 110, height / 2 + 20, 220, 20, new TextComponent("Done"), button -> onClose()));
    }
    @Override public void render(PoseStack pose, int x, int y, float partial) {
        renderBackground(pose); drawCenteredString(pose, font, title, width / 2, height / 2 - 70, 0xFFFFFF);
        drawCenteredString(pose, font, "GPU dropped items / Iris shader fallback", width / 2, height / 2, 0xBBBBBB);
        super.render(pose, x, y, partial);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
