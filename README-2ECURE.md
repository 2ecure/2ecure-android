# 2ecure Messenger for Android

This repository is **2ecure Messenger**, a rebranded fork of
[Element X Android](https://github.com/element-hq/element-x-android) for the private,
invite-only Matrix service at [2ecure.com](https://2ecure.com).

- Upstream base: tag `v26.09.2` (commit `477c813`), imported unmodified as the first commit.
- Every later commit is a 2ecure change (identifiers, service lock-in, push gateway, privacy defaults,
  branding). They are maintained as a patch series in the 2ecure-messenger repository
  (`mobile/android/patches`).
- Licence: **AGPL-3.0-only**, as offered by upstream (see `LICENSE`). This repository is the
  Corresponding Source for the 2ecure Android app.
- 2ecure is not affiliated with Element Creations Ltd or the Matrix.org Foundation.
  "Element" and "Matrix" are trademarks of their respective owners.

Builds: `.github/workflows/2ecure-android-release.yml` (GitHub Ubuntu runner, JDK 21): a signed release APK
and App Bundle. Upstream's workflows, Dependabot configuration and Git LFS attributes are removed.
