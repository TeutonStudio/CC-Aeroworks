#!/usr/bin/env python3
"""Render wiki recipe cards from recipe JSON and the installed Minecraft model assets.

Requires Pillow and NumPy. Reads dependency assets from libs/ without copying their
source textures into the wiki. Generated icons are orthographic model renders.
"""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import json
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'wiki/assets/recipes'
RESOURCES = ROOT / 'src/main/resources'
ARCHIVES = [ZipFile(p) for p in sorted((ROOT / 'libs').glob('*.jar'))]


def resource(path):
    p = RESOURCES / path
    if p.exists():
        return p.read_bytes()
    for z in ARCHIVES:
        if path in z.namelist():
            return z.read(path)
    raise FileNotFoundError(path)


def model(identifier):
    ns, name = identifier.split(':', 1) if ':' in identifier else ('minecraft', identifier)
    if ns == 'minecraft':
        if name in ('block/cube', 'cube'):
            return {'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16],
                    'faces': {side: {'texture': '#' + side} for side in ['up','down','north','south','east','west']}}]}
        return {}
    data = json.loads(resource(f'assets/{ns}/models/{name}.json'))
    parent = model(data['parent']) if 'parent' in data else {}
    return {**parent, **data, 'textures': {**parent.get('textures', {}), **data.get('textures', {})}}


def texture(identifier, textures):
    seen = set()
    while identifier.startswith('#'):
        if identifier in seen:
            raise ValueError('Texture cycle')
        seen.add(identifier)
        identifier = textures[identifier[1:]]
    ns, path = identifier.split(':')
    return Image.open(BytesIO(resource(f'assets/{ns}/textures/{path}.png'))).convert('RGBA')


def icon(identifier, size=300):
    ns, name = identifier.split(':')
    data = model(f'{ns}:item/{name}')
    if 'elements' not in data:
        image = texture(data['textures']['layer0'], data['textures'])
        image.thumbnail((size, size), Image.Resampling.NEAREST)
        out = Image.new('RGBA', (size, size))
        out.alpha_composite(image.resize((size, size), Image.Resampling.NEAREST))
        return out
    # Orthographic view showing north, east and upper faces.
    camera = np.array([1.35, 1.8, -2.8]); camera /= np.linalg.norm(camera)
    right = np.cross(np.array([0.,1.,0.]), camera); right /= np.linalg.norm(right)
    up = np.cross(camera, right)
    polygons=[]
    for element in data['elements']:
        x,y,z = element['from']; X,Y,Z = element['to']
        faces={
          'north': [(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],
          'south': [(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],
          'east': [(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],
          'west': [(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],
          'up': [(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],
          'down': [(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],
        }
        for side, face in element['faces'].items():
            vertices=np.array(faces[side], dtype=float)
            rotation=element.get('rotation')
            if rotation:
                angle=math.radians(rotation['angle']); axis='xyz'.index(rotation['axis'])
                origin=np.array(rotation['origin'])
                a,b=[i for i in range(3) if i!=axis]
                v=vertices-origin
                if rotation.get('rescale'):
                    v[:,[a,b]] /= math.cos(angle)
                va=v[:,a].copy(); vb=v[:,b].copy()
                v[:,a]=va*math.cos(angle)-vb*math.sin(angle)
                v[:,b]=va*math.sin(angle)+vb*math.cos(angle)
                vertices=v+origin
            projected=np.column_stack([vertices@right, -(vertices@up), vertices@camera])
            uv=face.get('uv', [0,0,16,16]); u,v,U,V=uv
            coords=np.array([(u,v),(U,v),(U,V),(u,V)],dtype=float)/16
            coords=np.roll(coords, face.get('rotation',0)//90,axis=0)
            image=np.array(texture(face['texture'], data['textures']))
            shade={'up':1.0,'down':0.55,'north':0.9,'south':0.7,'east':0.78,'west':0.72}[side]
            polygons.append((projected,coords,image,shade))
    all_points=np.concatenate([p[0] for p in polygons])
    lo=all_points[:,:2].min(axis=0); hi=all_points[:,:2].max(axis=0)
    scale=(size-22)/max(hi-lo); mid=(lo+hi)/2
    canvas=np.zeros((size,size,4),dtype=np.uint8); depth=np.full((size,size),-np.inf)
    for points,uv,tex,shade in polygons:
        pts=points.copy(); pts[:,:2]=(pts[:,:2]-mid)*scale+size/2
        for indices in [(0,1,2),(0,2,3)]:
            tri=pts[list(indices)]; tuv=uv[list(indices)]
            x0,y0=np.maximum(0,np.floor(tri[:,:2].min(axis=0)).astype(int))
            x1,y1=np.minimum(size-1,np.ceil(tri[:,:2].max(axis=0)).astype(int))
            if x1<x0 or y1<y0: continue
            xx,yy=np.meshgrid(np.arange(x0,x1+1)+.5,np.arange(y0,y1+1)+.5)
            A=np.vstack([tri[:,:2].T,np.ones(3)])
            if abs(np.linalg.det(A))<1e-7: continue
            weights=np.linalg.inv(A)@np.stack([xx,yy,np.ones_like(xx)]).reshape(3,-1)
            inside=(weights>=-1e-6).all(axis=0)
            zvalue=tri[:,2]@weights
            coords=tuv.T@weights
            tx=np.clip((coords[0]*tex.shape[1]).astype(int),0,tex.shape[1]-1)
            ty=np.clip((coords[1]*tex.shape[0]).astype(int),0,tex.shape[0]-1)
            pixels=tex[ty,tx].copy(); pixels[:,:3]=(pixels[:,:3]*shade).astype(np.uint8)
            old=depth[y0:y1+1,x0:x1+1].reshape(-1)
            mask=inside & (zvalue>=old) & (pixels[:,3]>0)
            sy,sx=np.where(mask.reshape(xx.shape)); gx=sx+x0; gy=sy+y0
            canvas[gy,gx]=pixels[mask]; depth[gy,gx]=zvalue[mask]
    return Image.fromarray(canvas)


LABELS={
 'de': {'pressing':'Mechanische Presse', 'deploying':'Deployer',
   'monitor_normal':'Normaler Monitor', 'monitor_advanced':'Erweiterter Monitor',
   'small_display':'Kleines Display', 'advanced_small_display':'Erweitertes kleines Display',
   'large_display':'Großes Display', 'advanced_large_display':'Erweitertes großes Display', 'iron_sheet':'Eisenplatte'},
 'en-gb': {'pressing':'Mechanical Press', 'deploying':'Deployer',
   'monitor_normal':'Normal Monitor', 'monitor_advanced':'Advanced Monitor',
   'small_display':'Small Display', 'advanced_small_display':'Advanced Small Display',
   'large_display':'Large Display', 'advanced_large_display':'Advanced Large Display', 'iron_sheet':'Iron Sheet'},
}
LABELS['en-us']=LABELS['en-gb']
FONT='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
if not Path(FONT).exists():
    FONT='/usr/share/fonts/google-noto-vf/NotoSans[wght].ttf'


def font(size): return ImageFont.truetype(FONT,size)
def centred(draw,x,y,text,fill='#e6edf3',size=23):
    f=font(size); b=draw.textbbox((0,0),text,font=f)
    draw.text((x-(b[2]-b[0])/2,y),text,font=f,fill=fill)


def main():
    OUTPUT.mkdir(parents=True,exist_ok=True)
    ids=['computercraft:monitor_normal','computercraft:monitor_advanced','create:iron_sheet',
         'create:mechanical_press','create:deployer']+[
         'cc_aeroworks:'+name for name in ['small_display','advanced_small_display','large_display','advanced_large_display']]
    icons={i:icon(i) for i in ids}
    for name in ['small_display','advanced_small_display','large_display','advanced_large_display']:
        process='pressing' if name.endswith('small_display') else 'deploying'
        path=ROOT/f'src/main/resources/data/cc_aeroworks/recipe/{process}/{name}.json'
        recipe=json.loads(path.read_text()); assert recipe['type']=='create:'+process
        ingredients=[entry['item'] for entry in recipe['ingredients']]
        result=recipe['results'][0]['id']; assert result=='cc_aeroworks:'+name
        for lang, labels in LABELS.items():
            folder=OUTPUT/lang; folder.mkdir(exist_ok=True)
            image=Image.new('RGBA',(1280,400),'#0d1117'); d=ImageDraw.Draw(image)
            d.rounded_rectangle((2,2,1277,397),radius=18,fill='#161b22',outline='#30363d',width=2)
            centred(d,640,22,labels[process],size=29)
            machine='create:mechanical_press' if process=='pressing' else 'create:deployer'
            image.alpha_composite(icons[machine].resize((132,132),Image.Resampling.LANCZOS),(574,72))
            xs=[235] if len(ingredients)==1 else [170,418]
            for x, ingredient in zip(xs,ingredients):
                d.rounded_rectangle((x-80,135,x+80,295),radius=8,fill='#21262d',outline='#484f58',width=2)
                image.alpha_composite(icons[ingredient].resize((150,150),Image.Resampling.LANCZOS),(x-75,140))
                centred(d,x,312,labels[ingredient.split(':')[1]],size=20)
                centred(d,x,345,'× 1',fill='#8b949e',size=19)
            if len(ingredients)==2: centred(d,294,192,'+',fill='#8b949e',size=36)
            d.line((554,240,725,240),fill='#58a6ff',width=7)
            d.polygon([(725,240),(702,225),(702,255)],fill='#58a6ff')
            x=1010
            d.rounded_rectangle((x-100,115,x+100,305),radius=8,fill='#162a22',outline='#3fb950',width=2)
            image.alpha_composite(icons[result].resize((185,185),Image.Resampling.LANCZOS),(x-92,118))
            centred(d,x,312,labels[name],size=22); centred(d,x,345,'× 1',fill='#8b949e',size=19)
            image.convert('RGB').save(folder/f'{name}.png',optimize=True)
    preview=Image.new('RGB',(1280,800),'#0d1117')
    for i,name in enumerate(['small_display','advanced_small_display','large_display','advanced_large_display']):
        card=Image.open(OUTPUT/'de'/f'{name}.png').resize((640,200))
        preview.paste(card,((i%2)*640,(i//2)*200+200))
    preview.save('/tmp/cca-display-recipes-preview.png')
    print('Rendered 12 recipe cards from four recipe JSON files and nine model/texture sources.')


if __name__=='__main__': main()
