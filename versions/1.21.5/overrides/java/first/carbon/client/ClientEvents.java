package first.carbon.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import first.carbon.Carbon;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ClientEvents {
    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public ResourceLocation getFabricId() { return ResourceLocation.fromNamespaceAndPath("carbon", "models"); }
            public void onResourceManagerReload(ResourceManager manager) {
                Carbon.Client.IRIS.invalidate(); Carbon.Client.RENDERER.invalidate(); Carbon.Client.DROPPED_RENDERER.invalidate();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Carbon.Client.RENDERER.invalidate(); Carbon.Client.DROPPED_RENDERER.invalidate();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Carbon.Client.RENDERER.invalidate(); Carbon.Client.DROPPED_RENDERER.invalidate(); Carbon.Client.IRIS.invalidate();
        });
        var command = ClientCommandManager.literal("carbon");
        command.then(ClientCommandManager.literal("enabled").then(ClientCommandManager.argument("enabled", BoolArgumentType.bool()).executes(context -> {
            Carbon.CONFIG.enabled.set(BoolArgumentType.getBool(context, "enabled")); Carbon.CONFIG.save(); return 1;
        })));
        command.then(ClientCommandManager.literal("physics").then(ClientCommandManager.argument("enabled", BoolArgumentType.bool()).executes(context -> {
            Carbon.CONFIG.physicalDroppedItems.set(BoolArgumentType.getBool(context, "enabled")); Carbon.CONFIG.save(); return 1;
        })));
        command.then(ClientCommandManager.literal("config").executes(context -> {
            var client = context.getSource().getClient();
            client.execute(() -> client.setScreen(new ItemConfigScreen(client.screen))); return 1;
        }));
        command.then(ClientCommandManager.literal("stats").executes(context -> {
            context.getSource().sendFeedback(Component.literal(Carbon.Client.RENDERER.statistics() + "; dropped: " + Carbon.Client.DROPPED_RENDERER.statistics())); return 1;
        }));
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(command));
    }
}
