package first.carbon.mixin;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="com.mojang.renderpearl.frontend.FrontendGpuDevice") public interface GpuDeviceAccess {@Accessor("backend") GpuDeviceBackend carbon$backend();}
