# Contributing

Thanks for helping improve PDFMate. Before opening an issue or pull request, check existing discussions and keep reports free of private document data.

## Bug reports

Include the Android version, device/emulator model, app version or commit, steps to reproduce, and the expected and actual behavior. Attach a sanitized stack trace when available. Never attach a real PDF, password, personal information, or confidential document content to a public issue.

## Changes

1. Create a focused branch from the default branch.
2. Keep changes scoped to one behavior where practical.
3. Add or update tests for behavior changes.
4. Run the relevant checks before opening a pull request:

   ```sh
   ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:assembleDebug
   ```

5. Describe user-visible changes, verification performed, and any remaining limitations in the pull request.

Do not include secrets, signing keys, local SDK paths, generated build outputs, or user documents in a change. New dependencies should be justified and their license and maintenance status considered.
