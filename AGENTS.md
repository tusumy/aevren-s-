# Repository workflow

## Android APK releases

Whenever an APK is produced for the user:

1. Commit every source and asset change used by the build.
2. Push those commits to the repository's `main` branch before handing off the APK.
3. Read back the remote `main` tree and verify it matches the tracked source tree used for the build.
4. Build, test, and verify the signed APK from that committed tree.
5. Report the remote commit SHA together with the APK.

Never deliver an APK whose corresponding source and assets exist only in a local workspace.
