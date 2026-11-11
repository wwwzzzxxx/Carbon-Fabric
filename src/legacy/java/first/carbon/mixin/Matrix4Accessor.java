package first.carbon.mixin;
import com.mojang.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Matrix4f.class)
public interface Matrix4Accessor {
    @Accessor("m00") void carbon$set00(float value);
    @Accessor("m01") void carbon$set01(float value);
    @Accessor("m02") void carbon$set02(float value);
    @Accessor("m03") void carbon$set03(float value);
    @Accessor("m10") void carbon$set10(float value);
    @Accessor("m11") void carbon$set11(float value);
    @Accessor("m12") void carbon$set12(float value);
    @Accessor("m13") void carbon$set13(float value);
    @Accessor("m20") void carbon$set20(float value);
    @Accessor("m21") void carbon$set21(float value);
    @Accessor("m22") void carbon$set22(float value);
    @Accessor("m23") void carbon$set23(float value);
    @Accessor("m30") void carbon$set30(float value);
    @Accessor("m31") void carbon$set31(float value);
    @Accessor("m32") void carbon$set32(float value);
    @Accessor("m33") void carbon$set33(float value);
}
