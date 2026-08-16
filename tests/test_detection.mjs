import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pipeline, RawImage } from '@huggingface/transformers';

const b64 = fs.readFileSync('tests/shoes_test_640.b64', 'utf8').trim();
const imgPath = path.join(os.tmpdir(), 'nico-shoes.jpg');
fs.writeFileSync(imgPath, Buffer.from(b64, 'base64'));
const image = await RawImage.read(imgPath);

console.log('image', image.width, image.height);
const detector = await pipeline('zero-shot-object-detection', 'onnx-community/grounding-dino-tiny-ONNX', { dtype: 'q8' });

const tests = [
  'shoe',
  'a shoe.',
  'white shoe',
  'a white shoe.',
  'sneaker',
  'a sneaker.',
  'white sneaker',
  'a white sneaker.',
  'white shoes.',
  'a pair of white shoes.'
];

for (const label of tests) {
  try {
    const out = await detector(image, [label], { threshold: 0.01 });
    const top = [...out].sort((a,b)=>b.score-a.score).slice(0,5);
    console.log('\nLABEL', JSON.stringify(label), 'COUNT', out.length);
    console.log(JSON.stringify(top));
  } catch (e) {
    console.log('\nLABEL', JSON.stringify(label), 'ERROR', e?.stack || e?.message || String(e));
  }
}
