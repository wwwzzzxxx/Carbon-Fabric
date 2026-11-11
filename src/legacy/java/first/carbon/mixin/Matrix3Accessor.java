package first.carbon.mixin;
import com.mojang.math.Matrix3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Matrix3f.class)
public interface Matrix3Accessor {
    @Accessor("m00") float carbon$get00();
    @Accessor("m00") void carbon$set00(float value);
    @Accessor("m01") float carbon$get01();
    @Accessor("m01") void carbon$set01(float value);
    @Accessor("m02") float carbon$get02();
    @Accessor("m02") void carbon$set02(float value);
    @Accessor("m10") float carbon$get10();
    @Accessor("m10") void carbon$set10(float value);
    @Accessor("m11") float carbon$get11();
    @Accessor("m11") void carbon$set11(float value);
    @Accessor("m12") float carbon$get12();
    @Accessor("m12") void carbon$set12(float value);
    @Accessor("m20") float carbon$get20();
    @Accessor("m20") void carbon$set20(float value);
    @Accessor("m21") float carbon$get21();
    @Accessor("m21") void carbon$set21(float value);
    @Accessor("m22") float carbon$get22();
    @Accessor("m22") void carbon$set22(float value);
}
