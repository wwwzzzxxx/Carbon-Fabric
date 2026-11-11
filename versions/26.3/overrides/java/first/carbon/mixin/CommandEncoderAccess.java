package first.carbon.mixin;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="com.mojang.renderpearl.frontend.FrontendCommandEncoder") public interface CommandEncoderAccess {@Accessor("backend") CommandEncoderBackend carbon$backend();}
