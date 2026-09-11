# Pont mobile Nico Alert <-> Nico OS local

Ce dossier remplace la dépendance Supabase de Nico Alert par un accès direct au Nico OS local du PC.

## Principe
- Nico OS reste la source de vérité dans `user_data/nico_os.db`.
- Nico OS continue d'écouter uniquement sur `127.0.0.1:8765`.
- `mobile_bridge.py` expose seulement trois opérations protégées par une clé : lire la prochaine tâche, terminer une tâche et créer la suivante.
- La base SQLite n'est jamais envoyée sur Internet.

## Démarrage
1. Lance Nico OS normalement.
2. Double-clique sur `DEMARRER_PONT_MOBILE.bat`.
3. La fenêtre affiche une adresse du type `http://192.168.1.42:8766` et une clé de liaison.
4. Dans Nico OS Alerte Android, ouvre la connexion locale et saisis ces deux valeurs.

Le téléphone et le PC doivent être sur le même réseau pour une adresse `192.168.x.x`.

## Accès hors de la maison
La V2 Android accepte n'importe quelle adresse HTTP privée. Pour un accès à distance, utiliser un réseau privé chiffré (par exemple Tailscale) puis saisir l'adresse privée du PC dans l'application. Ne pas ouvrir directement le port 8766 sur la box Internet.

## Sécurité
La clé est générée localement au premier lancement et enregistrée dans `mobile_bridge_config.json`. Ce fichier ne doit pas être publié ni envoyé à quelqu'un d'autre.
