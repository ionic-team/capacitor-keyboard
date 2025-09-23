# Example app

This example app is used to develop and test new features and verify bug fixes for the Capacitor Keyboard plugin, present at the root of this repository.

It is built with [Ionic React](https://ionicframework.com/react). You should have [Ionic cli installed](https://ionicframework.com/docs/intro/cli).

## Testing

To setup the app

```bash
npm install
ionic build
ionic cap sync
```

Then, to run the app natively, you can open Android Studio / Xcode via `ionic cap open [android/ios]` or do `ionic cap run [android/ios]` to generate and deploy a new build.

To run just the web app, run `ionic serve`.