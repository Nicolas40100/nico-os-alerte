import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:speech_to_text/speech_recognition_result.dart';
import 'package:speech_to_text/speech_to_text.dart';
import 'package:ultralytics_yolo/ultralytics_yolo.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const NicoScouterApp());
}

class NicoScouterApp extends StatelessWidget {
  const NicoScouterApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Nico Scouter Clean',
      theme: ThemeData(
        brightness: Brightness.dark,
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.greenAccent,
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: const ScouterPage(),
    );
  }
}

class ScouterPage extends StatefulWidget {
  const ScouterPage({super.key});

  @override
  State<ScouterPage> createState() => _ScouterPageState();
}

class _ScouterPageState extends State<ScouterPage> {
  static const _modelPath = 'assets/models/yoloe-26s-seg-pf_w8a32.tflite';

  final _textController = TextEditingController();
  final _speech = SpeechToText();
  final _tts = FlutterTts();
  final _yoloController = YOLOViewController();

  bool _cameraEnabled = false;
  bool _modelReady = false;
  bool _searching = false;
  bool _speechReady = false;
  bool _ttsReady = false;

  String _status = '✅ Application prête. Appuie sur CAMÉRA pour lancer l’IA.';
  String _originalTarget = '';
  String _englishTarget = '';
  Set<String> _targets = <String>{};
  YOLOResult? _targetResult;
  DateTime _lastAlert = DateTime.fromMillisecondsSinceEpoch(0);
  DateTime? _lastSeen;
  Timer? _clearTimer;

  static const Map<String, String> _fr = {
    'chaussure': 'shoe', 'chaussures': 'shoe', 'basket': 'shoe', 'baskets': 'shoe',
    'imprimante': 'printer', 'cafetière': 'coffee maker', 'cafetiere': 'coffee maker',
    'machine a cafe': 'coffee maker', 'télécommande': 'remote control', 'telecommande': 'remote control',
    'bouteille': 'bottle', 'tasse': 'cup', 'verre': 'glass', 'plante': 'plant',
    'lampe': 'lamp', 'clé': 'key', 'cle': 'key', 'clés': 'key', 'cles': 'key',
    'portefeuille': 'wallet', 'sac': 'bag', 'téléphone': 'cell phone', 'telephone': 'cell phone',
    'portable': 'cell phone', 'livre': 'book', 'chaise': 'chair', 'table': 'table',
    'ordinateur': 'computer', 'écran': 'monitor', 'ecran': 'monitor', 'clavier': 'keyboard',
    'souris': 'mouse', 'casque': 'headphones', 'écouteurs': 'headphones', 'ecouteurs': 'headphones',
    'lunettes': 'glasses', 'montre': 'watch', 'horloge': 'clock', 'réveil': 'clock', 'reveil': 'clock',
    'canapé': 'sofa', 'canape': 'sofa', 'fauteuil': 'armchair', 'lit': 'bed', 'oreiller': 'pillow',
    'couverture': 'blanket', 'frigo': 'refrigerator', 'réfrigérateur': 'refrigerator', 'refrigerateur': 'refrigerator',
    'four': 'oven', 'micro onde': 'microwave', 'micro-ondes': 'microwave', 'grille pain': 'toaster',
    'bouilloire': 'kettle', 'aspirateur': 'vacuum cleaner', 'ventilateur': 'fan', 'miroir': 'mirror',
    'poubelle': 'trash can', 'ciseaux': 'scissors', 'marteau': 'hammer', 'tournevis': 'screwdriver',
    'perceuse': 'drill', 'vélo': 'bicycle', 'velo': 'bicycle', 'moto': 'motorcycle', 'voiture': 'car',
    'ballon': 'ball', 'jouet': 'toy', 'peluche': 'stuffed animal', 'serviette': 'towel',
    'assiette': 'plate', 'fourchette': 'fork', 'cuillère': 'spoon', 'cuillere': 'spoon', 'couteau': 'knife',
    'poêle': 'frying pan', 'poele': 'frying pan', 'casserole': 'pot', 'boîte': 'box', 'boite': 'box',
    'carton': 'box', 'chargeur': 'charger', 'câble': 'cable', 'cable': 'cable', 'prise': 'outlet',
    'bureau': 'desk', 'étagère': 'shelf', 'etagere': 'shelf', 'armoire': 'wardrobe', 'porte': 'door',
    'fenêtre': 'window', 'fenetre': 'window', 'rideau': 'curtain', 'tapis': 'rug', 'vase': 'vase',
  };

  static const Map<String, String> _aliases = {
    'shoes': 'shoe', 'sneaker': 'shoe', 'sneakers': 'shoe', 'trainer': 'shoe', 'trainers': 'shoe',
    'phone': 'cell phone', 'smartphone': 'cell phone', 'cellphone': 'cell phone',
    'remote': 'remote control', 'tv remote': 'remote control', 'coffee machine': 'coffee maker',
    'refrigerator': 'refrigerator', 'fridge': 'refrigerator', 'television': 'tv',
  };

  String _clean(String s) {
    return s.toLowerCase()
        .replaceAll('à', 'a').replaceAll('â', 'a').replaceAll('ä', 'a')
        .replaceAll('é', 'e').replaceAll('è', 'e').replaceAll('ê', 'e').replaceAll('ë', 'e')
        .replaceAll('î', 'i').replaceAll('ï', 'i')
        .replaceAll('ô', 'o').replaceAll('ö', 'o')
        .replaceAll('ù', 'u').replaceAll('û', 'u').replaceAll('ü', 'u')
        .replaceAll('ç', 'c')
        .replaceAll(RegExp(r"[^a-z0-9\s'-]"), ' ')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
  }

  String _alias(String s) => _aliases[s] ?? s;

  String _localTranslate(String raw) {
    final q = _clean(raw);
    final words = q.split(' ');
    for (final e in _fr.entries) {
      final key = _clean(e.key);
      if (key.contains(' ')) {
        if (q.contains(key)) return e.value;
      } else if (words.contains(key)) {
        return e.value;
      }
    }
    return q;
  }

  Set<String> _makeTargets(String english) {
    final q = _clean(english);
    final out = <String>{};
    void add(String s) {
      final v = _alias(_clean(s));
      if (v.isNotEmpty) out.add(v);
    }
    add(q);
    for (final w in q.split(' ')) {
      if (!{'a','an','the','of','white','black','red','green','blue','yellow','pink','grey','gray','brown','beige','small','big','large'}.contains(w)) {
        add(w);
      }
    }
    if (q.contains('sneaker') || q.contains('shoe')) add('shoe');
    if (q.contains('remote')) add('remote control');
    if (q.contains('coffee')) add('coffee maker');
    if (q.contains('phone')) add('cell phone');
    return out;
  }

  Future<void> _startSearch([String? forced]) async {
    final raw = (forced ?? _textController.text).trim();
    if (raw.isEmpty) return;
    final english = _localTranslate(raw);
    final targets = _makeTargets(english);
    setState(() {
      _originalTarget = raw;
      _englishTarget = english;
      _targets = targets;
      _targetResult = null;
      _searching = targets.isNotEmpty;
      _status = _searching
          ? '🔎 Recherche : $raw → $english'
          : 'Objet non compris.';
    });
  }

  void _stopSearch() {
    _clearTimer?.cancel();
    setState(() {
      _searching = false;
      _targetResult = null;
      _status = '⏹ Recherche arrêtée.';
    });
  }

  Future<void> _listen() async {
    if (!_speechReady) {
      try {
        _speechReady = await _speech.initialize();
      } catch (_) {
        _speechReady = false;
      }
    }
    if (!_speechReady) {
      if (mounted) setState(() => _status = 'Micro indisponible.');
      return;
    }
    if (_speech.isListening) {
      await _speech.stop();
      return;
    }
    if (mounted) setState(() => _status = '🎙️ Je t’écoute…');
    await _speech.listen(
      localeId: 'fr_FR',
      listenFor: const Duration(seconds: 8),
      pauseFor: const Duration(seconds: 2),
      onResult: _onSpeech,
    );
  }

  void _onSpeech(SpeechRecognitionResult result) {
    final text = result.recognizedWords.trim();
    if (text.isNotEmpty) {
      _textController.text = text;
      _textController.selection = TextSelection.collapsed(offset: text.length);
    }
    if (result.finalResult && text.isNotEmpty) _startSearch(text);
    if (mounted) setState(() {});
  }

  String _canonical(String className) => _alias(_clean(className));

  bool _matches(String className) {
    final c = _canonical(className);
    if (_targets.contains(c)) return true;
    for (final t in _targets) {
      if (t.length >= 4 && (c.contains(t) || t.contains(c))) return true;
    }
    return false;
  }

  void _handleResults(List<YOLOResult> results) {
    if (!_searching || _targets.isEmpty) return;
    final matches = results.where((r) => r.confidence >= 0.12 && _matches(r.className)).toList()
      ..sort((a, b) => b.confidence.compareTo(a.confidence));

    if (matches.isEmpty) {
      if (_lastSeen != null && DateTime.now().difference(_lastSeen!).inMilliseconds > 1000 && _targetResult != null) {
        setState(() {
          _targetResult = null;
          _status = '🔎 Recherche : $_originalTarget';
        });
      }
      return;
    }

    final best = matches.first;
    _lastSeen = DateTime.now();
    setState(() {
      _targetResult = best;
      _status = '🟩 TROUVÉ : $_originalTarget — ${(best.confidence * 100).round()} %';
    });

    final now = DateTime.now();
    if (now.difference(_lastAlert).inMilliseconds > 3500) {
      _lastAlert = now;
      SystemSound.play(SystemSoundType.alert);
      _speakFound();
    }

    _clearTimer?.cancel();
    _clearTimer = Timer(const Duration(milliseconds: 1300), () {
      if (!mounted || !_searching || _lastSeen == null) return;
      if (DateTime.now().difference(_lastSeen!).inMilliseconds >= 1200) {
        setState(() {
          _targetResult = null;
          _status = '🔎 Recherche : $_originalTarget';
        });
      }
    });
  }

  Future<void> _speakFound() async {
    try {
      if (!_ttsReady) {
        await _tts.setLanguage('fr-FR');
        await _tts.setSpeechRate(0.52);
        _ttsReady = true;
      }
      await _tts.speak('$_originalTarget trouvé');
    } catch (_) {}
  }

  void _toggleCamera() {
    setState(() {
      _cameraEnabled = !_cameraEnabled;
      _modelReady = false;
      _targetResult = null;
      _status = _cameraEnabled
          ? 'Chargement caméra et IA sur CPU…'
          : '📷 Caméra coupée.';
    });
  }

  @override
  void dispose() {
    _clearTimer?.cancel();
    _textController.dispose();
    _speech.cancel();
    _tts.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF090B10),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 12, 14, 8),
              child: Row(
                children: [
                  const Expanded(child: Text('🔎 Nico Scouter Clean', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800))),
                  Text(_modelReady ? 'IA prête' : (_cameraEnabled ? 'IA charge…' : 'IA arrêtée'), style: const TextStyle(fontSize: 12)),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _textController,
                      textInputAction: TextInputAction.search,
                      onSubmitted: (_) => _startSearch(),
                      decoration: InputDecoration(
                        hintText: 'imprimante, chaussures, bouteille…',
                        filled: true,
                        fillColor: const Color(0xFF141821),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filledTonal(onPressed: _listen, icon: const Icon(Icons.mic_none), tooltip: 'Parler'),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 8, 14, 8),
              child: SizedBox(width: double.infinity, child: FilledButton(onPressed: _startSearch, child: const Text('CHERCHER'))),
            ),
            Container(
              width: double.infinity,
              margin: const EdgeInsets.symmetric(horizontal: 14),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: const Color(0xFF121722), borderRadius: BorderRadius.circular(12)),
              child: Text(_status, style: const TextStyle(fontSize: 13)),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 14),
                clipBehavior: Clip.antiAlias,
                decoration: BoxDecoration(color: Colors.black, borderRadius: BorderRadius.circular(18), border: Border.all(color: const Color(0xFF29313D))),
                child: _cameraEnabled
                    ? Stack(
                        fit: StackFit.expand,
                        children: [
                          YOLOView(
                            modelPath: _modelPath,
                            task: YOLOTask.segment,
                            controller: _yoloController,
                            confidenceThreshold: 0.10,
                            iouThreshold: 0.7,
                            useGpu: false,
                            cameraResolution: '720p',
                            lensFacing: LensFacing.back,
                            onResult: _handleResults,
                            onModelLoad: (path, task) {
                              _yoloController.setShowOverlays(false);
                              if (!mounted) return;
                              setState(() {
                                _modelReady = true;
                                if (!_searching) _status = '✅ Caméra et IA prêtes.';
                              });
                            },
                            onModelError: (error, path, task) {
                              if (!mounted) return;
                              setState(() {
                                _modelReady = false;
                                _status = '❌ Erreur IA : $error';
                              });
                            },
                          ),
                          IgnorePointer(child: CustomPaint(painter: TargetBoxPainter(_targetResult, _originalTarget))),
                          if (!_modelReady)
                            const Center(child: Card(child: Padding(padding: EdgeInsets.all(12), child: Text('Chargement IA CPU…')))),
                        ],
                      )
                    : const Center(child: Text('Caméra coupée — appuie sur CAMÉRA')),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 10, 14, 14),
              child: Row(
                children: [
                  Expanded(child: OutlinedButton.icon(onPressed: _toggleCamera, icon: Icon(_cameraEnabled ? Icons.videocam_off : Icons.videocam), label: Text(_cameraEnabled ? 'COUPER CAMÉRA' : 'CAMÉRA'))),
                  const SizedBox(width: 8),
                  Expanded(child: OutlinedButton.icon(onPressed: _stopSearch, icon: const Icon(Icons.stop), label: const Text('STOP'))),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class TargetBoxPainter extends CustomPainter {
  final YOLOResult? result;
  final String label;
  TargetBoxPainter(this.result, this.label);

  @override
  void paint(Canvas canvas, Size size) {
    final r = result;
    if (r == null) return;
    final n = r.normalizedBox;
    final rect = Rect.fromLTRB(
      n.left.clamp(0.0, 1.0) * size.width,
      n.top.clamp(0.0, 1.0) * size.height,
      n.right.clamp(0.0, 1.0) * size.width,
      n.bottom.clamp(0.0, 1.0) * size.height,
    );
    final paint = Paint()..color = const Color(0xFF39FF88)..style = PaintingStyle.stroke..strokeWidth = 5;
    canvas.drawRect(rect, paint);
    final text = TextPainter(
      text: TextSpan(text: '$label ${(r.confidence * 100).round()}%', style: const TextStyle(color: Color(0xFF39FF88), fontSize: 17, fontWeight: FontWeight.w900, backgroundColor: Color(0xDD000000))),
      textDirection: TextDirection.ltr,
    )..layout(maxWidth: size.width - 16);
    final x = rect.left.clamp(4.0, (size.width - text.width - 4).clamp(4.0, size.width));
    final y = (rect.top - text.height - 6).clamp(4.0, size.height - text.height - 4);
    text.paint(canvas, Offset(x, y));
  }

  @override
  bool shouldRepaint(covariant TargetBoxPainter oldDelegate) => oldDelegate.result != result || oldDelegate.label != label;
}
