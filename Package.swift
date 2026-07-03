// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "CapacitorKeyboard",
    platforms: [.iOS(.v16)],
    products: [
        .library(
            name: "CapacitorKeyboard",
            targets: ["KeyboardPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "9.0.0-alpha.5")
    ],
    targets: [
        .target(
            name: "KeyboardPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/KeyboardPlugin",
            publicHeadersPath: "include"),
        .testTarget(
            name: "KeyboardPluginTests",
            dependencies: ["KeyboardPlugin"],
            path: "ios/Tests/KeyboardPluginTests")
    ]
)
