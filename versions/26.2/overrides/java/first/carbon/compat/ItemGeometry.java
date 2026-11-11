package first.carbon.compat;
import java.util.*;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
public final class ItemGeometry {
    public final List<BakedQuad> quads;
    public final ItemTransform transform;
    public final AABB bounds;
    private ItemGeometry(List<BakedQuad> quads,ItemTransform transform,AABB bounds) {this.quads=quads;this.transform=transform;this.bounds=bounds;}
    private record Key(List<BakedQuad> quads,ItemTransform transform,AABB bounds) {}
    private static final LinkedHashMap<Key,ItemGeometry> models=new LinkedHashMap<>(256,.75f,true);
    public static ItemGeometry get(List<BakedQuad> quads,ItemTransform transform,AABB bounds) {
        var key=new Key(List.copyOf(quads),transform,bounds);
        var value=models.get(key);
        if(value==null) {value=new ItemGeometry(key.quads,transform,bounds);models.put(key,value);if(models.size()>512)models.remove(models.keySet().iterator().next());}
        return value;
    }
    public static ItemGeometry plain(List<BakedQuad> quads) {return get(quads,ItemTransform.NO_TRANSFORM,new AABB(0,0,0,0,0,0));}
    public static void clear() {models.clear();}
    public List<BakedQuad> getQuads(Object state,Direction side,RandomSource random) {return side==null ? quads : List.of();}
    public boolean isGui3d() {return bounds.getZsize()>.0625;}
    public ItemGeometry getTransforms() {return this;}
    public ItemTransform getTransform(Object context) {return transform;}
    public float hoverScale() {return (float)(-bounds.minY+.0625)*4;}
}
