# PixelBridge task runner. Run `make` or `make help` for the target list.
# Override any variable on the CLI, e.g. `make install SERIAL=emulator-5554`.
SHELL := bash
.DEFAULT_GOAL := help

# --- Config ---
GRADLE       ?= ./gradlew
MODULE       ?= :watch
PKG          ?= net.farcaster.pixelbridge
DEBUG_APK    ?= watch/build/outputs/apk/debug/watch-debug.apk
ADB          ?= adb
ANDROID_HOME ?= $(HOME)/Library/Android/sdk
EMULATOR     ?= $(ANDROID_HOME)/emulator/emulator
AVD          ?= WearOS51
AVD_IMAGE    ?= system-images;android-35-ext15;android-wear;arm64-v8a
SDKMANAGER   ?= sdkmanager

# adb target selector: set SERIAL=emulator-5554 (or ip:port) to disambiguate devices
SERIAL ?=
ADB_T  := $(ADB) $(if $(SERIAL),-s $(SERIAL),)

# `make inject` sample notification (override: make inject TITLE=Hi BODY=there APP=Signal ID=7)
APP   ?= Signal
TITLE ?= Alice
BODY  ?= Coffee later?
ID    ?= 42
OUT   ?= screenshot.png

.PHONY: help build release-apk clean test test-ui lint check verify \
        install run uninstall inject dismiss screenshot logcat \
        emulator emulator-setup docs-check tag release

help: ## Show this help
	@awk 'BEGIN{FS=":.*## "} /^[a-zA-Z0-9_-]+:.*## /{printf "  \033[36m%-15s\033[0m %s\n",$$1,$$2}' $(MAKEFILE_LIST)

# ---- Build ----
build: ## Build the debug APK
	$(GRADLE) $(MODULE):assembleDebug

release-apk: ## Build the (unsigned) release APK
	$(GRADLE) $(MODULE):assembleRelease

clean: ## Remove build outputs
	$(GRADLE) clean

# ---- Quality ----
test: ## Run JVM unit tests
	$(GRADLE) $(MODULE):testDebugUnitTest

test-ui: ## Run instrumented Compose UI tests (needs a device/emulator)
	$(GRADLE) $(MODULE):connectedDebugAndroidTest

lint: ## Run Android lint
	$(GRADLE) $(MODULE):lintDebug

check: lint test ## Lint + unit tests

verify: ## Clean build + lint + unit tests (pre-release gate)
	$(GRADLE) clean $(MODULE):lintDebug $(MODULE):testDebugUnitTest $(MODULE):assembleDebug

# ---- Device / run ----
install: build ## Install the debug APK to the connected device/emulator
	$(ADB_T) install -r $(DEBUG_APK)

run: install ## Install and launch the app
	$(ADB_T) shell am start -n $(PKG)/.ui.MainActivity

uninstall: ## Uninstall the app
	$(ADB_T) uninstall $(PKG)

inject: ## Inject a test notification (TITLE=.. BODY=.. APP=.. ID=..); debug builds only
	$(ADB_T) shell am broadcast -a $(PKG).INJECT -n $(PKG)/.debug.DebugInjectReceiver \
		--es app "$(APP)" --es title "$(TITLE)" --es body "$(BODY)" --el id $(ID)

dismiss: ## Dismiss an injected notification (ID=..)
	$(ADB_T) shell am broadcast -a $(PKG).INJECT -n $(PKG)/.debug.DebugInjectReceiver --el del $(ID)

screenshot: ## Capture a screenshot to $(OUT)
	$(ADB_T) shell screencap -p /sdcard/pb_shot.png
	$(ADB_T) pull /sdcard/pb_shot.png $(OUT)

logcat: ## Tail the app's BLE + notification logs
	$(ADB_T) logcat 'BleController:V' 'NusGattServer:V' 'NusAdvertiser:V' 'DebugInject:V' '*:S'

# ---- Emulator ----
emulator: ## Boot the Wear OS emulator ($(AVD)) headless, in the background
	$(EMULATOR) -avd $(AVD) -no-window -no-audio -no-boot-anim &

emulator-setup: ## One-time: install the Wear OS 5.1 image and create the $(AVD) AVD
	ANDROID_HOME=$(ANDROID_HOME) ANDROID_SDK_ROOT=$(ANDROID_HOME) $(SDKMANAGER) --sdk_root=$(ANDROID_HOME) "cmdline-tools;latest" "$(AVD_IMAGE)" "platforms;android-35"
	ANDROID_HOME=$(ANDROID_HOME) ANDROID_SDK_ROOT=$(ANDROID_HOME) $(ANDROID_HOME)/cmdline-tools/latest/bin/avdmanager create avd -n $(AVD) -k "$(AVD_IMAGE)" --device wearos_small_round --force

# ---- Release / deploy ----
docs-check: ## Pre-release docs checklist
	@echo "Pre-release doc check — confirm these are current for the release:"
	@echo "  [ ] README.md          — features, setup steps, anything user-visible"
	@echo "  [ ] CHANGELOG.md       — a new version entry"
	@echo "  [ ] docs/*             — reflect any changed behavior"
	@echo "  [ ] versionName in watch/build.gradle.kts matches the release tag"

tag: ## Create and push a git tag (VERSION=x.y.z)
	@test -n "$(VERSION)" || { echo "VERSION required, e.g. make tag VERSION=0.1.1"; exit 1; }
	git rev-parse -q --verify refs/tags/v$(VERSION) >/dev/null || git tag -a v$(VERSION) -m "PixelBridge v$(VERSION)"
	git push origin v$(VERSION)

release: ## Cut a release: verify -> build -> tag -> push -> publish APK as a GitHub release (VERSION=x.y.z)
	@test -n "$(VERSION)" || { echo "VERSION required, e.g. make release VERSION=0.1.1"; exit 1; }
	@git diff --quiet || { echo "ERROR: working tree not clean — commit first"; exit 1; }
	@test "$$(git rev-parse --abbrev-ref HEAD)" = "main" || { echo "ERROR: not on main"; exit 1; }
	@grep -q 'versionName = "$(VERSION)"' watch/build.gradle.kts || { echo "ERROR: versionName in build.gradle.kts != $(VERSION)"; exit 1; }
	@$(MAKE) --no-print-directory docs-check
	$(GRADLE) $(MODULE):testDebugUnitTest $(MODULE):assembleDebug
	git rev-parse -q --verify refs/tags/v$(VERSION) >/dev/null || git tag -a v$(VERSION) -m "PixelBridge v$(VERSION)"
	git push origin main --follow-tags
	bash scripts/github-release.sh $(VERSION) $(DEBUG_APK)
