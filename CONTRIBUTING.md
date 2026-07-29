# 👋 Contribution guidelines

This document describes how to contribute to browzomje-patches.

## 📖 Resources to help you get started

* [Issues](https://github.com/browzomje/browzomje-patches/issues) are where we keep track of bugs and feature requests
* [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) explains how the patcher works

## 🙏 Submitting a feature request

Features can be requested by opening an issue using the
[Feature request issue template](https://github.com/browzomje/browzomje-patches/issues/new?labels=Feature+request&template=feature_request.yml&title=feat%3A+).

## 🐞 Submitting a bug report

If you encounter a bug while using these patches, open an issue using the
[Bug report issue template](https://github.com/browzomje/browzomje-patches/issues/new?labels=Bug+report&template=bug_report.yml&title=bug%3A+).

Please include the app version you patched. Patches are pinned to specific versions of the
target app, and an issue on an unsupported version usually has a different cause than the same
symptom on a supported one.

## 🔎 Diagnosing a patch that does not work

Most reports come down to one of two things, and they are easy to tell apart:

1. **The patch did not attach.** The build output of the patcher prints one line per hook, e.g.
   `[morphe] Disable ads: agganciata a Lw12/d;-><init>`. A missing line means the fingerprint
   did not match the app version you used.
2. **The patch attached but does nothing at runtime.** Every hook logs to logcat under a single
   tag. Connect the device and run:

   ```bash
   adb logcat -s Morphe:V
   ```

   Each feature logs `hook attivo` the first time it runs, plus what it did or why it failed.
   The same summary is available on the device itself, in the Morphe settings screen under
   "Diagnostica".

Including that output in a bug report makes it dramatically faster to fix.

## 📝 How to contribute

1. Before contributing, it is recommended to open an issue to discuss your change
with the maintainers
2. Development happens on the `dev` branch. Fork the repository and create your branch from `dev`
3. Commit your changes
4. Submit a pull request to the `dev` branch of the repository and reference issues
that your pull request closes in the description of your pull request
5. Our team will review your pull request and provide feedback. Once your pull request is approved,
it will be merged into the `dev` branch and will be included in the next release

❤️ Thank you for considering contributing to browzomje-patches
