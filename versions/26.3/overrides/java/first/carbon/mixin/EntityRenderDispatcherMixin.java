package first.carbon.mixin;
import first.carbon.client.DroppedItemBatchRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(EntityRenderDispatcher.class) public abstract class EntityRenderDispatcherMixin implements DroppedItemBatchRenderer.ShadowSettings {
    public boolean imo$shadows(){return Minecraft.getInstance().options.entityShadows().get();}
}
