import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:google_mlkit_translation/google_mlkit_translation.dart';
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
      title: 'Nico Scouter Native',
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
  final _translator = OnDeviceTranslator(
    sourceLanguage: TranslateLanguage.french,
    targetLanguage: TranslateLanguage.english,
  );
  final _translationModels = OnDeviceTranslatorModelManager();

  bool _speechReady = false;
  bool _modelReady = false;
  bool _cameraEnabled = true;
  bool _searching = false;
  bool _translationReady = false;
  bool _preparingSearch = false;

  String _status = 'Chargement du moteur natif…';
  String _originalTarget = '';
  String _englishTarget = '';
  Set<String> _targetCandidates = <String>{};
  YOLOResult? _targetResult;
  DateTime? _lastSeen;
  DateTime _lastAlert = DateTime.fromMillisecondsSinceEpoch(0);
  Timer? _clearTimer;

  static const Map<String, String> _fastFrench = {
    'chaussure': 'shoe',
    'chaussures': 'shoe',
    'basket': 'shoe',
    'baskets': 'shoe',
    'sneaker': 'shoe',
    'sneakers': 'shoe',
    'imprimante': 'printer',
    'imprimantes': 'printer',
    'cafetière': 'coffee maker',
    'cafetiere': 'coffee maker',
    'télécommande': 'remote control',
    'telecommande': 'remote control',
    'bouteille': 'bottle',
    'bouteilles': 'bottle',
    'tasse': 'cup',
    'tasses': 'cup',
    'verre': 'glass',
    'plante': 'plant',
    'plantes': 'plant',
    'bananier': 'banana plant',
    'lampe': 'lamp',
    'lampes': 'lamp',
    'clé': 'key',
    'cle': 'key',
    'clés': 'key',
    'cles': 'key',
    'portefeuille': 'wallet',
    'sac': 'bag',
    'sacs': 'bag',
    'téléphone': 'cell phone',
    'telephone': 'cell phone',
    'portable': 'cell phone',
    'livre': 'book',
    'livres': 'book',
    'chaise': 'chair',
    'chaises': 'chair',
    'table': 'table',
    'ordinateur': 'computer',
    'écran': 'monitor',
    'ecran': 'monitor',
    'clavier': 'keyboard',
    'souris': 'mouse',
    'casque': 'headphones',
  };

  static const Map<String, String> _aliases = {
    'sneaker': 'shoe',
    'sneakers': 'shoe',
    'trainer': 'shoe',
    'trainers': 'shoe',
    'shoes': 'shoe',
    'footwear': 'shoe',
    'printer machine': 'printer',
    'printing machine': 'printer',
    'cellphone': 'cell phone',
    'smartphone': 'cell phone',
    'phone': 'cell phone',
    'coffee machine': 'coffee maker',
    'coffeemaker': 'coffee maker',
    'remote': 'remote control',
    'television remote': 'remote control',
    'tv remote': 'remote control',
    'handbag': 'bag',
  };

  static const Set<String> _ignoredWords = {
    'a', 'an', 'the', 'some', 'pair', 'of',
    'white', 'black', 'red', 'blue', 'green', 'yellow', 'orange', 'pink',
    'purple', 'grey', 'gray', 'brown', 'beige', 'silver', 'gold', 'golden',
    'small', 'big', 'large', 'little', 'round', 'square', 'old', 'new',
  };

  @override
  void initState() {
    super.initState();
    _yoloController.setShowOverlays(false);
    _initSpeech();
    _initTts();
  }

  Future<void> _initSpeech() async {
    try {
      final ready = await _speech.initialize(
        onError: (e) {
          if (!mounted) return;
          setState(() => _status = 'Micro : ${e.errorMsg}');
        },
      );
      if (mounted) setState(() => _speechReady = ready);
    } catch (_) {}
  }

  Future<void> _initTts() async {
    try {
      await _tts.setLanguage('fr-FR');
      await _tts.setSpeechRate(0.52);
      await _tts.setVolume(1.0);
    } catch (_) {}
  }

  @override
  void dispose() {
    _clearTimer?.cancel();
    _textController.dispose();
    _speech.cancel();
    _tts.stop();
    _translator.close();
    super.dispose();
  }

  String _clean(String input) {
    return input
        .toLowerCase()
        .replaceAll(RegExp(r"[^a-z0-9àâäéèêëîïôöùûüçœ\s'-]"), ' ')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
  }

  String _singular(String word) {
    if (word.endsWith('ies') && word.length > 4) {
      return '${word.substring(0, word.length - 3)}y';
    }
    if (word.endsWith('ses') && word.length > 4) {
      return word.substring(0, word.length - 2);
    }
    if (word.endsWith('s') && !word.endsWith('ss') && word.length > 3) {
      return word.substring(0, word.length - 1);
    }
    return word;
  }

  String _alias(String text) => _aliases[text] ?? text;

  Set<String> _makeCandidates(String english) {
    final cleaned = _clean(english)
        .replaceAll(RegExp(r"[^a-z0-9\s'-]"), ' ')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
    if (cleaned.isEmpty) return <String>{};

    final result = <String>{};
    void add(String value) {
      var v = value.trim();
      if (v.isEmpty) return;
      v = _alias(v);
      result.add(v);
    }

    add(cleaned);
    final tokens = cleaned.split(' ');
    final useful = tokens.where((w) => !_ignoredWords.contains(w)).toList();
    if (useful.isNotEmpty) {
      final phrase = useful.join(' ');
      add(phrase);
      add(_singular(phrase));
      final last = _singular(useful.last);
      add(last);
      if (useful.length >= 2) {
        add('${useful[useful.length - 2]} $last');
      }
    }

    final extra = <String>{};
    for (final item in result) {
      extra.add(_alias(_singular(item)));
    }
    result.addAll(extra);

    if (result.any((x) => x == 'sneaker' || x == 'trainer' || x == 'footwear')) {
      result.add('shoe');
    }
    if (cleaned.contains('coffee')) result.add('coffee maker');
    if (cleaned.contains('remote')) result.add('remote control');
    if (cleaned.contains('phone')) result.add('cell phone');
    if (cleaned.contains('banana') && cleaned.contains('plant')) {
      result.add('plant');
    }
    return result;
  }

  String _knownTranslation(String french) {
    final f = _clean(french);
    for (final entry in _fastFrench.entries) {
      if (f == entry.key || f.contains('${entry.key} ') || f.endsWith(' ${entry.key}')) {
        return entry.value;
      }
    }
    if (f.contains('chauss') || f.contains('basket') || f.contains('sneaker')) {
      return 'shoe';
    }
    if (f.contains('imprimante')) return 'printer';
    return '';
  }

  Future<void> _ensureTranslationModels() async {
    if (_translationReady) return;
    try {
      await _translationModels.downloadModel(TranslateLanguage.french.bcpCode);
      await _translationModels.downloadModel(TranslateLanguage.english.bcpCode);
      _translationReady = true;
    } catch (_) {
      // The translator may still work if the device already has the models.
    }
  }

  Future<String> _translateTarget(String raw) async {
    final known = _knownTranslation(raw);
    if (known.isNotEmpty) return known;

    final cleaned = _clean(raw);
    final looksEnglish = RegExp(r'^[a-z0-9\s-]+$').hasMatch(cleaned) &&
        <String>{'shoe','shoes','sneaker','sneakers','printer','bottle','cup','plant','lamp','chair','table','book','wallet','key','bag','computer','keyboard','mouse'}
            .any((word) => cleaned.contains(word));
    if (looksEnglish) return cleaned;

    try {
      await _ensureTranslationModels();
      final translated = await _translator.translateText(raw);
      if (translated.trim().isNotEmpty) return translated.trim();
    } catch (_) {}
    return raw;
  }

  Future<void> _startSearch([String? forced]) async {
    final raw = (forced ?? _textController.text).trim();
    if (raw.isEmpty || _preparingSearch) return;

    setState(() {
      _preparingSearch = true;
      _searching = false;
      _targetResult = null;
      _originalTarget = raw;
      _status = 'Préparation de « $raw »…';
    });

    final english = await _translateTarget(raw);
    final candidates = _makeCandidates(english);

    if (!mounted) return;
    setState(() {
      _englishTarget = english;
      _targetCandidates = candidates;
      _preparingSearch = false;
      _searching = candidates.isNotEmpty;
      _status = candidates.isEmpty
          ? 'Je n’ai pas compris l’objet.'
          : '🔎 Recherche active : $raw → $english';
    });
  }

  void _stopSearch() {
    _clearTimer?.cancel();
    setState(() {
      _searching = false;
      _preparingSearch = false;
      _targetResult = null;
      _status = '⏹ Recherche arrêtée.';
    });
  }

  Future<void> _listen() async {
    if (!_speechReady) {
      await _initSpeech();
    }
    if (!_speechReady) {
      if (mounted) setState(() => _status = 'Reconnaissance vocale indisponible.');
      return;
    }
    if (_speech.isListening) {
      await _speech.stop();
      return;
    }

    setState(() => _status = '🎙️ Je t’écoute…');
    await _speech.listen(
      localeId: 'fr_FR',
      listenFor: const Duration(seconds: 8),
      pauseFor: const Duration(seconds: 2),
      onResult: _onSpeechResult,
    );
  }

  void _onSpeechResult(SpeechRecognitionResult result) {
    final words = result.recognizedWords.trim();
    if (words.isNotEmpty) {
      _textController.text = words;
      _textController.selection = TextSelection.collapsed(offset: words.length);
    }
    if (result.finalResult && words.isNotEmpty) {
      _startSearch(words);
    }
    if (mounted) setState(() {});
  }

  String _canonicalClass(String className) {
    final c = _clean(className)
        .replaceAll(RegExp(r"[^a-z0-9\s'-]"), ' ')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
    return _alias(_singular(c));
  }

  bool _matchesTarget(String className) {
    if (_targetCandidates.isEmpty) return false;
    final c = _canonicalClass(className);
    if (_targetCandidates.contains(c)) return true;
    for (final target in _targetCandidates) {
      final t = _alias(_singular(target));
      if (c == t) return true;
      if (t.length >= 5 && (c.contains(t) || t.contains(c))) return true;
    }
    return false;
  }

  void _handleResults(List<YOLOResult> results) {
    if (!_searching || _targetCandidates.isEmpty) return;

    final matches = results
        .where((r) => r.confidence >= 0.15 && _matchesTarget(r.className))
        .toList()
      ..sort((a, b) => b.confidence.compareTo(a.confidence));

    if (matches.isEmpty) {
      final seen = _lastSeen;
      if (seen != null && DateTime.now().difference(seen).inMilliseconds > 900) {
        if (_targetResult != null && mounted) {
          setState(() {
            _targetResult = null;
            _status = '🔎 Recherche active : $_originalTarget';
          });
        }
      }
      return;
    }

    final best = matches.first;
    _lastSeen = DateTime.now();
    if (mounted) {
      setState(() {
        _targetResult = best;
        _status = '🟩 TROUVÉ : $_originalTarget — ${(best.confidence * 100).round()} %';
      });
    }

    final now = DateTime.now();
    if (now.difference(_lastAlert).inMilliseconds > 3500) {
      _lastAlert = now;
      SystemSound.play(SystemSoundType.alert);
      _tts.speak('$_originalTarget trouvé');
    }

    _clearTimer?.cancel();
    _clearTimer = Timer(const Duration(milliseconds: 1200), () {
      if (!mounted || !_searching) return;
      if (_lastSeen != null && DateTime.now().difference(_lastSeen!).inMilliseconds >= 1100) {
        setState(() {
          _targetResult = null;
          _status = '🔎 Recherche active : $_originalTarget';
        });
      }
    });
  }

  void _toggleCamera() {
    setState(() {
      _cameraEnabled = !_cameraEnabled;
      _modelReady = false;
      _targetResult = null;
      _status = _cameraEnabled ? 'Redémarrage caméra / IA…' : '📷 Caméra coupée.';
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF090B10),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 10, 14, 6),
              child: Row(
                children: [
                  const Expanded(
                    child: Text(
                      '🔎 Nico Scouter Native',
                      style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
                    decoration: BoxDecoration(
                      color: _modelReady ? const Color(0xFF173C28) : const Color(0xFF252B35),
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      _modelReady ? 'IA native : prête' : 'IA : chargement',
                      style: const TextStyle(fontSize: 12),
                    ),
                  ),
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
                        hintText: 'imprimante, chaussures, cafetière…',
                        filled: true,
                        fillColor: const Color(0xFF141821),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filledTonal(
                    onPressed: _listen,
                    icon: Icon(_speech.isListening ? Icons.mic : Icons.mic_none),
                    tooltip: 'Parler',
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 8, 14, 8),
              child: SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _preparingSearch ? null : () => _startSearch(),
                  child: Text(_preparingSearch ? 'PRÉPARATION…' : 'CHERCHER'),
                ),
              ),
            ),
            Container(
              width: double.infinity,
              margin: const EdgeInsets.symmetric(horizontal: 14),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
              decoration: BoxDecoration(
                color: const Color(0xFF121722),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(_status, style: const TextStyle(fontSize: 13)),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 14),
                clipBehavior: Clip.antiAlias,
                decoration: BoxDecoration(
                  color: Colors.black,
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(color: const Color(0xFF29313D)),
                ),
                child: _cameraEnabled
                    ? Stack(
                        fit: StackFit.expand,
                        children: [
                          YOLOView(
                            modelPath: _modelPath,
                            task: YOLOTask.segment,
                            controller: _yoloController,
                            confidenceThreshold: 0.12,
                            iouThreshold: 0.7,
                            useGpu: true,
                            cameraResolution: '720p',
                            lensFacing: LensFacing.back,
                            onResult: _handleResults,
                            onModelLoad: (path, task) {
                              _yoloController.setShowOverlays(false);
                              if (!mounted) return;
                              setState(() {
                                _modelReady = true;
                                if (!_searching) _status = '✅ IA native et caméra prêtes.';
                              });
                            },
                            onModelError: (error, path, task) {
                              if (!mounted) return;
                              setState(() {
                                _modelReady = false;
                                _status = '❌ Erreur IA native : $error';
                              });
                            },
                          ),
                          IgnorePointer(
                            child: CustomPaint(
                              painter: TargetBoxPainter(_targetResult, _originalTarget),
                            ),
                          ),
                          if (!_modelReady)
                            const Align(
                              alignment: Alignment.center,
                              child: Card(
                                child: Padding(
                                  padding: EdgeInsets.all(12),
                                  child: Text('Chargement du modèle natif…'),
                                ),
                              ),
                            ),
                        ],
                      )
                    : const Center(child: Text('Caméra coupée')),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 10, 14, 14),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _toggleCamera,
                      icon: Icon(_cameraEnabled ? Icons.videocam_off : Icons.videocam),
                      label: Text(_cameraEnabled ? 'COUPER CAMÉRA' : 'CAMÉRA'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _stopSearch,
                      icon: const Icon(Icons.stop),
                      label: const Text('STOP'),
                    ),
                  ),
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

    final boxPaint = Paint()
      ..color = const Color(0xFF39FF88)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 5;
    canvas.drawRect(rect, boxPaint);

    final display = '$label ${(r.confidence * 100).round()}%';
    final painter = TextPainter(
      text: TextSpan(
        text: display,
        style: const TextStyle(
          color: Color(0xFF39FF88),
          fontWeight: FontWeight.w900,
          fontSize: 17,
          backgroundColor: Color(0xDD000000),
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout(maxWidth: size.width - 16);

    final dy = (rect.top - painter.height - 6).clamp(4.0, size.height - painter.height - 4);
    final dx = rect.left.clamp(4.0, (size.width - painter.width - 4).clamp(4.0, size.width));
    painter.paint(canvas, Offset(dx, dy));
  }

  @override
  bool shouldRepaint(covariant TargetBoxPainter oldDelegate) {
    return oldDelegate.result != result || oldDelegate.label != label;
  }
}
