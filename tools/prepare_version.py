"""Generate a version overlay without modifying the verified baseline sources."""
from pathlib import Path
import json,shutil,sys
project=Path(sys.argv[1]).resolve();destination=Path(sys.argv[2]).resolve();root=project.parent.parent
assert root==Path(__file__).resolve().parent.parent and project.parent==root/'versions'
assert destination.is_relative_to(project/'build')
if destination.exists():shutil.rmtree(destination)
shutil.copytree(root/'src/main',destination,ignore=shutil.ignore_patterns('buffered'))
# The root build directly registers the original Entity Outliner for 1.18.2.
# Overlays opt into only the providers supported by their own version.
base_renderer = destination/'java/first/carbon/client/DroppedItemBatchRenderer.java'
base_renderer.write_text(base_renderer.read_text(encoding='utf8')
                         .replace('\n                || (enabled && first.carbon.compat.EntityOutlineCompatibility.selectsDroppedItems())', ''),
                         encoding='utf8')
shutil.copytree(root/'src/outline/java', destination/'java', dirs_exist_ok=True)
version = tuple(map(int, project.name.split('.')))
if version >= (1,21,5):
    shutil.copy2(root/'src/main/buffered/DroppedItemUniforms.java', destination/'java/first/carbon/client')
outline_era = 'modern' if version >= (1,21,3) else 'legacy'
outline_hook = (root/f'src/outline/{outline_era}/OutlineShaderMixin.java').read_text(encoding='utf-8')
if version >= (1,21,11):
    outline_hook = outline_hook.replace('ResourceLocation', 'Identifier')
(destination/'java/first/carbon/mixin/OutlineShaderMixin.java').write_text(outline_hook, encoding='utf-8')
buffered_entities = project.name in {'1.21.5', '1.21.8'}
if buffered_entities:
    shutil.copytree(root/'src/bufferedentities/java', destination/'java', dirs_exist_ok=True)
shared_dropped_outlines = project.name in {'1.21.10', '1.21.11', '26.1.2', '26.2'}
if shared_dropped_outlines:
    shutil.copytree(root/'src/outline/dropped', destination/'java', dirs_exist_ok=True)
    if project.name == '26.2':
        (destination/'java/first/carbon/mixin/OutlineBufferSourceMixin.java').unlink()
    if project.name == '1.21.10':
        file = destination/'java/first/carbon/client/DroppedItemOutline.java'
        file.write_text(file.read_text(encoding='utf8')
                        .replace('renderer.rendertype.RenderType', 'renderer.RenderType')
                        .replace('resources.Identifier', 'resources.ResourceLocation')
                        .replace('Identifier.withDefaultNamespace', 'ResourceLocation.withDefaultNamespace'),
                        encoding='utf8')
if project.name == '1.21.8':
    shared_outline = root/'src/outline/dropped/first/carbon/client/DroppedItemOutline.java'
    outline = (shared_outline.read_text(encoding='utf8')
               .replace('renderer.rendertype.RenderType', 'renderer.RenderType')
               .replace('net.minecraft.client.renderer.entity.state.EntityRenderState', 'net.minecraft.world.entity.Entity')
               .replace('import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;',
                        'import net.minecraft.world.entity.item.ItemEntity;')
               .replace('resources.Identifier', 'resources.ResourceLocation')
               .replace('Identifier.withDefaultNamespace', 'ResourceLocation.withDefaultNamespace')
               .replace('EntityRenderState', 'Entity')
               .replace('ItemEntityRenderState', 'ItemEntity')
               .replace('state.appearsGlowing()', 'Minecraft.getInstance().shouldEntityAppearGlowing(state)')
               .replace('state.outlineColor', '(state.getTeamColor() | 0xFF000000)')
               .replace('var pipeline = type.pipeline();',
                        'if (!(type instanceof RenderType.CompositeRenderType composite)) return null;\n'
                        '        var pipeline = composite.renderPipeline;')
               .replace('                        glUniformMatrix4fv(glGetUniformLocation(mask, "Projection"), false, projection.get(matrix));\n', ''))
    (destination/'java/first/carbon/client/DroppedItemOutline.java').write_text(outline, encoding='utf8')
if shared_dropped_outlines or project.name in {'1.21.8', '26.3'}:
    shutil.copytree(root/'src/outline/resources', destination/'resources', dirs_exist_ok=True)
retained_items = project.name in {'1.21.10', '1.21.11', '26.1.2', '26.2', '26.3'}
if retained_items:
    shutil.copytree(root/'src/renderstate/java', destination/'java', dirs_exist_ok=True)
    shutil.copytree(root/'src/renderstate/resources', destination/'resources', dirs_exist_ok=True)
    section_visibility = destination/'java/first/carbon/mixin/ItemSectionVisibilityMixin.java'
    visibility_source = section_visibility.read_text(encoding='utf8')
    if project.name == '1.21.10':
        visibility_source = visibility_source.replace('isSectionCompiledAndVisible', 'isSectionCompiled')
    if project.name in {'26.2', '26.3'}:
        visibility_source = (visibility_source.replace('@Mixin(LevelRenderer.class)', '@Mixin(LevelExtractor.class)')
                             .replace('import net.minecraft.core.BlockPos;', 'import net.minecraft.client.renderer.extract.LevelExtractor;\nimport net.minecraft.core.BlockPos;')
                             .replace('method = "extractVisibleEntities"', 'method = "isEntityVisible"'))
    if project.name == '26.3':
        visibility_source = (visibility_source.replace('BlockPos;)Z', 'BlockPos;J)Z')
                             .replace('BlockPos pos, Operation', 'BlockPos pos, long fadeDuration, Operation')
                             .replace('original.call(renderer, pos)', 'original.call(renderer, pos, fadeDuration)'))
        item_visibility = destination/'java/first/carbon/mixin/ItemVisibilityMixin.java'
        item_visibility.write_text(item_visibility.read_text(encoding='utf8')
                                   .replace('DDD)Z', 'DDDF)Z')
                                   .replace('double z, Operation', 'double z, float partial, Operation')
                                   .replace('z, 0.0F, original', 'z, partial, original'), encoding='utf8')
        state_cache = destination/'java/first/carbon/client/StaticItemRenderStates.java'
        state_cache.write_text(state_cache.read_text(encoding='utf8')
                               .replace('original.call(renderer, entity, frustum, x, y, z)',
                                        'original.call(renderer, entity, frustum, x, y, z, partial)'), encoding='utf8')
    section_visibility.write_text(visibility_source, encoding='utf8')
    if project.name.startswith('26.'):
        packet = destination/'java/first/carbon/mixin/ItemDrawPacketMixin.java'
        packet.write_text(packet.read_text(encoding='utf8').replace('state.LevelRenderState', 'state.level.LevelRenderState'), encoding='utf8')
        state_cache = destination/'java/first/carbon/client/StaticItemRenderStates.java'
        state_cache.write_text(state_cache.read_text(encoding='utf8').replace('BlockModelWrapper', 'CuboidItemModelWrapper'), encoding='utf8')
        external_dispatch = destination/'java/first/carbon/mixin/ExternalItemDispatcherMixin.java'
        external_dispatch.write_text(external_dispatch.read_text(encoding='utf8')
                                     .replace('state.CameraRenderState', 'state.level.CameraRenderState')
                                     .replace('state/CameraRenderState', 'state/level/CameraRenderState'), encoding='utf8')
        submission = destination/'java/first/carbon/mixin/RetainedItemSubmissionMixin.java'
        submission.write_text(submission.read_text(encoding='utf8').replace('state.CameraRenderState', 'state.level.CameraRenderState'), encoding='utf8')
        limit = destination/'java/first/carbon/mixin/ItemLimitExtractionMixin.java'
        limit.write_text(limit.read_text(encoding='utf8').replace('state.LevelRenderState', 'state.level.LevelRenderState')
                         .replace('state/LevelRenderState', 'state/level/LevelRenderState'), encoding='utf8')
    if project.name in {'26.2', '26.3'}:
        extraction = destination/'java/first/carbon/mixin/LevelExtractorItemStatesMixin.java'
        shutil.copy2(root/'src/renderstate/extractor/LevelExtractorItemStatesMixin.java', extraction)
        if project.name == '26.3':
            extraction.write_text(extraction.read_text(encoding='utf8').replace(
                'levelRenderer::isSectionCompiledAndVisible',
                'position -> levelRenderer.isSectionCompiledAndVisible(position, net.minecraft.util.Util.toMillis('
                'net.minecraft.client.Minecraft.getInstance().options.chunkSectionFadeInTime().get()))'
            ), encoding='utf8')
        limit = destination/'java/first/carbon/mixin/ItemLimitExtractionMixin.java'
        source = (limit.read_text(encoding='utf8')
                  .replace('renderer.LevelRenderer', 'renderer.extract.LevelExtractor')
                  .replace('renderer/LevelRenderer', 'renderer/extract/LevelExtractor')
                  .replace('LevelRenderer', 'LevelExtractor')
                  .replace('method = "renderLevel"', 'method = "extract"'))
        limit.write_text(source, encoding='utf8')
# Later render-state versions already consult Minecraft's live outline decision.
outline_providers = {
    '1.17.1': ('entityoutliner',),
    '1.20.1': ('reentityoutliner',),
    '1.20.2': ('reentityoutliner',),
    '1.20.4': ('entityoutliner',),
    '1.21.1': ('lpctools', 'reentityoutliner'),
    '1.21.3': ('lpctools', 'reentityoutliner'),
    '1.21.4': ('lpctools', 'reentityoutliner'),
    '1.21.5': ('lpctools', 'reentityoutliner'),
    '1.21.8': ('lpctools', 'reentityoutliner'),
}
providers = outline_providers.get(project.name)
if providers:
    java = destination/'java/first/carbon'
    probe = (root/'src/outline/compat/EntityOutlineCompatibility.java').read_text(encoding='utf-8')
    probe = probe.replace('SUPPORTS_ENTITYOUTLINER = true;', 'SUPPORTS_ENTITYOUTLINER = false;')
    for mod in providers:
        flag = 'SUPPORTS_'+mod.upper()
        assert probe.count(flag+' = false;') == 1, flag
        probe = probe.replace(flag+' = false;', flag+' = true;')
    (java/'compat/EntityOutlineCompatibility.java').write_text(probe, encoding='utf-8')

for file in (project/'overrides').rglob('*'):
    if file.is_file():
        target=destination/file.relative_to(project/'overrides');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(file,target)
patches=json.loads((project/'patches.json').read_text(encoding='utf8')) if (project/'patches.json').exists() else []
for patch in patches:
    path=destination/patch['file'];text=path.read_text(encoding='utf8');actual=text.count(patch['old'])
    if actual!=patch['count']:raise SystemExit(f"Patch mismatch {path}: expected {patch['count']}, got {actual}")
    path.write_text(text.replace(patch['old'],patch['new']),encoding='utf8')

if providers:
    renderer = java/'client/DroppedItemBatchRenderer.java'
    source = renderer.read_text(encoding='utf-8')
    selection = 'enabled && first.carbon.compat.EntityOutlineCompatibility.selectsDroppedItems()'
    if buffered_entities:
        field = '    private boolean enabled;'
        physics = '        boolean physics = enabled && Carbon.CONFIG.physicalDroppedItems.get();'
        reuse = 'return active && level != null && !nativeDispatch'
        collect = 'if (!active) return entities;'
        assert all(source.count(key) == 1 for key in (field, physics, reuse, collect)), renderer
        source = source.replace(field, field+'\n    private boolean outlineSelection;')
        source = source.replace(physics, physics+f'''
        boolean outlineSelection = {selection};
        if (this.outlineSelection != outlineSelection) {{
            directItems.clear();
            sceneDirty = true;
            this.outlineSelection = outlineSelection;
        }}''')
        source = source.replace(reuse, reuse+' && !outlineSelection')
        source = source.replace(collect, 'if (!active || outlineSelection) return entities;')
    else:
        native = '|| entityCulling.requiresNativeVisibility(minecraft);'
        assert source.count(native) == 1, renderer
        source = source.replace(native, f'|| entityCulling.requiresNativeVisibility(minecraft)\n                || ({selection});')
    renderer.write_text(source, encoding='utf-8')

# These Re:Entity Outliner releases use string keys and recompute the item key in each render hook.
re_lookup_versions = {
    '1.20.1': '23.1.2.3',
    '1.21.8': '25.2.0.2',
    '1.21.10': '25.3.0.2',
    '1.21.11': '25.4.0.2',
    '26.1.2': '26.1.2.4',
    '26.2': '26.2.1.0',
    '26.3': '26.3.0.0',
}
re_lookup_version = re_lookup_versions.get(project.name)
if re_lookup_version:
    shutil.copytree(root/'src/outline/re', destination/'java', dirs_exist_ok=True)
    file = destination/'java/first/carbon/compat/ReEntityOutlinerItemLookup.java'
    file.write_text(file.read_text(encoding='utf8').replace('"26.3.0.0"', f'"{re_lookup_version}"'), encoding='utf8')
    if version < (26,2):
        file = destination/'java/first/carbon/mixin/ReEntityOutlinerItemLookupMixin.java'
        source = file.read_text(encoding='utf8').replace('import net.minecraft.world.entity.EntityTypes;\n', '')
        source = source.replace('EntityTypes.ITEM', 'EntityType.ITEM')
        if version < (26,):
            signature = 'getOrCreateEntityProperties(Lnet/minecraft/world/entity/EntityType;)Lnet/reentityoutliner/util/EntityTypesProperties;'
            intermediary = signature.replace('net/minecraft/world/entity/EntityType', 'net/minecraft/class_1299')
            assert source.count(f'"{signature}"') == 1
            source = source.replace(f'"{signature}"', f'{{"{intermediary}", "{signature}"}}')
        file.write_text(source, encoding='utf8')

metadata=destination/'resources/fabric.mod.json';data=json.loads(metadata.read_text(encoding='utf8'));data['depends']['minecraft']=project.name
matrix=json.loads((root/'versions/matrix.json').read_text(encoding='utf8'))
java=next(target['java'] for target in matrix['targets'] if target['minecraft']==project.name)
data['depends']['java']='>='+str(java)
if project.name in {'1.19.4', '1.20.2', '1.20.4', '1.20.6'}:
    itemphysic = root/'src/itemphysiclegacy'
    shutil.copytree(itemphysic/'java', destination/'java', dirs_exist_ok=True)
    config = 'carbon.itemphysic-legacy.mixins.json'
    mixins = json.loads((itemphysic/'resources'/config).read_text(encoding='utf8'))
    mixins['compatibilityLevel'] = 'JAVA_'+str(java)
    (destination/'resources'/config).write_text(json.dumps(mixins, indent=2), encoding='utf8')
    data['mixins'].append(config)
buffered_drops = destination/'resources/carbon.buffered-drops.mixins.json'
drop_code = destination/'java/first/carbon'
drop_dispatch = drop_code/'mixin/BufferedDropDispatcherMixin.java'
stuck_accessor = drop_code/'mixin/BufferedDropEntityAccessor.java'
pose_cache = drop_code/'client/BufferedDropPoses.java'
if version >= (1,21,10) or project.name in {'1.17.1', '1.21.3'}:
    data['mixins'].remove(buffered_drops.name)
    buffered_drops.unlink()
    for file in (drop_dispatch, stuck_accessor, drop_code/'mixin/BufferedDropMixinPlugin.java'):
        file.unlink()
    if version >= (1,21,10):
        pose_cache.unlink()
        (drop_code/'compat/BufferedDropProvider.java').unlink()
else:
    drop_mixins = json.loads(buffered_drops.read_text(encoding='utf8'))
    drop_mixins['compatibilityLevel'] = 'JAVA_'+str(java)
    if version >= (1,21,4):
        source = (root/'src/main/buffered/BufferedDropDispatcherMixin.java').read_text(encoding='utf8')
        if version >= (1,21,5):
            source = (source.replace('render(Lnet/minecraft/world/entity/Entity;DDDF',
                                     'render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;DDD')
                      .replace('plain.model == layer.model',
                                     'plain.extents == layer.extents && plain.quads.equals(layer.quads)')
                      .replace('layer.model, layer.renderType, null', 'plain.quads, layer.renderType, layer.transform'))
        drop_dispatch.write_text(source, encoding='utf8')
        shutil.copy2(root/'src/main/buffered/BufferedDropState.java', drop_code/'compat')
        drop_mixins['client'].remove('BufferedDropEntityAccessor')
        stuck_accessor.unlink()
    buffered_drops.write_text(json.dumps(drop_mixins, indent=2), encoding='utf8')
if (1,19,4) <= version < (1,21,10):
    source = (pose_cache.read_text(encoding='utf8')
              .replace('base.pose().copy()', 'new org.joml.Matrix4f(base.pose())')
              .replace('base.normal().copy()', 'new org.joml.Matrix3f(base.normal())'))
    if version >= (1,20,1):
        source = source.replace('item.isOnGround()', 'item.onGround()')
    pose_cache.write_text(source, encoding='utf8')
buffered_outline_mixins = ('BufferedOutlineBuilderMixin', 'BufferedOutlineGeneratorMixin')
outline_renderer = drop_code/'client/BufferedItemOutlines.java'
outline_generator = drop_code/'mixin/BufferedOutlineGeneratorMixin.java'
outline_shaders = destination/'resources/assets/carbon/shaders'
buffered_outline_bridge = 'BufferedOutlineSourceMixin'
outline_source = drop_code/f'mixin/{buffered_outline_bridge}.java'
outline_buffer = drop_code/'client/OrderedOutlineBufferSource.java'
outline_buffer_bridge = (1,19,4) <= version <= (1,21,8)
if not outline_buffer_bridge:
    outline_source.unlink()
    outline_buffer.unlink()
elif version >= (1,21,1):
    # Since 1.21 the immediate source owns a ByteBufferBuilder and a sequenced layer map.
    outline_source.write_text(outline_source.read_text(encoding='utf8')
                              .replace('BufferBuilder', 'ByteBufferBuilder'), encoding='utf8')
    outline_buffer.write_text(outline_buffer.read_text(encoding='utf8')
                              .replace('BufferBuilder', 'ByteBufferBuilder')
                              .replace('java.util.Map', 'java.util.LinkedHashMap')
                              .replace('Map.of()', 'new LinkedHashMap<>()'), encoding='utf8')
if version >= (1,21,10):
    outline_renderer.unlink()
    for name in buffered_outline_mixins:
        (drop_code/f'mixin/{name}.java').unlink()
    for extension in ('vsh', 'fsh'):
        (outline_shaders/f'buffered_outline.{extension}').unlink()
elif version >= (1,21,1):
    source = (outline_generator.read_text(encoding='utf8')
              .replace('import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;\n', '')
              .replace(' extends DefaultedVertexConsumer', '')
              .replace('    @Unique', '    @Shadow @Final private int color;\n    @Unique', 1)
              .replace('defaultA << 24 | defaultR << 16 | defaultG << 8 | defaultB', 'color')
              .replace('{"endVertex", "vertex(FFFFFFFFFIIFFF)V"}', '"addVertex(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"')
              .replace('CallbackInfo;', 'CallbackInfoReturnable;')
              .replace('CallbackInfo ci', 'CallbackInfoReturnable<VertexConsumer> ci'))
    outline_generator.write_text(source, encoding='utf8')
    if buffered_entities:
        source = (outline_renderer.read_text(encoding='utf8')
                  .replace('layer.setupRenderState();', 'RenderCompat.setupType(layer);')
                  .replace('layer.clearRenderState();', 'RenderCompat.clearType(layer);'))
        if project.name == '1.21.5':
            source = source.replace('com.mojang.blaze3d.platform.GlStateManager', 'com.mojang.blaze3d.opengl.GlStateManager')
            source = source.replace('GlStateManager._bindTexture(RenderSystem.getShaderTexture(0));',
                                    '''if (RenderSystem.getShaderTexture(0) instanceof com.mojang.blaze3d.opengl.GlTexture texture) {
            GlStateManager._bindTexture(texture.glId());
            texture.flushModeChanges();
        }''')
        else:
            source = (source.replace('import com.mojang.blaze3d.platform.GlStateManager;\n', '')
                      .replace('glUniformMatrix4fv(projectionUniform, false, RenderCompat.floats(RenderSystem.getProjectionMatrix(), matrix));',
                               'RenderCompat.bindShared(program);')
                      .replace('RenderSystem.getShaderColor()', 'RenderCompat.color()')
                      .replace('        int activeTexture = GlStateManager._getActiveTexture();\n'
                               '        GlStateManager._activeTexture(GL_TEXTURE0);\n'
                               '        GlStateManager._bindTexture(RenderSystem.getShaderTexture(0));\n'
                               '        GlStateManager._activeTexture(activeTexture);\n', ''))
            vertex = outline_shaders/'buffered_outline.vsh'
            vertex.write_text(vertex.read_text(encoding='utf8').replace('uniform mat4 ProjMat;',
                              'layout(std140) uniform Projection { mat4 ProjMat; };'), encoding='utf8')
        outline_renderer.write_text(source, encoding='utf8')
if retained_items:
    data['mixins'].append('carbon.external-drops.mixins.json')
    external = destination/'resources/carbon.external-drops.mixins.json'
    extra_mixins = json.loads(external.read_text(encoding='utf8'))
    extra_mixins['compatibilityLevel'] = 'JAVA_'+str(java)
    external.write_text(json.dumps(extra_mixins, indent=2), encoding='utf8')
if int(project.name.split('.')[0])>=26:
    data['depends'].pop('fabric',None);data['depends']['fabric-api']='*';data['depends']['fabricloader']='>=0.18.4'
if (1,20) <= tuple(map(int,project.name.split('.'))) < (26,):data['depends']['fabricloader']='>=0.15.11'
data['description']='GPU item instancing for Minecraft '+project.name+' Fabric.'
metadata.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')
config=destination/'resources/carbon.mixins.json';data=json.loads(config.read_text());data['compatibilityLevel']='JAVA_'+str(java);
if outline_buffer_bridge and buffered_outline_bridge not in data['client']:
    data['client'].append(buffered_outline_bridge)
elif not outline_buffer_bridge and buffered_outline_bridge in data['client']:
    data['client'].remove(buffered_outline_bridge)
for name in buffered_outline_mixins:
    if version >= (1,21,10) and name in data['client']:
        data['client'].remove(name)
    elif version < (1,21,10) and name not in data['client']:
        data['client'].append(name)
if 'OutlineShaderMixin' not in data['client']:
    data['client'].append('OutlineShaderMixin')
if re_lookup_version and 'ReEntityOutlinerItemLookupMixin' not in data['client']:
    data['client'].append('ReEntityOutlinerItemLookupMixin')
if shared_dropped_outlines and project.name != '26.2':
    data['client'].append('OutlineBufferSourceMixin')
if buffered_entities:
    data['client'].append('IrisEmptyRenderGroupMixin')
if retained_items:
    data['client'].extend(['ItemStateExtractionMixin', 'RetainedItemSubmissionMixin', 'ItemLimitExtractionMixin'])
    data['client'].extend(['ItemFrustumAccess', 'ItemVisibilityMixin', 'ItemSectionVisibilityMixin',
                           'ItemDrawPacketMixin', 'ItemSimulationTickMixin'])
    if project.name in {'26.2', '26.3'}:
        data['client'].append('LevelExtractorItemStatesMixin')
if tuple(map(int,project.name.split('.'))) >= (1,21,5):
    assert 'ItemModelSubmissionMixin' in data['client'], 'Generic item submission hook must be registered'
config.write_text(json.dumps(data,indent=2),encoding='utf8')
if project.name=='1.17.1':(destination/'java/first/carbon/compat/IrisLayer.java').unlink()
