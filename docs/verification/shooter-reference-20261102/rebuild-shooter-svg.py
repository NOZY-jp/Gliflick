from pathlib import Path
import cv2,numpy as np,json,math,re,hashlib
from PIL import Image,ImageDraw
folder=Path(__file__).resolve().parent
repo=folder.parents[2]
root=folder/'references';dest=repo/'app/src/main/res/raw'
records=[]
for tier in range(1,6):
 name=f'Shooter{tier if tier>1 else ""}'
 rgb=np.asarray(Image.open(root/f'{name}.png').convert('RGB')).astype(np.float32)
 up=cv2.resize(rgb[:,:,0]-rgb[:,:,1],None,fx=8,fy=8,interpolation=cv2.INTER_CUBIC)
 mask=(up>30).astype(np.uint8)*255
 num,labels,stats,centers=cv2.connectedComponentsWithStats(mask)
 core=max(range(1,num),key=lambda i:stats[i,cv2.CC_STAT_AREA])
 cc,_=cv2.findContours((labels==core).astype(np.uint8)*255,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
 hull=cv2.convexHull(max(cc,key=cv2.contourArea).astype(np.float32)/8 - 0.4375)
 for eps in np.arange(.3,3,.05):
  triangle=cv2.approxPolyDP(hull,float(eps),True).reshape(-1,2)
  if len(triangle)==3:break
 assert len(triangle)==3
 pairs=[(float(np.linalg.norm(triangle[a]-triangle[b])),a,b) for a,b in [(0,1),(1,2),(0,2)]]
 _,a,b=min(pairs);tip=triangle[3-a-b];mid=(triangle[a]+triangle[b])/2
 origin=tip*.25+mid*.75
 axis=tip-mid;angle=math.atan2(float(axis[1]),float(axis[0]))
 tip_units=22.5 if tier<4 else 30.0
 units_per_pixel=tip_units/float(np.linalg.norm(tip-origin))
 rotation=np.array([[math.cos(angle),math.sin(angle)],[-math.sin(angle),math.cos(angle)]])
 contours,hierarchy=cv2.findContours(mask,cv2.RETR_TREE,cv2.CHAIN_APPROX_SIMPLE)
 polygons=[]
 for contour in contours:
  if cv2.contourArea(contour)/64<.25:continue
  points=cv2.approxPolyDP(contour,.4*8,True).reshape(-1,2)/8 - 0.4375
  canonical=((points-origin)@rotation.T)*units_per_pixel
  polygons.append(canonical)
 radius=max(float(np.linalg.norm(poly,axis=1).max()) for poly in polygons)
 # Retain measured relative positions; a single radius controls native drawing and caption clearance.
 radius=math.ceil(radius*1000)/1000
 paths=[]
 for poly in polygons:
  pts=poly/radius
  paths.append('M'+' L'.join(f'{x:.6f},{y:.6f}' for x,y in pts)+' Z')
 svg='<svg xmlns="http://www.w3.org/2000/svg" viewBox="-1 -1 2 2"><!-- Wiki '+name+': traced straight contours; forward is +X. --><path fill="#9E5B60" fill-rule="evenodd" d="'+''.join(paths)+'"/></svg>\n'
 (dest/f'enemy_shooter_{tier}.svg').write_bytes(svg.encode('utf-8'))
 record={'tier':tier,'source_url':f'https://alias.black/glyphica/img/{name}.png','sha256':hashlib.sha256((root/f'{name}.png').read_bytes()).hexdigest(),'origin_px':origin.tolist(),'forward_radians':angle,'units_per_pixel':units_per_pixel,'radius_units':radius,'component_count':num-1,'vertices':sum(len(p) for p in polygons)}
 records.append(record)
 print(record)
(folder/'shooter-reference-geometry.json').write_bytes((json.dumps(records,indent=2)+'\n').encode())
p=repo/'app/src/main/java/jp/ac/ritsumei/ise/phy/exp2/is0842rf/testgame/game/combat/EnemyCatalog.kt'
s=p.read_text(encoding='utf-8')
values=', '.join(str(r['radius_units'])+'f' for r in records)
s=re.sub(r'private val shooterVisualRadii = listOf\([^\n]+\)', 'private val shooterVisualRadii = listOf('+values+')',s)
p.write_bytes(s.encode('utf-8'))
