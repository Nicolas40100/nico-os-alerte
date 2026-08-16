import base64
from pathlib import Path
from ultralytics import YOLOE

b64 = Path('tests/shoes_test_640.b64').read_text().strip()
img = Path('/tmp/nico_shoes.jpg')
img.write_bytes(base64.b64decode(b64))

print('Loading YOLOE prompt-free...')
model = YOLOE('yoloe-26n-seg-pf.pt')
print('Model loaded')

results = model.predict(str(img), conf=0.03, imgsz=640, verbose=False)
r = results[0]
print('detections:', len(r.boxes) if r.boxes is not None else 0)
if r.boxes is not None:
    rows=[]
    for b in r.boxes:
        cls=int(b.cls.item())
        conf=float(b.conf.item())
        name=r.names.get(cls, str(cls)) if isinstance(r.names, dict) else r.names[cls]
        xyxy=[round(float(x),1) for x in b.xyxy[0].tolist()]
        rows.append((conf,name,xyxy))
    for conf,name,xyxy in sorted(rows, reverse=True)[:30]:
        print(f'{conf:.4f}\t{name}\t{xyxy}')
