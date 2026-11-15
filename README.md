# Device Care

A parental-control helper for Android. It turns off messages, search and sign-in screens in the apps you choose (Instagram, TikTok, Pinterest by default), blocks the same sites in browsers, and on a "managed" phone it also hides browsers and locks accounts.

- No internet permission. Nothing leaves the phone.
- Password-protected settings.
- Written for Android 8+ (aimed at a Sony Xperia 1 IV). **This code has not been compiled or run yet.** Expect to fix a few build errors and tune the rules on first use.

**Important:** the screen rules in `Rules.kt` are a first guess. Instagram, TikTok and Pinterest change their screens often and TikTok hides many labels. Expect to tune the rules once with the Debug recording (see step 6).

---

## 1. Build the APK

### Option A: Android Studio (Windows)
1. Install Android Studio from developer.android.com/studio.
2. Unzip this project, then File > Open and pick the `DeviceCare` folder. Let Gradle sync (first time downloads a lot).
3. If it offers to upgrade the Android Gradle Plugin, choose "Remind me later".
4. Build > Build Bundle(s) / APK(s) > Build APK(s).
5. The APK is at `app\build\outputs\apk\debug\app-debug.apk`.

### Option B: GitHub (no installs)
1. Create a free private repository at github.com.
2. Upload every file in this folder (drag the contents in, keep the `.github` folder).
3. Open the Actions tab > "Build APK" > Run workflow.
4. When it finishes, open the run and download the `DeviceCare-apk` artifact. Unzip it to get `app-debug.apk`.

## 2. Install on your phone (Windows + USB)

1. Phone: Settings > About phone > tap Build number 7 times, then Settings > System > Developer options > USB debugging ON. Plug in USB and accept the prompt.
2. Download "SDK Platform-Tools for Windows" from developer.android.com/tools/releases/platform-tools and unzip.
3. In that folder, hold Shift, right-click > Open PowerShell here, then:

```
.\adb devices
.\adb install -r path\to\app-debug.apk
.\adb shell pm grant com.devicecare.app android.permission.WRITE_SECURE_SETTINGS
```

The `pm grant` line lets the app switch its own protection engine on and re-enable it if someone turns it off.

4. Open **Device Care** on the phone, create a password, then tap **Turn on protection engine**.
   (If that button opens Settings instead, go to Settings > Accessibility > Installed apps > Device Care > On. On Android 13+ you may need App info > three dots > Allow restricted settings first.)

## 3. Test on your own phone first

Your own phone already has accounts, so **managed mode is not possible there**. You can still test the screen blocking:

1. In Device Care, set Instagram / TikTok / Pinterest to "DMs & search".
2. Open each app and try the messages tab, search, and the add-account screen. You should see a dark "turned off" card and get sent back.
3. If something is not caught, or the wrong thing is blocked, do step 6.
4. To pause while you use the apps normally: Pause protection, or set the app to Off.

## 4. Managed mode for the child's phone

Managed mode (device owner) is what stops the child from uninstalling Device Care, adding other users, sideloading apps, booting safe mode, or using browsers.

1. Factory reset the phone. In setup, **skip adding any Google account** (Android refuses device-owner setup once accounts exist).
2. Enable USB debugging, then:

```
.\adb install -r app-debug.apk
.\adb shell dpm set-device-owner com.devicecare.app/.AdminReceiver
.\adb shell pm grant com.devicecare.app android.permission.WRITE_SECURE_SETTINGS
```

3. Open Device Care, create the password, turn on the protection engine.
4. Now add the child's Google account, install the apps, and log in to Instagram / TikTok / Pinterest yourself.
5. Back in Device Care: switch **Lock phone accounts** on. Browsers are hidden by default.
6. **Family Link is likely not compatible with a device-owner phone.** Use Play Store parental controls (Play Store > Settings > Family > Parental controls) and Microsoft Family Safety for time limits instead. Test before relying on it.

To undo everything: open Device Care > Release managed mode. If you ever lose the password, a recovery-mode factory reset is the fallback.

## 5. How it works (honest limits)

- An accessibility service reads the labels on screen in the chosen apps and presses Back/Home when it sees a messages, search or sign-in screen.
- It reacts after the screen appears, so you may see a brief flash of the screen, covered by the dark card.
- The web check reads the browser address bar. Chrome sometimes shows only the domain, so on a managed phone **hiding browsers is the reliable method**.
- In-app browsers (links opened inside Instagram etc.) are not covered.
- A phone that is not in managed mode can have the service turned off in Settings. The watchdog switches it back on within a moment if you ran the `pm grant` command.

## 6. Tuning the rules (Debug recording)

1. Device Care > Tools > Debug recording ON.
2. Open Instagram and visit: home, Messages/inbox, a chat, search, add-account screen. Do the same for TikTok and Pinterest.
3. Turn Debug recording OFF, then on the PC:

```
.\adb shell run-as com.devicecare.app cat files/diag.txt > diag.txt
```

4. Send `diag.txt` to Claude. It lists the on-screen labels and ids for each screen, which is what the rules in `Rules.kt` are written from. (It contains whatever text was on screen, including chat names and message previews, so record on a test account if that matters.)

`run-as` only works on the debug build, which is what both build options produce.

## 7. Changing the name or icon

- Name: `app/src/main/res/values/strings.xml` (`app_name`).
- Icon: `app/src/main/res/drawable/ic_launcher_foreground.xml` and `values/colors.xml`.
