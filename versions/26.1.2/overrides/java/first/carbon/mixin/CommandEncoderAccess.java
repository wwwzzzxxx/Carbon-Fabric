package first.carbon.mixin;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(CommandEncoder.class) public interface CommandEncoderAccess {@Accessor("backend") CommandEncoderBackend carbon$backend();}
