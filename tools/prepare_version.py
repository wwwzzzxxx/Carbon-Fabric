"""Generate a version overlay without modifying the verified baseline sources."""
from pathlib import Path
import json,shutil,sys
project=Path(sys.argv[1]).resolve();destination=Path(sys.argv[2]).resolve();root=project.parent.parent
assert root==Path(__file__).resolve().parent.parent and project.parent==root/'versions'
assert destination.is_relative_to(project/'build')
if destination.exists():shutil.rmtree(destination)
shutil.copytree(root/'src/main',destination)
buffered_entities = project.name in {'1.21.5', '1.21.8'}
if buffered_entities:
    shutil.copytree(root/'src/bufferedentities/java', destination/'java', dirs_exist_ok=True)
retained_items = project.name in {'1.21.10', '1.21.11', '26.1.2', '26.2', '26.3'}
if retained_items:
    shutil.copytree(root/'src/renderstate/java', destination/'java', dirs_exist_ok=True)
    if project.name.startswith('26.'):
        state_cache = destination/'java/first/carbon/client/StaticItemRenderStates.java'
        state_cache.write_text(state_cache.read_text(encoding='utf8').replace('BlockModelWrapper', 'CuboidItemModelWrapper'), encoding='utf8')
        submission = destination/'java/first/carbon/mixin/RetainedItemSubmissionMixin.java'
        submission.write_text(submission.read_text(encoding='utf8').replace('state.CameraRenderState', 'state.level.CameraRenderState'), encoding='utf8')
        limit = destination/'java/first/carbon/mixin/ItemLimitExtractionMixin.java'
        limit.write_text(limit.read_text(encoding='utf8').replace('state.LevelRenderState', 'state.level.LevelRenderState')
                         .replace('state/LevelRenderState', 'state/level/LevelRenderState'), encoding='utf8')
    if project.name in {'26.2', '26.3'}:
        shutil.copy2(root/'src/renderstate/extractor/LevelExtractorItemStatesMixin.java', destination/'java/first/carbon/mixin')
        limit = destination/'java/first/carbon/mixin/ItemLimitExtractionMixin.java'
        source = (limit.read_text(encoding='utf8')
                  .replace('renderer.LevelRenderer', 'renderer.extract.LevelExtractor')
                  .replace('renderer/LevelRenderer', 'renderer/extract/LevelExtractor')
                  .replace('LevelRenderer', 'LevelExtractor')
                  .replace('method = "renderLevel"', 'method = "extract"'))
        limit.write_text(source, encoding='utf8')
for file in (project/'overrides').rglob('*'):
    if file.is_file():
        target=destination/file.relative_to(project/'overrides');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(file,target)
patches=json.loads((project/'patches.json').read_text(encoding='utf8')) if (project/'patches.json').exists() else []
for patch in patches:
    path=destination/patch['file'];text=path.read_text(encoding='utf8');actual=text.count(patch['old'])
    if actual!=patch['count']:raise SystemExit(f"Patch mismatch {path}: expected {patch['count']}, got {actual}")
    path.write_text(text.replace(patch['old'],patch['new']),encoding='utf8')
metadata=destination/'resources/fabric.mod.json';data=json.loads(metadata.read_text(encoding='utf8'));data['depends']['minecraft']=project.name
matrix=json.loads((root/'versions/matrix.json').read_text(encoding='utf8'))
java=next(target['java'] for target in matrix['targets'] if target['minecraft']==project.name)
data['depends']['java']='>='+str(java)
if int(project.name.split('.')[0])>=26:
    data['depends'].pop('fabric',None);data['depends']['fabric-api']='*';data['depends']['fabricloader']='>=0.18.4'
if (1,20) <= tuple(map(int,project.name.split('.'))) < (26,):data['depends']['fabricloader']='>=0.15.11'
data['description']='GPU item instancing for Minecraft '+project.name+' Fabric.'
metadata.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')
config=destination/'resources/carbon.mixins.json';data=json.loads(config.read_text());data['compatibilityLevel']='JAVA_'+str(java);
if buffered_entities:
    data['client'].append('IrisEmptyRenderGroupMixin')
if retained_items:
    data['client'].extend(['ItemStateExtractionMixin', 'RetainedItemSubmissionMixin', 'ItemLimitExtractionMixin'])
    if project.name in {'26.2', '26.3'}:
        data['client'].append('LevelExtractorItemStatesMixin')
if tuple(map(int,project.name.split('.'))) >= (1,21,5):
    assert 'ItemModelSubmissionMixin' in data['client'], 'Generic item submission hook must be registered'
config.write_text(json.dumps(data,indent=2),encoding='utf8')
if project.name=='1.17.1':(destination/'java/first/carbon/compat/IrisLayer.java').unlink()
