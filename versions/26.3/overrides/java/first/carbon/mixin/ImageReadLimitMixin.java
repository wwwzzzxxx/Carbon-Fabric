package first.carbon.mixin;
import java.io.*;
import java.nio.ByteBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Preserve the encoded byte count across both old write-mode and newer flipped readers. */
@Mixin(NativeImage.class)
public abstract class ImageReadLimitMixin {
 @WrapOperation(method="read(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/TextureUtil;readResource(Ljava/io/InputStream;)Ljava/nio/ByteBuffer;"))
 private static ByteBuffer carbon$encodedLength(InputStream stream,Operation<ByteBuffer> original)throws IOException {
  long[] count={0};
  InputStream counted=new FilterInputStream(stream) {
   @Override public int read()throws IOException {int value=in.read();if(value>=0)count[0]++;return value;}
   @Override public int read(byte[] bytes,int offset,int length)throws IOException {int read=in.read(bytes,offset,length);if(read>0)count[0]+=read;return read;}
  };
  ByteBuffer data=original.call(counted);
  if(count[0]<=0 || count[0]>data.capacity()) {data.position(0);MemoryUtil.memFree(data);throw new IOException("Invalid encoded image length");}
  data.limit((int)count[0]);data.position(0);return data;
 }
}
