package first.carbon.client;

import first.carbon.Carbon;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class OutlineShader {
    private static final String GATHER =
            "#if __VERSION__ >= 150 && defined(GL_ARB_texture_gather) && defined(GL_ARB_gpu_shader5) && !defined(VULKAN)\n"
            + "#extension GL_ARB_texture_gather : enable\n"
            + "#extension GL_ARB_gpu_shader5 : enable\n"
            + "#define CARBON_GATHER_OUTLINE\n"
            + "#endif\n";

    private OutlineShader() {
    }

    public static InputStream optimize(InputStream input) throws IOException {
        String shader = IOUtils.toString(input, StandardCharsets.UTF_8);
        return new ByteArrayInputStream(optimize(shader).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean enabled() {
        return Carbon.CONFIG.enabled.get() && !Boolean.getBoolean("carbon.test.disableOutlineSobel");
    }

    public static boolean isBuiltin(String pack) {
        return "vanilla".equals(pack) || "Default".equals(pack);
    }

    public static String optimize(String source) {
        String sampler = source.contains("uniform sampler2D InSampler;") ? "InSampler" : "DiffuseSampler";
        boolean legacy = source.startsWith("#version 110");
        String lookup = legacy ? "texture2D" : "texture";
        String centerSample = "vec4 center = " + lookup + "(" + sampler + ", texCoord);";
        String lastSample = "vec4 down = " + lookup + "(" + sampler + ", texCoord + vec2(0.0, oneTexel.y));";
        int endVersion = source.indexOf('\n');
        int center = source.indexOf(centerSample);
        int last = source.indexOf(lastSample);
        if (endVersion < 0 || center < 0 || last <= center || source.contains("CARBON_GATHER_OUTLINE")) return source;
        center += centerSample.length();
        last += lastSample.length();
        String result = legacy ? "gl_FragColor" : "fragColor";

        // Fast-reject a transparent five-texel footprint. Evaluate edges with the original math.
        String probe = "\n#ifdef CARBON_GATHER_OUTLINE\n"
                + "    if (center.a == 0.0) {\n"
                + "        vec2 corner = texCoord + 0.5 * oneTexel;\n"
                + "        vec4 neighbors = textureGatherOffsets(" + sampler + ", corner, "
                + "ivec2[4](ivec2(-1, 0), ivec2(1, 0), ivec2(0, -1), ivec2(0, 1)), 3);\n"
                + "        if (dot(neighbors, vec4(1.0)) == 0.0) {\n"
                + "            " + result + " = vec4(0.0);\n"
                + "            return;\n"
                + "        }\n"
                + "    }\n"
                + "#endif\n";
        String fallback = "\n#ifndef CARBON_GATHER_OUTLINE\n"
                + "    if (center.a + left.a + right.a + up.a + down.a == 0.0) {\n"
                + "        " + result + " = vec4(0.0);\n"
                + "        return;\n"
                + "    }\n"
                + "#endif\n";
        return source.substring(0, endVersion + 1) + GATHER + source.substring(endVersion + 1, center)
                + probe + source.substring(center, last) + fallback + source.substring(last);
    }
}
