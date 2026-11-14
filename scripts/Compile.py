#!/usr/bin/env python3

import argparse
import os
import platform
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
SCRIPTS_DIR = ROOT_DIR / "scripts"
BUILD_DIR = ROOT_DIR / "build"

SUPPORTED_PLATFORMS = [
    "android",
    "windows",
    "linux",
    "macos",
    "ios",
    "web",
    "desktop",
    "all"
]

PLATFORM_TASKS = {
    "android": {
        "debug": "assembleDebug",
        "release": "assembleRelease"
    },
    "windows": {
        "debug": "packageDebugDistributionForCurrentOS",
        "release": "packageReleaseDistributionForCurrentOS"
    },
    "linux": {
        "debug": "packageDebugDistributionForCurrentOS",
        "release": "packageReleaseDistributionForCurrentOS"
    },
    "macos": {
        "debug": "packageDebugDistributionForCurrentOS",
        "release": "packageReleaseDistributionForCurrentOS"
    },
    "desktop": {
        "debug": "run",
        "release": "packageDistributionForCurrentOS"
    },
    "ios": {
        "debug": "linkDebugFrameworkIosArm64",
        "release": "linkReleaseFrameworkIosArm64"
    },
    "web": {
        "debug": "jsBrowserDevelopmentWebpack",
        "release": "jsBrowserProductionWebpack"
    }
}


class Logger:

    @staticmethod
    def info(message):
        print(f"[INFO] {message}")

    @staticmethod
    def success(message):
        print(f"[SUCCESS] {message}")

    @staticmethod
    def warning(message):
        print(f"[WARNING] {message}")

    @staticmethod
    def error(message):
        print(f"[ERROR] {message}")


class BuildSystem:

    def __init__(self, args):
        self.args = args
        self.system = platform.system().lower()
        self.start_time = time.time()
        self.gradle = self.find_gradle()

    def find_gradle(self):
        if self.system == "windows":
            wrapper = ROOT_DIR / "gradlew.bat"
        else:
            wrapper = ROOT_DIR / "gradlew"

        if wrapper.exists():
            if self.system == "windows":
                return [str(wrapper)]

            return ["sh", str(wrapper)]

        installed = shutil.which("gradle")

        if installed:
            return [installed]

        Logger.error("Gradle was not found.")
        Logger.error("Install Gradle or add the Gradle Wrapper.")
        sys.exit(1)

    def check_environment(self):
        Logger.info("Checking build environment...")

        Logger.info(f"Operating system: {platform.system()}")
        Logger.info(f"Architecture: {platform.machine()}")
        Logger.info(f"Python: {platform.python_version()}")

        java_home = os.environ.get("JAVA_HOME")

        if java_home:
            Logger.info(f"JAVA_HOME: {java_home}")
        else:
            Logger.warning("JAVA_HOME is not configured.")

        if not shutil.which("java"):
            Logger.warning("Java was not found in PATH.")

        android_home = (
            os.environ.get("ANDROID_HOME")
            or os.environ.get("ANDROID_SDK_ROOT")
        )

        if android_home:
            Logger.info(f"Android SDK: {android_home}")
        elif self.args.platform in ("android", "all"):
            Logger.warning("Android SDK environment variable is missing.")

        if self.args.platform == "ios" and self.system != "darwin":
            Logger.warning(
                "iOS builds generally require macOS and Xcode."
            )

        Logger.success("Environment check completed.")

    def execute(self, command):
        Logger.info("Executing: " + " ".join(command))

        try:
            result = subprocess.run(
                command,
                cwd=ROOT_DIR,
                check=False
            )

            if result.returncode != 0:
                Logger.error(
                    f"Command failed with exit code {result.returncode}."
                )
                return False

            return True

        except KeyboardInterrupt:
            Logger.warning("Build interrupted by user.")
            return False

        except OSError as error:
            Logger.error(str(error))
            return False

    def gradle_command(self, task):
        command = self.gradle + [task]

        if self.args.offline:
            command.append("--offline")

        if self.args.stacktrace:
            command.append("--stacktrace")

        if self.args.no_daemon:
            command.append("--no-daemon")

        if self.args.quiet:
            command.append("--quiet")
        else:
            command.append("--console=plain")

        if self.args.parallel:
            command.append("--parallel")

        return command

    def clean(self):
        Logger.info("Cleaning project...")
        return self.execute(self.gradle_command("clean"))

    def get_task(self, target):
        if self.args.task:
            return self.args.task

        tasks = PLATFORM_TASKS.get(target)

        if tasks is None:
            return None

        return tasks.get(self.args.build)

    def build_platform(self, target):
        Logger.info(f"Building target: {target}")
        Logger.info(f"Build type: {self.args.build}")

        task = self.get_task(target)

        if not task:
            Logger.error(f"No Gradle task defined for {target}.")
            return False

        if target in ("windows", "linux", "macos"):
            if target != self.system:
                Logger.error(
                    f"{target} packaging requires a compatible host."
                )
                return False

        return self.execute(self.gradle_command(task))

    def build_all(self):
        targets = [
            "android",
            "windows",
            "linux",
            "macos",
            "ios",
            "web"
        ]

        results = {}

        for target in targets:
            Logger.info(f"Processing {target}...")

            results[target] = self.build_platform(target)

        print()
        Logger.info("Build summary:")

        for target, success in results.items():
            status = "SUCCESS" if success else "FAILED"
            print(f"  {target}: {status}")

        return all(results.values())

    def list_artifacts(self):
        Logger.info("Searching for build artifacts...")

        extensions = {
            ".apk",
            ".aab",
            ".exe",
            ".msi",
            ".dmg",
            ".deb",
            ".rpm",
            ".jar",
            ".ipa",
            ".framework",
            ".zip"
        }

        found = []

        for directory in [
            ROOT_DIR / "app" / "build" / "outputs",
            ROOT_DIR / "build",
            ROOT_DIR / "composeApp" / "build"
        ]:
            if not directory.exists():
                continue

            for file in directory.rglob("*"):
                if file.is_file() and file.suffix.lower() in extensions:
                    found.append(file)

        if not found:
            Logger.warning("No build artifacts found.")
            return

        for file in sorted(set(found)):
            try:
                relative = file.relative_to(ROOT_DIR)
                size = file.stat().st_size / (1024 * 1024)

                Logger.success(
                    f"{relative} ({size:.2f} MB)"
                )
            except OSError:
                continue

    def run(self):
        print("=" * 55)
        print("V-CONVERTER BUILD SYSTEM")
        print("=" * 55)

        Logger.info(f"Project directory: {ROOT_DIR}")

        self.check_environment()

        if self.args.only_clean:
            success = self.clean()

        else:
            if self.args.clean:
                if not self.clean():
                    Logger.error("Cleaning failed.")
                    return 1

            if self.args.platform == "all":
                success = self.build_all()
            else:
                success = self.build_platform(self.args.platform)

        elapsed = time.time() - self.start_time

        print()
        print("=" * 55)

        if success:
            Logger.success("Build process completed successfully.")
            if not self.args.only_clean:
                self.list_artifacts()
        else:
            Logger.error("Build process failed.")

        Logger.info(f"Elapsed time: {elapsed:.2f} seconds")

        print("=" * 55)

        return 0 if success else 1


def parse_arguments():
    parser = argparse.ArgumentParser(
        description="V-Converter Cross-Platform Build System"
    )

    parser.add_argument(
        "--platform",
        choices=SUPPORTED_PLATFORMS,
        default="android",
        help="Target platform"
    )

    parser.add_argument(
        "--build",
        choices=["debug", "release"],
        default="debug",
        help="Build configuration"
    )

    parser.add_argument(
        "--task",
        type=str,
        help="Override the default Gradle task"
    )

    parser.add_argument(
        "--clean",
        action="store_true",
        help="Clean before building"
    )

    parser.add_argument(
        "--only-clean",
        action="store_true",
        help="Clean without building"
    )

    parser.add_argument(
        "--offline",
        action="store_true",
        help="Use Gradle offline mode"
    )

    parser.add_argument(
        "--stacktrace",
        action="store_true",
        help="Display Gradle stack traces"
    )

    parser.add_argument(
        "--no-daemon",
        action="store_true",
        help="Disable the Gradle daemon"
    )

    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Reduce Gradle output"
    )

    parser.add_argument(
        "--parallel",
        action="store_true",
        help="Enable parallel Gradle execution"
    )

    return parser.parse_args()


def main():
    args = parse_arguments()
    compiler = BuildSystem(args)
    return compiler.run()


if __name__ == "__main__":
    sys.exit(main())
