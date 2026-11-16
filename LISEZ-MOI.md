# Mes œufs — application Android

## Option 1 : sans rien installer (GitHub, ~5 min)
1. Crée un compte gratuit sur https://github.com
2. Clique sur « + » → « New repository », nomme-le `mes-oeufs`, coche « Public », crée-le.
3. Clique sur « uploading an existing file » et glisse TOUT le contenu de ce dossier
   (y compris le dossier `.github` — sur PC, active l'affichage des fichiers cachés).
4. Clique « Commit changes ». La compilation démarre toute seule (onglet « Actions », ~3 min).
5. Sur ton téléphone, va dans ton dépôt → « Releases » → touche `MesOeufs.apk`.
6. Ouvre le fichier et autorise « Installer des applis inconnues » si Android le demande.

## Option 2 : avec Android Studio (sur PC)
1. Installe Android Studio : https://developer.android.com/studio
2. « Open » → choisis ce dossier, laisse la synchro Gradle se finir.
3. Menu Build → Build App Bundle(s) / APK(s) → Build APK(s).
4. L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`. Copie-le sur ton téléphone.

## Bon à savoir
- Les données sont enregistrées sur le téléphone (pas dans le cloud).
  Désinstaller l'appli efface les données → fais un « Copier en CSV » de temps en temps.
- Pour modifier l'appli : tout est dans `app/src/main/assets/index.html`.
