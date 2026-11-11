package first.carbon.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import first.carbon.Carbon;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ClientEvents {
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> Carbon.Client.DROPPED_RENDERER.clientTick());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public ResourceLocation getFabricId() { return new ResourceLocation("carbon", "models"); }
            public void onResourceManagerReload(ResourceManager manager) { Carbon.Client.RENDERER.invalidate(); Carbon.Client.IRIS.invalidate(); }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Carbon.Client.RENDERER.invalidate());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Carbon.Client.RENDERER.invalidate());
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("carbon")
            .then(ClientCommandManager.literal("enabled").then(ClientCommandManager.argument("enabled", BoolArgumentType.bool()).executes(context -> {
                Carbon.CONFIG.enabled.set(BoolArgumentType.getBool(context, "enabled")); Carbon.CONFIG.save(); return 1;
            })))
            .then(ClientCommandManager.literal("config").executes(context -> {
                final net.minecraft.client.Minecraft client = context.getSource().getClient();
                client.tell(() -> client.setScreen(new ItemConfigScreen(client.screen))); return 1;
            }))
            .then(ClientCommandManager.literal("stats").executes(context -> {
                context.getSource().sendFeedback(new TextComponent(Carbon.Client.DROPPED_RENDERER.statistics() + " | " + Carbon.Client.RENDERER.statistics()
                        + " | " + (Carbon.Client.IRIS.active() ? "Iris shader: native rendering" : "legacy GPU backend"))); return 1;
            })));
    }
}
