# Screenshots

Taken by the client gametest, not by hand, so they always show the current UI.

1. `JAVA_HOME=/opt/homebrew/opt/openjdk ./gradlew runClientGameTest`
   (the `showcase()` step stages the chat at GUI scale 3 on a 1080p window)
2. Crop the chat corner into this folder:

   ```sh
   python3 - <<'EOF'
   import glob
   from PIL import Image
   S = 'build/run/clientGameTest/screenshots'
   for n in ['tabs', 'all', 'system', 'dm']:
       f = glob.glob(f'{S}/*_showcase-{n}.png')[0]
       Image.open(f).crop((0, 540, 1100, 1080)).save(f'docs/screenshots/{n}.png', optimize=True)
   Image.open(glob.glob(f'{S}/*_showcase-settings.png')[0]).save('docs/screenshots/settings.png', optimize=True)
   EOF
   ```
3. After pushing, run the *Upload screenshots to Modrinth gallery* workflow on GitHub.
