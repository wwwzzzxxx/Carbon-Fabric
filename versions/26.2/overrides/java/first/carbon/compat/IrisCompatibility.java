package first.carbon.compat;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.vertex.*;
import first.carbon.client.ItemBatchRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3f;
import java.util.*;
public final class IrisCompatibility {
    private boolean checked,present,active,blocked;
    private java.lang.reflect.Method enabled;
    private Object api;
    private final Map<Object,Object> programs=new HashMap<>(),layers=new HashMap<>();
    public int generation,draws;
    public void beginFrame() {
        draws=0;
        if(!checked) {checked=true;present=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris");
            if(present)try {var c=Class.forName("net.irisshaders.iris.api.v0.IrisApi");api=c.getMethod("getInstance").invoke(null);enabled=c.getMethod("isShaderPackInUse");}catch(ReflectiveOperationException e){blocked=true;}}
        if(present&&enabled!=null)try {boolean next=(boolean)enabled.invoke(api);if(next!=active){active=next;invalidate();}}catch(ReflectiveOperationException e){blocked=true;}
    }
    public boolean active(){return active;}
    public boolean canInstance() {
        return (!present || !blocked && !active)
                && ((first.carbon.mixin.GpuDeviceAccess)(Object)com.mojang.blaze3d.systems.RenderSystem.getDevice()).carbon$backend().getClass().getName().contains(".opengl.");
    }
    public boolean shadowPass(){return false;}
    public boolean vanillaShadows(){return !present;}
    public int[] captureItem(){return null;}
    public void restoreCapture(int[] value){}
    public long identifiers(){return 0;}
    public VertexConsumer getBuffer(RenderCompat.CaptureSource source,RenderType type){return source.getBuffer(type);}
    public RenderType unwrap(RenderType type){return type;}
    public void marker(ItemBatchRenderer.Target target){}
    public GlProgram shader(GlProgram selected,RenderType type,boolean dropped){return selected;}
    public boolean usesShader(GlProgram shader){return false;}
    public void apply(GlProgram shader){throw new IllegalStateException("Native shader programs require native uniform blocks");}
    public void restoreProgram(int program){org.lwjgl.opengl.GL33.glUseProgram(program);}
    public void draw(GlProgram shader,int mode,int instanceBuffer,int vertexBuffer,int indexBuffer,VertexFormat format,int indices,int count,Vector3f camera,float clock,float scale,Vector3f center,Vector3f halfSize,boolean flat){throw new IllegalStateException("Iris instancing is unavailable for this backend");}
    public void releaseMesh(int buffer){}
    public void invalidate(){generation++;}
    public interface LayerAccess {RenderType carbon$wrapped();}
}
