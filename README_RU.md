
# AI HAND MASKS — Android starter project

This is an Android Studio/Gradle source project intended for cloud building with GitHub Actions.
It uses CameraX and MediaPipe Tasks Vision, and includes seven selectable modes.

## Build without installing Android Studio on your computer
1. Create/sign in to a GitHub account in your browser.
2. Create a new repository named `AI_HAND_MASKS`.
3. Upload the contents of this ZIP (not the ZIP itself) to the repository.
4. Open the repository's **Actions** tab.
5. Select **Build AI HAND MASKS APK** and press **Run workflow**.
6. When the workflow finishes, open the run and download the artifact `AI_HAND_MASKS-debug-apk`.
7. Extract the artifact ZIP; install `app-debug.apk` on the Android phone. If Android asks, allow installation from that browser/file manager.

## Important limitations
- This is a starter port, not a verified release APK. It must be successfully built and tested on a real Samsung/Redmi device.
- The original desktop mouse-control mode cannot control a PC mouse from Android; mode 7 shows a gesture cursor/ pinch indicator inside the app.
- Mode 6 is a basic hand-following snake prototype.
- Face mesh rendering is an approximation based on landmark connections, not the original OpenCV tessellation.
- The GitHub Actions workflow uses the installed Gradle command and may need adjustment if the hosted runner changes.
