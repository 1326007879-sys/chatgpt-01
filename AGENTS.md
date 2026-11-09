# Android AI pet development

This is an independent Android project. Read README.md, docs/BUILD.md and docs/PROVENANCE.md first.
Use this checkout; cloud tasks are already isolated. Do not create extra worktrees unless asked.

- Kotlin application/tests are under android/. Python files under tools/asset_pipeline and scripts/ prepare assets only; they are not shipped as application code.
- Preserve user work, the upstream license and all character/audio notices. Never push to the upstream repository.
- Do not commit build outputs, local toolchains, logs, signing keys or model credentials.
- Before pushing code: run pytest, ruff, Android testDebugUnitTest and lintDebug. For packaging or integration changes also build both app and instrumentation APKs. Record device tests separately; compilation is not device acceptance.
- For lifecycle/concurrency changes use event-based regression tests and repeat affected tests under load. Do not relax assertions to make timing pass.
- Keep existing HTTP TLS verification, per-service credential isolation, cancellable requests, atomic history writes and storage error visibility.
- Work in feature branches; main is the project baseline. PR review precedes merge. Do not publish releases or cloud environments unless requested.
- Existing cloud platform settings live outside Git and must not be replaced merely by editing project metadata.

- Before planning an update or publishing a new version, read docs/NEXT_UPDATE.md and remind the user of pending requirements before choosing the update scope.
