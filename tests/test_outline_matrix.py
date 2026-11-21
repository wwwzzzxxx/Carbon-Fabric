import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SELECTORS = {
    "1.17.1": {"entityoutliner"},
    "1.20.1": {"reentityoutliner"},
    "1.20.2": {"reentityoutliner"},
    "1.20.4": {"entityoutliner"},
    **dict.fromkeys(
        ("1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8"),
        {"lpctools", "reentityoutliner"},
    ),
}
RE_LOOKUP_VERSIONS = {
    "1.20.1": "23.1.2.3",
    "1.21.8": "25.2.0.2",
    "1.21.10": "25.3.0.2",
    "1.21.11": "25.4.0.2",
    "26.1.2": "26.1.2.4",
    "26.2": "26.2.1.0",
    "26.3": "26.3.0.0",
}
OUTLINE_BUFFERS = {"1.21.10", "1.21.11", "26.1.2"}
SHARED_OUTLINES = OUTLINE_BUFFERS | {"26.2", "26.3"}
ORDERED_OUTLINE_BUFFERS = {
    "1.18.2", "1.19.4", "1.20.1", "1.20.2", "1.20.4", "1.20.6",
    "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8",
}
BUFFERED_DROPS = {
    "1.16.5", "1.18.2", "1.19.4", "1.20.1", "1.20.2", "1.20.4", "1.20.6", "1.21.1",
    "1.21.4", "1.21.5", "1.21.8",
}
BUFFERED_RENDER_STATES = {"1.21.4", "1.21.5", "1.21.8"}
BUFFERED_OUTLINE_MIXINS = {"BufferedOutlineBuilderMixin", "BufferedOutlineGeneratorMixin"}
LEGACY_FULL_VERSIONS = {"1.19.4", "1.20.2", "1.20.4", "1.20.6"}
LEGACY_FULL_MIXINS = {"LegacyItemPhysicFireMixin", "LegacyItemPhysicRenderScopeMixin"}


class OutlineMatrixTest(unittest.TestCase):
    def test_direct_builds_register_expected_outline_hooks(self):
        for version, resources, mixin, source in (
            (
                "1.16.5",
                "src/legacy/resources",
                "carbon.legacy.mixins.json",
                "src/legacy/java/first/carbon/mixin/OutlineShaderMixin.java",
            ),
            (
                "1.18.2",
                "src/main/resources",
                "carbon.mixins.json",
                "src/outline/legacy/OutlineShaderMixin.java",
            ),
        ):
            with self.subTest(version=version):
                self.assertTrue((ROOT / source).is_file())
                self.check_metadata(ROOT / resources, mixin, version)

    def test_version_overlays_generate_the_expected_outline_hooks(self):
        targets = self.read_json(ROOT / "versions/matrix.json")["targets"]
        for target in targets:
            version = target["minecraft"]
            if version in {"1.16.5", "1.18.2"}:
                continue
            with self.subTest(version=version):
                project = ROOT / "versions" / version
                build = project / "build"
                build.mkdir(exist_ok=True)
                with tempfile.TemporaryDirectory(prefix="outline-check-", dir=build) as temp:
                    generated = Path(temp) / "generated"
                    process = subprocess.run(
                        [sys.executable, "-X", "utf8", str(ROOT / "tools/prepare_version.py"), str(project), str(generated)],
                        capture_output=True,
                        encoding="utf-8",
                    )
                    self.assertEqual(0, process.returncode, process.stderr)
                    self.check_generated(generated, version)

    def check_generated(self, generated, version):
        resources = generated / "resources"
        code = generated / "java/first/carbon"
        self.check_metadata(resources, "carbon.mixins.json", version)
        for mixin in LEGACY_FULL_MIXINS:
            self.assertEqual(version in LEGACY_FULL_VERSIONS, (code / f"mixin/{mixin}.java").is_file())
        self.assertTrue((code / "mixin/OutlineShaderMixin.java").is_file())
        mask = resources / "assets/carbon/shaders/dropped_outline.fsh"
        self.assertEqual(version == "1.21.8" or version in SHARED_OUTLINES, mask.is_file())
        ordered_buffer = code / "client/OrderedOutlineBufferSource.java"
        mask_bridge = code / "mixin/BufferedOutlineSourceMixin.java"
        self.assertEqual(version in ORDERED_OUTLINE_BUFFERS, ordered_buffer.is_file())
        self.assertEqual(version in ORDERED_OUTLINE_BUFFERS, mask_bridge.is_file())
        if mask_bridge.is_file():
            bridge = mask_bridge.read_text(encoding="utf-8")
            self.assertIn('isModLoaded("immediatelyfast")', bridge)
            self.assertIn("new OrderedOutlineBufferSource(buffer)", bridge)
            builder = "ByteBufferBuilder" if tuple(map(int, version.split("."))) >= (1, 21, 1) else "BufferBuilder"
            self.assertIn(f"blaze3d/vertex/{builder};", bridge)
            self.assertIn(f"{builder} buffer", ordered_buffer.read_text(encoding="utf-8"))
        buffered_outlines = version not in SHARED_OUTLINES
        self.assertEqual(buffered_outlines, (code / "client/BufferedItemOutlines.java").is_file())
        for mixin in BUFFERED_OUTLINE_MIXINS:
            self.assertEqual(buffered_outlines, (code / f"mixin/{mixin}.java").is_file())
        for ext in ("vsh", "fsh"):
            self.assertEqual(buffered_outlines, (resources / f"assets/carbon/shaders/buffered_outline.{ext}").is_file())
        if buffered_outlines:
            renderer = (code / "client/ItemBatchRenderer.java").read_text(encoding="utf-8")
            self.assertIn("outlines.append(cached.batch, a.data", renderer)
            self.assertIn("outlines.flush(owner, type)", renderer)
        if version in {"1.20.1", "1.20.2", "1.20.4", "1.20.6"}:
            sodium = (code / "mixin/SodiumBufferBuilderMixin.java").read_text(encoding="utf-8")
            self.assertIn("BufferedItemOutlines.OutlineBuffer", sodium)
            self.assertIn("getOriginalBufferBuilder()", sodium)
        if version in {"1.21.5", "1.21.8"}:
            submission = (code / "mixin/ItemModelSubmissionMixin.java").read_text(encoding="utf-8")
            self.assertIn('@WrapOperation(method = "renderItem"', submission)
            self.assertIn("render.call(", submission)
        if version == "1.21.8":
            level = (code / "mixin/LevelRendererMixin.java").read_text(encoding="utf-8")
            self.assertIn("renderer.prepareOutlines(remaining)", level)
            self.assertIn("renderer.drawOutlines()", level)
            self.assertIn("beginEntityPass()", level)
            self.assertIn("endEntityPass()", level)
        outline_buffer = code / "mixin/OutlineBufferSourceMixin.java"
        self.assertEqual(version in OUTLINE_BUFFERS, outline_buffer.is_file())
        retained_pose = code / "client/RetainedExternalDropPose.java"
        self.assertEqual(version in SHARED_OUTLINES, retained_pose.is_file())
        if retained_pose.is_file():
            submission = (code / "mixin/RetainedItemSubmissionMixin.java").read_text(encoding="utf-8")
            self.assertIn("submitRetained(item, pose, collector)", submission)
        buffered_dispatch = code / "mixin/BufferedDropDispatcherMixin.java"
        self.assertEqual(version in BUFFERED_DROPS, buffered_dispatch.is_file())
        if buffered_dispatch.is_file():
            dispatch = buffered_dispatch.read_text(encoding="utf-8")
            self.assertEqual(version in BUFFERED_RENDER_STATES, "state instanceof ItemEntityRenderState" in dispatch)
            self.assertIn("bufferedDropPoses()", dispatch)
            self.assertIn("draw.addRetained(entry.data, frame)", (code / "client/ItemBatchRenderer.java").read_text(encoding="utf-8"))

        provider = code / "compat/EntityOutlineCompatibility.java"
        self.assertEqual(version in SELECTORS, provider.is_file())
        if provider.is_file():
            selectors = re.findall(r"\bSUPPORTS_(\w+) = true;", provider.read_text(encoding="utf-8"))
            self.assertEqual(SELECTORS[version], {selector.lower() for selector in selectors})

        lookup = code / "compat/ReEntityOutlinerItemLookup.java"
        hook = code / "mixin/ReEntityOutlinerItemLookupMixin.java"
        expected = RE_LOOKUP_VERSIONS.get(version)
        self.assertEqual(expected is not None, lookup.is_file())
        self.assertEqual(expected is not None, hook.is_file())
        if expected:
            self.assertIn(f'"{expected}".equals(', lookup.read_text(encoding="utf-8"))
            mixin = hook.read_text(encoding="utf-8")
            self.assertIn("@Pseudo", mixin)
            self.assertIn("require = 0", mixin)
            self.assertEqual(version.startswith("1."), "Lnet/minecraft/class_1299;" in mixin)

    def check_metadata(self, resources, config, version):
        manifest = self.read_json(resources / "fabric.mod.json")
        mixins = self.read_json(resources / config)["client"]
        self.assertEqual(version, manifest["depends"]["minecraft"])
        self.assertIn(config, manifest["mixins"])
        compat_file = "carbon.external-drops.mixins.json"
        self.assertEqual(version in SHARED_OUTLINES, compat_file in manifest["mixins"])
        self.assertEqual(version in SHARED_OUTLINES, (resources / compat_file).is_file())
        if version in SHARED_OUTLINES:
            compatibility = self.read_json(resources / compat_file)
            self.assertEqual("first.carbon.mixin.ExternalDropMixinPlugin", compatibility["plugin"])
            self.assertEqual({"ExternalItemDispatcherMixin", "ExternalItemPoseMixin"}, set(compatibility["client"]))
        legacy_full = "carbon.itemphysic-legacy.mixins.json"
        self.assertEqual(version in LEGACY_FULL_VERSIONS, legacy_full in manifest["mixins"])
        self.assertEqual(version in LEGACY_FULL_VERSIONS, (resources / legacy_full).is_file())
        if version in LEGACY_FULL_VERSIONS:
            settings = self.read_json(resources / legacy_full)
            self.assertEqual("first.carbon.mixin.LegacyItemPhysicMixinPlugin", settings["plugin"])
            self.assertEqual("JAVA_" + manifest["depends"]["java"].removeprefix(">="), settings["compatibilityLevel"])
            self.assertEqual(LEGACY_FULL_MIXINS, set(settings["client"]))
        buffered = "carbon.buffered-drops.mixins.json"
        self.assertEqual(version in BUFFERED_DROPS, buffered in manifest["mixins"])
        self.assertEqual(version in BUFFERED_DROPS, (resources / buffered).is_file())
        if version in BUFFERED_DROPS:
            config = self.read_json(resources / buffered)
            self.assertEqual(config["compatibilityLevel"], "JAVA_" + manifest["depends"]["java"].removeprefix(">="))
            self.assertEqual(config["plugin"], "first.carbon.mixin.BufferedDropMixinPlugin")
            expected = {"BufferedDropDispatcherMixin"}
            if version not in BUFFERED_RENDER_STATES:
                expected.add("BufferedDropEntityAccessor")
            self.assertEqual(expected, set(config["client"]))
        self.assertTrue({"itemdrops", "itemphysic", "itemphysiclite"}.isdisjoint(manifest["depends"]))
        self.assertEqual(1, mixins.count("OutlineShaderMixin"))
        self.assertEqual(int(version in RE_LOOKUP_VERSIONS), mixins.count("ReEntityOutlinerItemLookupMixin"))
        self.assertEqual(int(version in OUTLINE_BUFFERS), mixins.count("OutlineBufferSourceMixin"))
        self.assertEqual(int(version in ORDERED_OUTLINE_BUFFERS), mixins.count("BufferedOutlineSourceMixin"))
        for mixin in BUFFERED_OUTLINE_MIXINS:
            self.assertEqual(int(version not in SHARED_OUTLINES), mixins.count(mixin))
        self.assertTrue({"lpctools", "reentityoutliner", "entityoutliner"}.isdisjoint(manifest["depends"]))

    @staticmethod
    def read_json(path):
        return json.loads(path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
