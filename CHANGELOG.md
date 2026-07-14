### 1.3-beta2 (26.11.2022)

* only show "load screenshot" button when app has screenshots
* fix toolbar layout when rotating to a different orientation than on app startup

### 1.3-beta1 (26.11.2022)

* Minimum supported Android version is now 5.1
* add an option to follow system theme (new default)
* add night mode dark splash screen for Android 12+
* opt into Android 13 per-app language selection
* fix app language selector (on Android 13+ it should be done through system settings)
* update dependencies and translations

### 1.2 (28.05.2022)

**NOTE:** F-Droid Classic 1.2 will be the last version to support Android 4.4

* Improve accessibility of the 'add repo' speed dial
* remove leftover "write external storage" permission
* Never show cryptocurrency donation options
* Fix a crash when trying to uninstall system apps from the version list of an app
* Translation updates

### 1.2-rc1 (23.05.2022)

**NOTE:** F-Droid Classic 1.2 will be the last version to support Android 4.4

* Add (disabled) NewPipe and Briar repos for new installs
* Switch new installs to using ftp.fau.de F-Droid mirrors instead of slow f-droid.org
* Enable Pull-to-refresh on app list and repo details screen
* Add a setting to disable all pull to refresh handling
* Suppress speaking "App Icon" in app list via talkback
* Update dependencies and translations

### 1.2-beta2 (18.07.2021)

* make it possible to scan repo QR codes from repositories screen
* open media files from F-Droid repos in a browser instead of trying to "install" them somewhere
* only show repo force-update options in expert mode
* add swipe to refresh handling in the repo screen
* fix bug where repo list didn't refresh after initial repo updating
* allow uninstalling updates of system apps
* make tab titles somewhat work again (See Bubu/fdroidclassic#23)
* link to correct repository from About screen
* make app name not translatable
* update dependencies and translations

### 1.2-beta1 (20.05.2021)

* fix install button visibility for launcher apps
* fix a potential crash related to repo categories
* fix long app names overflowing into version number in app list
* fix some overflows in app details version list
* Fix user agent
* update dependencies and translations

### 1.1 (07.02.2021)

* Translation updates

### 1.1-rc1 (02.02.2021)

* Add option to only load screenshots on demand
* Fix screenshot placeholder image not being visible when using light theme
* Fix annoying downloader crash, again (#52)
* Remove ACCESS_WIFI_STATE permission
* Dependency and translation updates

### 1.1-beta4 (29.12.2020)

* Fix a bunch of crashes that have been reported since the last beta
* Some donation option tweaks
* Translation and dependency updates

### 1.1-beta3 (17.9.2020)

* Display Screenshots!
* Add missing Liberapay and Open Collective donation options
* Show translations links for apps that have them specified
* Disable the F-Droid Classic repo by default for new installs

### 1.1-beta2 (15.9.2020)

* Fix Index update being completely broken on Android < 7 introduced in 1.1-beta1

### 1.1-beta1 (14.9.2020)

* Implement repo force refresh and db reset options (#33)
* Add option to update a single repo from the repository details
* View apps from a single repo by selecting the repository from the category spinner
* Bug fixes and translation improvements
* Fixed translations preferring secondary locale over primary (thanks to @spacecowboy)

### 1.0 (10.09.2020)

* Fix app title in all languages

### 1.0-rc2 (06.09.2020)

* Fix installing upgrades when there are multiple apks signed by different keys (reproducible builds)
* Fix fdroidrepos:// URIs sometimes not being recognized when adding a new repo
* Add backup config (don't try to backup cached apks)
* Add network security config (force always using https for some well-known urls)

### 1.0-rc1 (05.09.2020)

* Translation updates (See https://weblate.bubu1.eu/projects/f-droid-classic/)
* Update dependencies

### 1.0-beta11 (26.06.2020)

* Change ranking in search results to prefer name and summary matches (thanks to @gcbrown76)
* Return recently updated list to original functionality (don't include newly added in the list)
* Fix a crash when attempting to change the password for a password protected repo
* Add an option to open the system app-info settings page for installed apps from F-Droid classic. Either use the menu entry or long-press the app-icon
* Lots of internal code cleanup and dependency upgrades (thanks @TacoTheDank)

### 1.0-beta10 (04.06.2020)

* Slightly prettier About dialog
* Theme changes apply instantly and not only on back button.
* Add an "Update all" button to the menu, this might still be a bit buggy.
* The download cancel button is now visible in night mode. It's also slightly bigger and has a ripple effect now.
* Fix crash on phone boot
* Don't autoverify intent filters (https://developer.android.com/training/app-links/verify-site-associations), we're fine with an app choser dialouge.

### 1.0-beta9 (24.05.2020)

* lots of improvements to AppDetails view. (658078058106ea868c7f90f675214ff120b6af3d for details)
* add whatsnew section
* Night Theme will now actually have a black background
* use the new F-Droid button style

### 1.0-beta8 (24.05.2020)

* fix crash on install when no privext is installed (introduced in beta7)
* Remove buggy appicon transition

### 1.0-beta7 (24.05.2020)

* Fix a bug where removing a repository without disabling it first would get the app in an inconsistent state.(#29)
* Switch from proguard to d8 for optimizing the apk. This should hopefully not cause any user visible changes.
* Add FileInstaller for installing non-apk files
* Add a list of supported privileged extension packageids. Still need figure out a release process for these.
* Fix a crash when uninstalling an app. (#25)

### 1.0-beta6 (12.05.2020)

* Display app version codes in expert mode (#22)
* Don't show uninstall button for apps that aren't uninstallable (i.e. system apps)
* Some code cleanup and fixes around format string usage. Fixes uninstall error message and makes other messages better translatable.

### 1.0-beta5 (11.04.2020)

* fix using too much storage space (#24)

### 1.0-beta4 (06.03.2020)

* fix a crash that was sometimes happening when updating apps

### 1.0-beta3 (26.02.2020)

* fix upgrade button sometimes not showing (#11)
* fix repo update button not working in repo list
* fix performance issues on startup/repo update (#19)
  This disables the number of installed apps/number of updates updating for now.

### 1.0-beta2 (25.02.2020)

* backport repository share option
* upgrade to AndroidX, AGP 3.6
* some resource and dependency cleanup

### 1.0-beta1 (25.02.2020)

* support index-v1 format
* removed all swap functionality
* upgrade all libraries to latest versions
* bump Android targetSDK, compileSDK, and minSDK
* remove a lot of workarounds for older android versions
* New Icon!
* some fixes for icons not loading

See [F-Droid Changelog](https://gitlab.com/fdroid/fdroidclient/-/blob/master/CHANGELOG.md#01023-2017-04-01) before 0.102.3 for previous changes to codebase.
