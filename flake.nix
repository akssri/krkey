{
  description = "KrKey Android development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    # Locked separately so updating it for newer tools leaves the JDK/SDK alone
    nixpkgs-tools.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs =
    {
      self,
      nixpkgs,
      android-nixpkgs,
      nixpkgs-tools,
    }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfree = true;
      };
      toolsPkgs = nixpkgs-tools.legacyPackages.${system};

      android-composition = android-nixpkgs.sdk.${system} (
        sdkPkgs: with sdkPkgs; [
          cmdline-tools-latest
          build-tools-36-0-0
          platform-tools
          platforms-android-36
        ]
      );

    in
    {
      devShells.${system} = {
        # Main development environment - simple shell with Android tools
        default = pkgs.mkShell {
          buildInputs = with pkgs; [
            jdk21
            android-composition
            adb-sync
            scrcpy
            toolsPkgs.perfetto.tools # trace_processor_shell, traceconv
          ];

          shellHook = ''
            echo "🤖 KrKey development environment"

            # Set JAVA_HOME for Gradle
            export JAVA_HOME=${pkgs.jdk21}/lib/openjdk

            # Set Android SDK path
            export ANDROID_SDK_ROOT=${android-composition}/share/android-sdk
            export ANDROID_HOME=$ANDROID_SDK_ROOT

            # Add Android tools to PATH
            export PATH=$PATH:$ANDROID_SDK_ROOT/platform-tools
            export PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin
            export PATH=$PATH:$ANDROID_SDK_ROOT/build-tools/36.0.0

            # Gradle configuration
            export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_SDK_ROOT/build-tools/36.0.0/aapt2"

            # Generate local.properties from nix environment
            echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties

            echo "Java version: $(java -version 2>&1 | head -n1)"
            echo "✅ Environment ready!"
            echo "• JAVA_HOME: $JAVA_HOME"
            echo "• ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
            echo "• Available commands: ./gradlew (alias), adb, aapt2"
            echo ""
            echo "🚀 Quick start:"
            echo "  ./gradlew assembleDebug    # Build debug APK"
            echo "  ./gradlew installDebug     # Install to connected device"
            echo "  ./gradlew test             # Run tests"
            echo "  ./gradlew ktlintFormat     # Format code"
          '';
        };
      };
    };
}
