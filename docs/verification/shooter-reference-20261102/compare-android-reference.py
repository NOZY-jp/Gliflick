"""Compare real Android pixels with Wiki silhouettes using captured actor poses."""
from pathlib import Path
import json, math, argparse
import cv2
import numpy as np
from PIL import Image, ImageDraw

folder=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument("--screen",type=Path,default=folder/"android.png")
parser.add_argument("--poses",type=Path,default=folder/"android.json")
args=parser.parse_args()
screen=np.asarray(Image.open(args.screen).convert("RGB"))
poses=json.loads(args.poses.read_text(encoding="utf-8"))
references=json.loads((folder/"shooter-reference-geometry.json").read_text(encoding="utf-8"))
rows=[];results=[]
for reference in references:
    tier=reference["tier"]
    name=f"Shooter{tier if tier>1 else ''}"
    source=np.asarray(Image.open(folder/"references"/f"{name}.png").convert("RGB"))
    pose=next(p for p in poses["enemies"] if p["kind"]=="SHOOTER" and p["tier"]==tier)
    height,width=source.shape[:2]
    yy,xx=np.mgrid[:height,:width].astype(np.float32)
    dx=xx-reference["origin_px"][0];dy=yy-reference["origin_px"][1]
    angle=pose["angle"]-reference["forward_radians"]
    scale=reference["units_per_pixel"]*(4.5/7)*poses["unit"]
    map_x=(pose["x"]+scale*(dx*math.cos(angle)-dy*math.sin(angle))-.5).astype(np.float32)
    map_y=(pose["y"]+scale*(dx*math.sin(angle)+dy*math.cos(angle))-.5).astype(np.float32)
    actual=cv2.remap(screen,map_x,map_y,cv2.INTER_LINEAR,borderMode=cv2.BORDER_CONSTANT,borderValue=(240,240,230))
    source_mask=source[:,:,0].astype(float)-source[:,:,1]>30
    actual_mask=actual[:,:,0].astype(float)-actual[:,:,1]>30
    intersection=np.logical_and(source_mask,actual_mask).sum()
    union=np.logical_or(source_mask,actual_mask).sum()
    iou=float(intersection/union)
    count,labels,stats,_=cv2.connectedComponentsWithStats(source_mask.astype(np.uint8))
    recall=[]
    for component in range(1,count):
        if stats[component,cv2.CC_STAT_AREA]<3:continue
        region=labels==component
        recall.append(float((region&actual_mask).sum()/region.sum()))
    results.append({"tier":tier,"silhouette_iou":iou,"reference_component_recall":recall,
                    "pass":iou>=.85 and min(recall)>=.75})
    print(results[-1])
    overlay=np.full_like(source,244)
    overlay[source_mask&actual_mask]=(70,120,96)
    overlay[source_mask&~actual_mask]=(193,77,94)
    overlay[~source_mask&actual_mask]=(55,104,171)
    ys,xs=np.where(source_mask|actual_mask)
    bounds=(max(0,int(xs.min())-5),max(0,int(ys.min())-5),min(width,int(xs.max())+6),min(height,int(ys.max())+6))
    row=Image.new("RGB",(900,200),(248,248,242));draw=ImageDraw.Draw(row)
    draw.text((8,8),f"Tier {tier}   IoU {iou:.3f}",fill=(50,50,50))
    for column,(picture,label) in enumerate([(source,"Wiki reference"),(actual,"Android capture - aligned"),
                                          (overlay,"Green=match  Red/Blue=difference")]):
        cropped=Image.fromarray(picture).crop(bounds)
        enlarged=cropped.resize((cropped.width*3,cropped.height*3),Image.Resampling.NEAREST)
        enlarged.thumbnail((280,155))
        row.paste(enlarged,(column*300+(300-enlarged.width)//2,32))
        draw.text((column*300+10,180),label,fill=(50,50,50))
    rows.append(row)
panel=Image.new("RGB",(900,200*len(rows)),(248,248,242))
for i,row in enumerate(rows):panel.paste(row,(0,i*200))
panel.save(folder/"comparison.png")
(folder/"comparison-results.json").write_bytes((json.dumps(results,indent=2)+"\n").encode())
if not all(r["pass"] for r in results):raise SystemExit("Reference comparison failed; inspect capture and geometry.")
