import base64
from pathlib import Path
from ultralytics import YOLOE, YOLO

b64 = Path('tests/shoes_test_640.b64').read_text().strip()
img = Path('/tmp/nico_shoes.jpg')
img.write_bytes(base64.b64decode(b64))

model_name='yoloe-26s-seg-pf.pt'
print('Loading', model_name)
model = YOLOE(model_name)

print('PT TEST')
r = model.predict(str(img), conf=0.02, imgsz=640, verbose=False)[0]
rows=[]
if r.boxes is not None:
    for b in r.boxes:
        cls=int(b.cls.item()); conf=float(b.conf.item())
        name=r.names.get(cls, str(cls)) if isinstance(r.names, dict) else r.names[cls]
        xyxy=[round(float(x),1) for x in b.xyxy[0].tolist()]
        rows.append((conf,name,xyxy))
print('PT_SHOE_MATCHES', sorted([x for x in rows if 'shoe' in x[1].lower() or 'sneaker' in x[1].lower()], reverse=True)[:10])

print('EXPORT LITERT W8A32')
exported = model.export(format='litert', imgsz=640, quantize='w8a32', device='cpu')
print('EXPORTED', exported)

print('LITERT TEST')
mobile = YOLO(exported)
r2 = mobile.predict(str(img), conf=0.02, imgsz=640, verbose=False)[0]
rows2=[]
if r2.boxes is not None:
    for b in r2.boxes:
        cls=int(b.cls.item()); conf=float(b.conf.item())
        name=r2.names.get(cls, str(cls)) if isinstance(r2.names, dict) else r2.names[cls]
        xyxy=[round(float(x),1) for x in b.xyxy[0].tolist()]
        rows2.append((conf,name,xyxy))
for conf,name,xyxy in sorted(rows2, reverse=True)[:30]:
    print(f'MOBILE\t{conf:.4f}\t{name}\t{xyxy}')
matches=sorted([x for x in rows2 if 'shoe' in x[1].lower() or 'sneaker' in x[1].lower()], reverse=True)
print('LITERT_SHOE_MATCHES', matches[:10])
if not matches or matches[0][0] < 0.10:
    raise SystemExit('FAIL: LiteRT export did not reliably detect shoe on exact user image')
print('PASS: LiteRT export detects shoe on exact user image')
