# //nullvariant

CODM Garena loader — self-contained, no license, no remote catalog.

- **Package:** `com.nullvariant.official`
- **Version:** 1.6.59
- **Assets:** bundled `libexternal.so` + `libloader64.so` + `libloader32.so`
- **Font:** MedievalSharp (`res/font/medieval.ttf`)
- **Icon / thumbnail / floating icon:** our own `nullvariant_*` assets

## Build

The APK is built by GitHub Actions (`.github/workflows/build.yml`).

- Push to `main` or run **Actions → Build APK → Run workflow**.
- Download the `nullvariant-apk` artifact from the run.

## Use

1. Install the APK.
2. Open the loader, tap **PREPARE** (extracts the bundled payload).
3. Tap **INJECT** while CODM is running (lobby is safest).
4. Non-root needs the loader and CODM in the same GSpace/virtual container; root enables the `-root` path.
