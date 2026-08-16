import base64
from pathlib import Path
from ultralytics import YOLOE

b64 = Path('tests/shoes_test_640.b64').read_text().strip()
img = Path('/tmp/nico_shoes.jpg')
img.write_bytes(base64.b64decode(b64))

for model_name in ['yoloe-26s-seg-pf.pt']:
    print('\nLoading', model_name)
    model = YOLOE(model_name)
    results = model.predict(str(img), conf=0.02, imgsz=640, verbose=False)
    r = results[0]
    print('detections:', len(r.boxes) if r.boxes is not None else 0)
    rows=[]
    if r.boxes is not None:
        for b in r.boxes:
            cls=int(b.cls.item())
            conf=float(b.conf.item())
            name=r.names.get(cls, str(cls)) if isinstance(r.names, dict) else r.names[cls]
            xyxy=[round(float(x),1) for x in b.xyxy[0].tolist()]
            rows.append((conf,name,xyxy))
    for conf,name,xyxy in sorted(rows, reverse=True)[:40]:
        print(f'{conf:.4f}\t{name}\t{xyxy}')
    matches=[x for x in rows if any(w in x[1].lower() for w in ['shoe','sneaker'])]
    print('SHOE_MATCHES', sorted(matches, reverse=True)[:10])
