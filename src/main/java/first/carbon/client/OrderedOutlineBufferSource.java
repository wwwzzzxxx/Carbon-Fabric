package first.carbon.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.Map;

/** Uses the vanilla mask flush order when an optimization mod replaces the buffer factory. */
public final class OrderedOutlineBufferSource extends MultiBufferSource.BufferSource {
    public OrderedOutlineBufferSource(BufferBuilder buffer) {
        super(buffer, Map.of());
    }
}
