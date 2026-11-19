# Unlit·息幕 source conventions

- app/: conventional Android Java, AIDL, resources and unit tests. native/: input JNI C source. scripts/: portable build commands. Root Gradle files configure the project; README.md explains usage, build and compatibility; SOURCES.md and NOTICE retain attribution.
- Keep local.ken.foldguard as application identity for upgrades from FoldGuard. Visible branding is Unlit·息幕.
- Preserve Extinguish copyright, source references, GPL-3.0-or-later license, and Shizuku MIT notice. Mark adapted display-control source accurately.
- Before changes, define the requested behavior; change only the necessary files. Run the build, unit tests, Android lint and native warnings-as-errors using README.md instructions.
- Do not commit device/account identifiers, local paths, credentials, signing keys, build outputs, runtime logs or screenshots. Physical touch/key acceptance is separate from local test success.

- Keep the manual touch probe optional. Acquire the required input devices successfully before switching off the display.
