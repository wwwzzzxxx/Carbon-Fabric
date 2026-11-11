package first.carbon.compat;

import com.mojang.blaze3d.vertex.*;
import first.carbon.Carbon;
import first.carbon.client.ItemBatchRenderer;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.opengl.GlProgram;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL43.*;

// Iris 的格式、通道和着色器适配均集中在此，不注入 Iris 类。
public final class IrisCompatibility {
    private final String namespace = "net.irisshaders.iris.";
    private boolean checked, present, active, blocked, bridgeReady;
    private MethodHandle packInUse, shadowPass, pipelineGetter, getEntity, setEntity, getItem, setItem, getBlock, setBlock;
    private Object pipeline;
    private Object settings;
    private Object itemEntityId;
    private Method entityIds;
    private final Map<Long, Program> programs = new HashMap<>();
    private final IdentityHashMap<MultiBufferSource.BufferSource, IdentityHashMap<RenderType, RenderType>> layers = new IdentityHashMap<>();
    private final IdentityHashMap<Class<?>, java.lang.reflect.Field> sourceDelegates = new IdentityHashMap<>();
    public int generation;
    public int draws;

    public void beginFrame() {
        draws = 0;
        if (!checked) {
            checked = true;
            try {
                var lookup = MethodHandles.publicLookup();
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                packInUse = lookup.unreflect(api.getMethod("isShaderPackInUse")).bindTo(instance);
                present = true;
                active = (boolean) packInUse.invokeExact();
                shadowPass = lookup.unreflect(Class.forName(namespace + "shadows.ShadowRenderingState").getMethod("areShadowsCurrentlyBeingRendered"));
                Object manager = Class.forName(namespace + "Iris").getMethod("getPipelineManager").invoke(null);
                pipelineGetter = lookup.unreflect(manager.getClass().getMethod("getPipelineNullable")).bindTo(manager);
                Class<?> captured = Class.forName(namespace + "uniforms.CapturedRenderingState");
                Object state = captured.getField("INSTANCE").get(null);
                getEntity = lookup.unreflect(captured.getMethod("getCurrentRenderedEntity")).bindTo(state);
                setEntity = lookup.unreflect(captured.getMethod("setCurrentEntity", int.class)).bindTo(state);
                getItem = lookup.unreflect(captured.getMethod("getCurrentRenderedItem")).bindTo(state);
                setItem = lookup.unreflect(captured.getMethod("setCurrentRenderedItem", int.class)).bindTo(state);
                getBlock = lookup.unreflect(captured.getMethod("getCurrentRenderedBlockEntity")).bindTo(state);
                setBlock = lookup.unreflect(captured.getMethod("setCurrentBlockEntity", int.class)).bindTo(state);
                Class<?> world = Class.forName(namespace + "shaderpack.materialmap.WorldRenderingSettings");
                settings = world.getField("INSTANCE").get(null);
                entityIds = world.getMethod("getEntityIds");
                itemEntityId = Class.forName(namespace + "shaderpack.materialmap.NamespacedId").getConstructor(String.class, String.class).newInstance("minecraft", "item");
                bridgeReady = true;
                present = true;
            } catch (ClassNotFoundException exception) {
                blocked = present;
                if (present) LoggerFactory.getLogger("Carbon").warn("Iris bridge class unavailable; retaining native rendering", exception);
            } catch (Throwable exception) {
                blocked = true;
                LoggerFactory.getLogger("Carbon").warn("Iris bridge initialization unavailable", exception);
            }
        }
        if (present) {
            try {
                boolean enabled = (boolean) packInUse.invokeExact();
                Object current = pipelineGetter.invoke();
                if (current != pipeline || active != enabled) {
                    invalidate();
                    pipeline = current;
                    active = enabled;
                }
            } catch (Throwable exception) {
                blocked = true;
            }
        }
    }

    public boolean active() {
        return active;
    }

    public void apply(GlProgram shader) {
        if (active) {
            glUseProgram(shader.getProgramId());
        }
        first.carbon.compat.RenderCompat.applyShader(shader);
    }

    public void restoreProgram(int program) {
        glUseProgram(program);
    }

    public boolean canInstance() {
        return !present || !blocked && (!active || GL.getCapabilities().OpenGL43);
    }

    public boolean shadowPass() {
        boolean shadow = false;
        if (active) {
            try { shadow = (boolean) shadowPass.invokeExact(); }
            catch (Throwable exception) { blocked = true; }
        }
        return shadow;
    }

    public boolean vanillaShadows() {
        boolean allowed = true;
        if (active && pipeline != null) {
            try { allowed = !(boolean) pipeline.getClass().getMethod("shouldDisableVanillaEntityShadows").invoke(pipeline); }
            catch (ReflectiveOperationException ignored) { allowed = false; }
        }
        return allowed;
    }

    @SuppressWarnings("unchecked")
    public int[] captureItem() {
        int[] previous = null;
        if (active) {
            try {
                previous = new int[]{(int) getEntity.invokeExact(), (int) getItem.invokeExact(), (int) getBlock.invokeExact()};
                Object ids = entityIds.invoke(settings);
                int entity = ids == null ? 0 : ((ToIntFunction<Object>) ids).applyAsInt(itemEntityId);
                setEntity.invokeExact(entity);
            } catch (Throwable exception) { blocked = true; }
        }
        return previous;
    }

    public void restoreCapture(int[] previous) {
        if (previous != null) {
            try {
                setEntity.invokeExact(previous[0]);
                setItem.invokeExact(previous[1]);
                setBlock.invokeExact(previous[2]);
            } catch (Throwable exception) { blocked = true; }
        }
    }

    public long identifiers() {
        long value = 0;
        if (active) {
            try {
                value = ((int) getEntity.invokeExact() & 65535L) | (((int) getBlock.invokeExact() & 65535L) << 16)
                        | (((int) getItem.invokeExact() & 65535L) << 32);
            } catch (Throwable exception) { blocked = true; }
        }
        return value;
    }

    public VertexConsumer getBuffer(MultiBufferSource source, RenderType type) {
        MultiBufferSource original = source;
        if (present && canInstance() && Carbon.CONFIG.enabled.get()) {
            boolean shadow = shadowPass();
            if (shadow && (type == RenderType.glint() || type == RenderType.glintTranslucent()
                    || type == RenderType.entityGlint() || type == RenderType.entityGlint())) return source.getBuffer(type);
            try {
                for (int depth = 0; depth < 8; depth++) {
                    String name = original.getClass().getName();
                    if (name.equals(namespace + "layer.BufferSourceWrapper")
                            || name.equals("net.irisshaders.iris.layer.BufferSourceWrapper")
                            || original.getClass().isSynthetic()
                            && name.startsWith(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class.getName() + "$$Lambda")) {
                        var field = sourceDelegates.get(original.getClass());
                        if (field == null) {
                            for (var candidate : original.getClass().getDeclaredFields()) {
                                if (MultiBufferSource.class.isAssignableFrom(candidate.getType())) {
                                    candidate.setAccessible(true);
                                    field = candidate;
                                    sourceDelegates.put(original.getClass(), candidate);
                                    break;
                                }
                            }
                        }
                        MultiBufferSource delegate = field == null ? null : (MultiBufferSource) field.get(original);
                        if (delegate == null || delegate == original) break;
                        original = delegate;
                    } else break;
                }
            } catch (ReflectiveOperationException exception) { original = source; }
            if (original instanceof MultiBufferSource.BufferSource owner
                    && (shadow || !owner.getClass().equals(MultiBufferSource.BufferSource.class))) {
                RenderType layer = layers.computeIfAbsent(owner, key -> new IdentityHashMap<>()).computeIfAbsent(type, key -> {
                    try {
                        return (RenderType) Class.forName("first.carbon.compat.IrisLayer").getConstructor(MultiBufferSource.BufferSource.class, RenderType.class).newInstance(owner, key);
                    } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
                });
                VertexConsumer result = source.getBuffer(layer);
                if (result instanceof ItemBatchRenderer.Target target) target.imo$target(owner, layer);
                return result;
            }
        }
        VertexConsumer result = source.getBuffer(type);
        if (result instanceof ItemBatchRenderer.Target target && source instanceof MultiBufferSource.BufferSource owner) target.imo$target(owner, type);
        return result;
    }

    public RenderType unwrap(RenderType type) {
        return type instanceof LayerAccess layer ? layer.carbon$wrapped() : type;
    }

    public void marker(ItemBatchRenderer.Target target) {
        if (target.imo$type() instanceof LayerAccess && target instanceof VertexConsumer vertices) {
            for (int vertex = 0; vertex < 4; vertex++) {
                vertices.addVertex(0, 0, 0).setColor(255, 255, 255, 0).setUv(0, 0).setOverlay(655360).setLight(0).setNormal(0, 1, 0);
            }
        }
    }

    public GlProgram shader(GlProgram selected, RenderType type, boolean dropped) {
        if (active && pipeline != null && (dropped || type instanceof LayerAccess)) {
            try {
                RenderType original = unwrap(type);
                String key;
                if (shadowPass()) key = "SHADOW_ENTITIES_CUTOUT";
                else if (original == RenderType.glint() || original == RenderType.glintTranslucent() || original == RenderType.entityGlint() || original == RenderType.entityGlint()) key = "GLINT";
                else if (original == RenderType.entitySolid(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)) key = "ENTITIES_SOLID_DIFFUSE";
                else if (original == RenderType.itemEntityTranslucentCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                        || original == RenderType.itemEntityTranslucentCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)) key = "ENTITIES_TRANSLUCENT";
                else key = "ENTITIES_CUTOUT_DIFFUSE";
                Object shaders = pipeline.getClass().getMethod("getShaderMap").invoke(pipeline);
                Class<?> keys = Class.forName(namespace + "pipeline.programs.ShaderKey");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object shaderKey = Enum.valueOf((Class) keys, key);
                Object shader = shaders.getClass().getMethod("getShader", keys).invoke(shaders, shaderKey);
                if (shader instanceof GlProgram instance) return instance;
            } catch (ReflectiveOperationException exception) {
                blocked = true;
            }
        }
        return selected;
    }

    public boolean usesShader(GlProgram shader) {
        return active && shader != null && shader.getClass().getName().startsWith(namespace);
    }

    public void draw(GlProgram shader, int mode, int instanceBuffer, int vertexBuffer, int indexBuffer,
                     VertexFormat format, int indices, int count, Vector3f camera, float clock, float scale,
                     Vector3f center, Vector3f halfSize, boolean flat) {
        if (!blocked) {
            try (MemoryStack memory = MemoryStack.stackPush()) {
                long key = (long) shader.getProgramId() << 3 | mode;
                Program program = programs.get(key);
                if (program == null) {
                    program = new Program(shader.getProgramId(), mode);
                    programs.put(key, program);
                }
                int binding = program.binding;
                int previous = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, binding);
                long offset = previous == 0 ? 0 : glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START, binding);
                long length = previous == 0 ? 0 : glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE, binding);
                int generic = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
                try {
                    glUseProgram(program.id);
                    FloatBuffer floats = memory.mallocFloat(16);
                    IntBuffer integers = memory.mallocInt(16);
                    for (Uniform uniform : program.uniforms) uniform.copy(shader.getProgramId(), floats, integers);
                    glUniform3f(program.camera, camera.x, camera.y, camera.z);
                    glUniform1f(program.clock, clock);
                    glUniform1f(program.scale, scale);
                    glUniform3f(program.center, center.x, center.y, center.z);
                    glUniform3f(program.halfSize, halfSize.x, halfSize.y, halfSize.z);
                    glUniform1i(program.flat, flat ? 1 : 0);
                    int vao = program.vaos.get(vertexBuffer);
                    if (vao == 0) {
                        vao = glGenVertexArrays();
                        program.vaos.put(vertexBuffer, vao);
                        glBindVertexArray(vao);
                        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
                        var names = format.getElementAttributeNames();
                        var elements = format.getElements();
                        int attributeOffset = 0;
                        for (int i = 0; i < elements.size(); i++) {
                            var element = elements.get(i);
                            attributeOffset = format.getOffset(element);
                            String name = names.get(i);
                            if (name.equals("Position") || name.equals("Color") || name.equals("Normal") || name.startsWith("UV")) name = "iris_" + name;
                            int attribute = glGetAttribLocation(program.id, name);
                            if (attribute >= 0) {
                                int glType = switch (element.type().name()) {
                                    case "FLOAT" -> GL_FLOAT;
                                    case "BYTE" -> GL_BYTE;
                                    case "UBYTE" -> GL_UNSIGNED_BYTE;
                                    case "SHORT" -> GL_SHORT;
                                    case "USHORT" -> GL_UNSIGNED_SHORT;
                                    case "INT" -> GL_INT;
                                    default -> GL_UNSIGNED_INT;
                                };
                                boolean integer = name.equals("iris_UV1") || name.equals("iris_UV2") || name.equals("iris_Entity");
                                if (integer) glVertexAttribIPointer(attribute, element.count(), glType, format.getVertexSize(), attributeOffset);
                                else glVertexAttribPointer(attribute, element.count(), glType, glType == GL_BYTE || glType == GL_UNSIGNED_BYTE, format.getVertexSize(), attributeOffset);
                                glEnableVertexAttribArray(attribute);
                            }
                        }
                        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
                    } else glBindVertexArray(vao);
                    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, instanceBuffer);
                    glDrawElementsInstanced(GL_TRIANGLES, indices, GL_UNSIGNED_INT, 0, count);
                    draws++;
                } finally {
                    if (previous != 0 && length > 0) glBindBufferRange(GL_SHADER_STORAGE_BUFFER, binding, previous, offset, length);
                    else glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, previous);
                    glBindBuffer(GL_SHADER_STORAGE_BUFFER, generic);
                    glUseProgram(shader.getProgramId());
                }
            } catch (Throwable exception) {
                blocked = true;
                generation++;
                LoggerFactory.getLogger("Carbon").error("Iris instancing unavailable for this pipeline; restoring native submissions", exception);
            }
        }
    }

    public void releaseMesh(int buffer) {
        for (Program program : programs.values()) {
            int vao = program.vaos.remove(buffer);
            if (vao != 0) glDeleteVertexArrays(vao);
        }
    }

    public void invalidate() {
        for (Program program : programs.values()) program.close();
        programs.clear();
        layers.clear();
        sourceDelegates.clear();
        blocked = present && !bridgeReady;
        generation++;
    }

    public interface LayerAccess {
        RenderType carbon$wrapped();
    }

    private static final class Program implements AutoCloseable {
        private final int id, binding, camera, clock, scale, center, halfSize, flat;
        private final List<Uniform> uniforms = new ArrayList<>();
        private final Int2IntOpenHashMap vaos = new Int2IntOpenHashMap();

        private Program(int source, int mode) throws Exception {
            int[] attached = new int[glGetProgrami(source, GL_ATTACHED_SHADERS)];
            glGetAttachedShaders(source, new int[1], attached);
            int vertex = 0;
            String text = null;
            for (int shader : attached) {
                int type = glGetShaderi(shader, GL_SHADER_TYPE);
                if (type == GL_VERTEX_SHADER) text = glGetShaderSource(shader);
                if (type == GL_TESS_CONTROL_SHADER || type == GL_TESS_EVALUATION_SHADER) throw new IllegalArgumentException("Tessellated item shader requires native submissions");
            }
            if (text == null) throw new IllegalStateException("Missing Iris vertex source");
            Set<Integer> used = new HashSet<>();
            int blocks = glGetProgramInterfacei(source, GL_SHADER_STORAGE_BLOCK, GL_ACTIVE_RESOURCES);
            for (int i = 0; i < blocks; i++) {
                int[] result = new int[1];glGetProgramResourceiv(source, GL_SHADER_STORAGE_BLOCK, i, new int[]{GL_BUFFER_BINDING}, new int[1], result);used.add(result[0]);
            }
            int selected = glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) - 1;
            while (used.contains(selected)) selected--;
            if (selected < 0) throw new IllegalStateException("No free shader storage binding");
            binding = selected;
            text = text.replaceFirst("#version[^\r\n]*", "#version 430 core");
            for (String name : new String[]{"Position", "Normal", "Color", "UV0", "UV1", "UV2"}) {
                if (Pattern.compile("\\bin\\s+\\w+\\s+" + name + "\\s*;").matcher(text).find()) {
                    text = text.replaceAll("\\b" + name + "\\b", "iris_" + name);
                }
            }
            String[] inputs = {"iris_Position", "iris_Normal", "iris_UV1", "iris_UV2", "at_tangent", "iris_Entity"};
            String[] types = {"vec3", "vec3", "ivec2", "ivec2", "vec4", "ivec3"};
            StringBuilder declarations = new StringBuilder();
            for (int i = 0; i < inputs.length; i++) {
                String name = inputs[i];
                var declaration = Pattern.compile("\\bin\\s+(\\w+)\\s+" + name + "\\s*;").matcher(text);
                if (declaration.find()) types[i] = declaration.group(1);
                else declarations.append("in ").append(types[i]).append(' ').append(name).append(";\n");
                text = text.replaceAll("\\b" + name + "\\b", "carbon_" + name);
                text = text.replaceFirst("\\bin\\s+" + types[i] + "\\s+carbon_" + name + "\\s*;", "in " + types[i] + " " + name + ";");
                declarations.append(types[i]).append(" carbon_").append(name).append(";\n");
            }
            text = text.replaceFirst("void\\s+main\\s*\\([^)]*\\)", "void carbon_original_main()");
            String kernel;
            try (var stream = IrisCompatibility.class.getResourceAsStream("/assets/carbon/shaders/iris_instancing.glsl")) {
                if (stream == null) throw new IllegalStateException("Missing instance shader bridge");
                kernel = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            String header = "\n#define CARBON_MODE " + mode + "\n#define CARBON_UV1_TYPE " + types[2] + "\n#define CARBON_UV2_TYPE " + types[3]
                    + "\nlayout(std430, binding=" + binding + ") readonly buffer CarbonInstances { uint carbon_data[]; };\n" + declarations;
            int versionEnd = text.indexOf('\n');
            text = text.substring(0, versionEnd + 1) + header + text.substring(versionEnd + 1) + "\n" + kernel;
            id = glCreateProgram();
            try (MemoryStack memory = MemoryStack.stackPush()) {
                vertex = glCreateShader(GL_VERTEX_SHADER);glShaderSource(vertex, text);glCompileShader(vertex);
                if (glGetShaderi(vertex, GL_COMPILE_STATUS) == GL_FALSE) throw new IllegalStateException(glGetShaderInfoLog(vertex));
                glAttachShader(id, vertex);
                for (int shader : attached) if (glGetShaderi(shader, GL_SHADER_TYPE) != GL_VERTEX_SHADER) glAttachShader(id, shader);
                IntBuffer size = memory.mallocInt(1), type = memory.mallocInt(1);
                for (int i = 0; i < glGetProgrami(source, GL_ACTIVE_ATTRIBUTES); i++) {
                    String name = glGetActiveAttrib(source, i, 256, size, type);
                    String attribute = name.equals("Position") || name.equals("Normal") || name.equals("Color") || name.startsWith("UV") ? "iris_" + name : name;
                    glBindAttribLocation(id, glGetAttribLocation(source, name), attribute);
                }
                int outputs = glGetProgramInterfacei(source, GL_PROGRAM_OUTPUT, GL_ACTIVE_RESOURCES);
                for (int i = 0; i < outputs; i++) {
                    String name = glGetProgramResourceName(source, GL_PROGRAM_OUTPUT, i);int location = glGetFragDataLocation(source, name);
                    if (location >= 0) glBindFragDataLocation(id, location, name);
                }
                glLinkProgram(id);
                if (glGetProgrami(id, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(id));
                for (int i = 0; i < glGetProgrami(source, GL_ACTIVE_UNIFORM_BLOCKS); i++) {
                    String name = glGetActiveUniformBlockName(source, i);
                    int block = glGetUniformBlockIndex(id, name);
                    if (block != GL_INVALID_INDEX) glUniformBlockBinding(id, block, glGetActiveUniformBlocki(source, i, GL_UNIFORM_BLOCK_BINDING));
                }
                for (int i = 0; i < blocks; i++) {
                    String name = glGetProgramResourceName(source, GL_SHADER_STORAGE_BLOCK, i);
                    int block = glGetProgramResourceIndex(id, GL_SHADER_STORAGE_BLOCK, name);
                    int[] originalBinding = new int[1];
                    glGetProgramResourceiv(source, GL_SHADER_STORAGE_BLOCK, i, new int[]{GL_BUFFER_BINDING}, new int[1], originalBinding);
                    if (block != GL_INVALID_INDEX) glShaderStorageBlockBinding(id, block, originalBinding[0]);
                }
                for (int i = 0; i < glGetProgrami(source, GL_ACTIVE_UNIFORMS); i++) {
                    String name = glGetActiveUniform(source, i, 256, size, type);
                    for (int element = 0; element < size.get(0); element++) {
                        String uniform = size.get(0) > 1 ? name.replace("[0]", "[" + element + "]") : name;
                        int location = glGetUniformLocation(id, uniform);
                        if (location >= 0) uniforms.add(new Uniform(glGetUniformLocation(source, uniform), location, type.get(0)));
                    }
                }
            } catch (Throwable exception) {
                glDeleteProgram(id);throw exception;
            } finally { if (vertex != 0) glDeleteShader(vertex); }
            camera = glGetUniformLocation(id, "carbonCamera");clock = glGetUniformLocation(id, "carbonClock");scale = glGetUniformLocation(id, "carbonScale");
            center = glGetUniformLocation(id, "carbonCenter");halfSize = glGetUniformLocation(id, "carbonHalfSize");flat = glGetUniformLocation(id, "carbonFlat");
        }

        @Override
        public void close() {
            for (int vao : vaos.values()) glDeleteVertexArrays(vao);
            glDeleteProgram(id);
        }
    }

    private record Uniform(int source, int target, int type) {
        private void copy(int program, FloatBuffer floats, IntBuffer integers) {
            floats.clear();integers.clear();
            switch (type) {
                case GL_FLOAT, GL_FLOAT_VEC2, GL_FLOAT_VEC3, GL_FLOAT_VEC4, GL_FLOAT_MAT2, GL_FLOAT_MAT3, GL_FLOAT_MAT4, GL_FLOAT_MAT2x3, GL_FLOAT_MAT2x4, GL_FLOAT_MAT3x2, GL_FLOAT_MAT3x4, GL_FLOAT_MAT4x2, GL_FLOAT_MAT4x3 -> {
                    glGetUniformfv(program, source, floats);
                    switch (type) {
                        case GL_FLOAT -> glUniform1fv(target, floats.limit(1));
                        case GL_FLOAT_VEC2 -> glUniform2fv(target, floats.limit(2));
                        case GL_FLOAT_VEC3 -> glUniform3fv(target, floats.limit(3));
                        case GL_FLOAT_VEC4 -> glUniform4fv(target, floats.limit(4));
                        case GL_FLOAT_MAT2 -> glUniformMatrix2fv(target, false, floats.limit(4));
                        case GL_FLOAT_MAT3 -> glUniformMatrix3fv(target, false, floats.limit(9));
                        case GL_FLOAT_MAT2x3 -> glUniformMatrix2x3fv(target, false, floats.limit(6));
                        case GL_FLOAT_MAT2x4 -> glUniformMatrix2x4fv(target, false, floats.limit(8));
                        case GL_FLOAT_MAT3x2 -> glUniformMatrix3x2fv(target, false, floats.limit(6));
                        case GL_FLOAT_MAT3x4 -> glUniformMatrix3x4fv(target, false, floats.limit(12));
                        case GL_FLOAT_MAT4x2 -> glUniformMatrix4x2fv(target, false, floats.limit(8));
                        case GL_FLOAT_MAT4x3 -> glUniformMatrix4x3fv(target, false, floats.limit(12));
                        default -> glUniformMatrix4fv(target, false, floats.limit(16));
                    }
                }
                case GL_UNSIGNED_INT, GL_UNSIGNED_INT_VEC2, GL_UNSIGNED_INT_VEC3, GL_UNSIGNED_INT_VEC4 -> {
                    glGetUniformuiv(program, source, integers);
                    switch(type) {
                        case GL_UNSIGNED_INT_VEC2 -> glUniform2uiv(target, integers.limit(2));
                        case GL_UNSIGNED_INT_VEC3 -> glUniform3uiv(target, integers.limit(3));
                        case GL_UNSIGNED_INT_VEC4 -> glUniform4uiv(target, integers.limit(4));
                        default -> glUniform1uiv(target, integers.limit(1));
                    }
                }
                default -> {
                    glGetUniformiv(program, source, integers);
                    switch(type) {
                        case GL_INT_VEC2, GL_BOOL_VEC2 -> glUniform2iv(target, integers.limit(2));
                        case GL_INT_VEC3, GL_BOOL_VEC3 -> glUniform3iv(target, integers.limit(3));
                        case GL_INT_VEC4, GL_BOOL_VEC4 -> glUniform4iv(target, integers.limit(4));
                        default -> glUniform1iv(target, integers.limit(1));
                    }
                }
            }
        }
    }
}
