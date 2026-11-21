# Bobblehead Widget

An Android home-screen widget that looks like a sticker. You pick a photo of a person, and the app cuts out the head. You then choose a body from a catalog, the way you would pick an avatar, and the head is placed on it. Tap the widget and the head wobbles.

The catalog has 8 cartoon bodies with a clay-figurine look, all drawn in code: Cardigan, T-shirt, Suit, Dress, Hoodie, Basketball, Skirt and Football (seated, holding a ball). The hands and neck use a skin colour sampled from the cutout face.

- Only the figure shows. There is no card, panel or base, and the background is transparent.
- Built with Kotlin and Jetpack Glance. Needs Android 12 or later. Made with Nothing OS 5 in mind.
- Everything runs on the phone. ML Kit face detection and selfie segmentation are bundled in the app, so no photos leave the device.
- No storage permission. The system Photo Picker gives the app access to the one photo you choose.

## Build without Android Studio (GitHub Actions)

GitHub builds the APK in the cloud, so you need nothing on your PC except git and a free GitHub account.

1. Create an empty repository on github.com. Click **New repository**, name it `bobblehead-widget`, and leave all the "initialize" boxes unticked.
2. Push this folder. Run these commands from inside `bobblehead-widget`:
   ```sh
   git init -b main
   git add .
   git commit -m "Bobblehead widget"
   git remote add origin https://github.com/<your-user>/bobblehead-widget.git
   git push -u origin main
   ```
   Git asks you to sign in to GitHub in a browser the first time.
3. On GitHub, open the repository, then the **Actions** tab, then the **Build APK** run. The build takes about 5–8 minutes. A green tick means it worked.
4. Open the finished run and scroll to **Artifacts**. Download **bobblehead-debug-apk**. It is a zip file that contains `app-debug.apk`.
   - A red cross means the build failed. Open the failed step and copy the error text.
   - You can start a build by hand any time from **Actions** > **Build APK** > **Run workflow**.

## Install on the phone

1. Copy `app-debug.apk` to the phone. Use a USB cable, Google Drive, or email it to yourself.
2. On the phone, tap the APK in Files. Allow **Install unknown apps** for that app when Android asks.
3. Play Protect may warn about an unknown developer. Tap **Install anyway**. This is normal for a self-built debug app.

Each CI build is signed with a new debug key. To install a newer build, **uninstall the old app first**. Uninstalling removes your placed widgets, so you will need to add them again.

## Add the widget

1. Long-press an empty spot on the home screen. Tap **Widgets**, find **Bobblehead**, and drag it onto the home screen.
2. The setup screen opens. Tap **Pick photo** and choose a photo of a person.
3. The head is cut out automatically. Tap the preview to test the shake.
   - If the automatic cutout fails, or you want to fix it, use **Adjust manually**. Drag the oval over the head, set its size with the slider, and tap **Use this crop**.
4. Under **Choose a body**, tap a body. Each thumbnail already shows your head on that body.
5. Tap **Done**.
6. To resize the widget, long-press it and drag the handles. The minimum size is about 2×2 cells.

Tap the widget to make the head shake.

## Change the photo or body

Use any of these:

- **Long-press the widget** and choose the settings or reconfigure option. The label depends on the launcher.
- **Open the Bobblehead app icon.** It opens the same setup screen. If you have more than one Bobblehead widget, it asks which one to change.
- A widget without a photo opens the setup screen when you tap it.

## How it works

| Part | File |
|---|---|
| Widget UI (Glance), per-widget state keys, receiver | `app/src/main/java/dev/bobble/widget/BobbleWidget.kt` |
| Tap action, shake controller, lite-mode fallback | `ShakeAction.kt` |
| Shake angles, easing, rotation math (unit-tested) | `ShakeMath.kt` |
| Head and body placement, neck pivot constants (unit-tested) | `FigureLayout.kt` |
| Frame drawing, head and body caches | `FrameRenderer.kt` |
| Head cutout (ML Kit), manual oval fallback, photo decoding | `HeadCutter.kt` |
| White sticker outline, bitmap helpers | `StickerOutline.kt` |
| Body catalog: ids, names, size ratios, neck pivots (unit-tested) | `BodyCatalog.kt` |
| Standing bodies, with outfits defined as data | `StandingBodyPainter.kt` |
| Seated football body, clay shading helpers, skin-tone sampling | `BodyPainter.kt` |
| Saved head PNGs (`files/heads/`) | `HeadStore.kt` |
| Setup screen (Compose) | `ConfigActivity.kt` |
| Widget definition: size, resize, configure, reconfigurable | `app/src/main/res/xml/bobble_widget_info.xml` |

### Head cutout

The cutout is a plain cut-and-paste. The face's pixels are never recoloured, filtered or stylized. Only the background is made transparent.

1. Face detection finds the largest face in the photo.
2. The face box is enlarged generously so hair and ears are kept.
3. Selfie segmentation removes the background. Just below the chin, the cut fades out over a short distance so the neck and shoulders are not included.
4. The result is trimmed and limited to 512 px, then saved as a transparent PNG in app-private storage.
5. A white sticker outline is optional. Turn on **White outline around head** in setup. It is off by default.

If no face is found or the mask is empty, the setup screen switches to the manual oval crop.

### Composition

The head is about 58% of the figure's height. Its bottom edge overlaps the body just below the neck pivot. There is no spring. The neck pivot is set by `FigureLayout.NECK_PIVOT_X` and `NECK_PIVOT_Y`, given as fractions of the body image.

### Add a body to the catalog

1. In `StandingBodyPainter.OUTFITS`, add an `Outfit`. Set the top colour, sleeve length, bottom type (pants, shorts, skirt or dress) and colours, and the shoes. You can also add a jacket, buttons, tie, hood, socks or jersey number.
2. In `BodyCatalog.ALL`, add a `BodySpec` with the same id and a display name.

The new body appears in the picker automatically. The unit tests check that the head stays inside the frame on every catalog body.

### Shake

A tap runs `ShakeAction`, which pushes one widget update per frame:

- **Normal mode:** 12 frames, one every 75 ms, about 0.9 s in total. Angles follow `0 → +14° → −10° → +7° → −4° → +2° → 0°` with eased steps. Each frame is drawn when it is needed at 300×300 px, so the whole sequence is never in memory at once. The last frame is always upright, even if the animation is interrupted.
- **Fallback mode:** if at least half the frames arrive late, the launcher is probably slowing down updates. Later shakes then use 8 pre-rendered 240 px frames, one every 125 ms. Picking a new photo switches back to normal mode.
- **Double taps:** a tap during a shake is ignored.

### Saved state and restarts

The head file path, chosen body, current angle and frame index are stored in Glance's per-widget DataStore. The PNG is in app-private storage. Both survive a reboot, and the widget redraws itself on boot. Removing a widget deletes its PNG.

## Known limits

- **Animation smoothness depends on the launcher.** Widgets are updated through RemoteViews, which is not meant for animation, and some launchers slow these updates down. If the shake looks jerky, the app switches to fallback mode by itself.
- **Frame size.** Frames are kept at 300 px or smaller because of RemoteViews bitmap limits. On a very large widget the image is scaled up and looks a little soft.
- **Cutout quality.** Selfie segmentation is tuned for one person facing the camera. Group photos, strong side profiles, hats or busy backgrounds may need the manual oval. Hair that goes beyond the padded head box is cut off.
- **Updating the app.** Each CI build has a new debug signature, so you must uninstall the old app before installing a new build.
- **Re-pick option on long-press.** Some launchers may not show "reconfigure" in the long-press menu. Use the app icon instead.
- **App size.** The bundled ML Kit models add roughly 10–20 MB.
- **Not tested on a real device.** This project was written without a build environment. Its first real build is the GitHub Actions run.

## Tests

### Unit tests (run automatically in CI)

`app/src/test/java/dev/bobble/widget/`:

- `ShakeMathTest`
  - 12 frames, last frame exactly 0°, every keyframe hit (+14, −10, +7, −4, +2)
  - Swings get smaller and change direction each time
  - No angle beyond 14°
  - Total time between 800 and 1000 ms, each frame 70–80 ms
  - Easing curve, rotation about the pivot (pivot stays fixed, distance is kept, positive angles turn clockwise on screen)
  - Rule for switching to fallback mode
- `FigureLayoutTest`
  - Head is 55–60% of the figure's height
  - Head bottom overlaps the body below the pivot, and the head is centered on the pivot
  - The upright figure fits the frame
  - All four head corners stay inside the frame at ±14° for head shapes from narrow to wide
  - The layout scales evenly with frame size

The **unit-test-reports** artifact holds the HTML report.

### Manual test checklist (on the phone)

1. Add the widget. The setup screen opens. Back out without picking a photo: the widget is not added.
2. Pick a clear selfie. The cutout has a white outline, the background is gone, and the hair is mostly kept. Tapping the preview shakes the head.
3. Tap **Done**. The widget shows only the figure on the wallpaper, with no card behind it.
4. Tap the widget. The head wobbles for about 1 second around the neck and ends upright.
5. Tap quickly several times. Only one wobble plays, and it still ends upright.
6. Pick a photo with no face, such as a landscape. The manual oval appears. Drag it, resize it, and tap **Use this crop**.
7. Resize the widget to 2×2, 3×3 and 4×2. The figure stays centered and is not cut off.
8. Reboot the phone. The widget still shows the same head.
9. Change the photo by long-pressing the widget and choosing reconfigure. Also try it from the app icon.
10. Add two widgets with different photos and different bodies. Each keeps its own head and body. Remove one, and the other is not affected.
11. Tap every body in the catalog. The preview changes, the head sits on the neck of each one, and the hand colour roughly matches the face.
12. Switch between dark and light mode. The widget background stays transparent.
