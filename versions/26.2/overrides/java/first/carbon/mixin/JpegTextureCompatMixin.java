package first.carbon.mixin;
import java.nio.ByteBuffer;
import net.minecraft.util.PngInfo;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Older resource packs sometimes store JPEG data under a .png resource name. */
@Mixin(PngInfo.class)
public abstract class JpegTextureCompatMixin {
    @Inject(method="validateHeader",at=@At("HEAD"),cancellable=true)
    private static void carbon$jpeg(ByteBuffer bytes,CallbackInfo ci) throws java.io.IOException {
        int start=bytes.position();
        if(bytes.remaining()<3 || (bytes.get(start)&255)!=255 || (bytes.get(start+1)&255)!=216 || (bytes.get(start+2)&255)!=255)return;
        try(MemoryStack stack=MemoryStack.stackPush()) {
            var width=stack.mallocInt(1);var height=stack.mallocInt(1);var channels=stack.mallocInt(1);
            if(STBImage.stbi_info_from_memory(bytes,width,height,channels) && width.get(0)>0 && height.get(0)>0
                    && width.get(0)<=16384 && height.get(0)<=16384 && (long)width.get(0)*height.get(0)<=16777216L) {
                // NativeImage keeps its normal decoder, allocation checks and matching STB free.
                ci.cancel();
            } else {throw new java.io.IOException("Invalid JPEG header or dimensions exceed the 16 megapixel compatibility limit");}
        }
    }
}
