package first.carbon.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutlineShaderTest {
    @ParameterizedTest
    @CsvSource({
            "110, DiffuseSampler, texture2D, gl_FragColor",
            "150, DiffuseSampler, texture, fragColor",
            "330, InSampler, texture, fragColor"
    })
    void insertsTransparentFootprintTestsWithoutChangingEdgeMath(
            int version, String sampler, String lookup, String result) {
        String original = example(version, sampler, lookup, result);
        String optimized = OutlineShader.optimize(original);
        String zero = result + " = vec4(0.0);";
        int center = optimized.indexOf("vec4 center =");
        int firstNeighbor = optimized.indexOf("vec4 left =");
        int lastNeighbor = optimized.indexOf("vec4 down =");
        int originalOutput = optimized.indexOf(result + " = vec4(center.rgb");

        assertTrue(optimized.startsWith("#version " + version + "\n#if __VERSION__ >= 150"));
        assertTrue(optimized.contains("defined(GL_ARB_texture_gather) && defined(GL_ARB_gpu_shader5) && !defined(VULKAN)"));
        String gather = optimized.substring(center, firstNeighbor);
        assertTrue(gather.contains("if (center.a == 0.0)"));
        assertTrue(gather.contains("textureGatherOffsets(" + sampler + ", corner,"));
        assertTrue(gather.contains("ivec2(-1, 0), ivec2(1, 0), ivec2(0, -1), ivec2(0, 1)), 3)"));
        assertTrue(gather.contains("if (dot(neighbors, vec4(1.0)) == 0.0)"));
        assertTrue(gather.contains(zero + "\n            return;"));

        String fallback = optimized.substring(lastNeighbor, originalOutput);
        assertTrue(fallback.contains("#ifndef CARBON_GATHER_OUTLINE"));
        assertTrue(fallback.contains("center.a + left.a + right.a + up.a + down.a == 0.0"));
        assertTrue(fallback.contains(zero + "\n        return;"));

        String retained = optimized.replaceFirst("(?s)#if __VERSION__.*?#endif\\n", "")
                .replaceFirst("(?s)\\n#ifdef CARBON_GATHER_OUTLINE.*?#endif\\n", "")
                .replaceFirst("(?s)\\n#ifndef CARBON_GATHER_OUTLINE.*?#endif\\n", "");
        assertEquals(original, retained, "The original pass must still handle nontransparent pixels");
    }

    @Test
    void leavesUnrecognizedShaderUnchanged() {
        String shader = example(330, "InSampler", "texture", "fragColor")
                .replace("vec4 down =", "vec4 customBottom =");
        assertSame(shader, OutlineShader.optimize(shader));
    }

    @Test
    void leavesAnAlreadyOptimizedShaderUnchanged() {
        String shader = OutlineShader.optimize(example(150, "DiffuseSampler", "texture", "fragColor"));
        assertSame(shader, OutlineShader.optimize(shader));
    }

    @Test
    void streamPathMatchesTheTextPath() throws IOException {
        String shader = example(110, "DiffuseSampler", "texture2D", "gl_FragColor") + "// 轮廓\n";
        var input = new ByteArrayInputStream(shader.getBytes(StandardCharsets.UTF_8));
        try (var output = OutlineShader.optimize(input)) {
            assertEquals(OutlineShader.optimize(shader), new String(output.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void recognizesOnlyBuiltinResourcePacks() {
        assertTrue(OutlineShader.isBuiltin("vanilla"));
        assertTrue(OutlineShader.isBuiltin("Default"));
        assertFalse(OutlineShader.isBuiltin("file/custom-outlines.zip"));
        assertFalse(OutlineShader.isBuiltin(null));
    }

    private static String example(int version, String sampler, String lookup, String result) {
        return """
                #version %d
                uniform sampler2D %s;
                void main() {
                    vec4 center = %s(%s, texCoord);
                    vec4 left = %s(%s, texCoord - vec2(oneTexel.x, 0.0));
                    vec4 right = %s(%s, texCoord + vec2(oneTexel.x, 0.0));
                    vec4 up = %s(%s, texCoord - vec2(0.0, oneTexel.y));
                    vec4 down = %s(%s, texCoord + vec2(0.0, oneTexel.y));
                    %s = vec4(center.rgb, max(abs(left.a - right.a), abs(up.a - down.a)));
                }
                """.formatted(version, sampler, lookup, sampler, lookup, sampler, lookup, sampler,
                lookup, sampler, lookup, sampler, result);
    }
}
