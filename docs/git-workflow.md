# Git Workflow

This document defines the basic Git workflow for the Disha-Rakshak project.

## Check Current Status

```bash
git status
```

Use this command before adding or committing changes. It shows modified, deleted, and untracked files.

## Add Changes

To add all changed files:

```bash
git add .
```

To add a specific file:

```bash
git add README.md
```

Example:

```bash
git add docs/imu-testing.md
```

## Commit Changes

Use a clear commit message that explains what changed.

```bash
git commit -m "Add MPU6050 unit test documentation"
```

## Push to GitHub

```bash
git push
```

## Common Workflow

A normal workflow should look like this:

```bash
git status
git add .
git commit -m "Describe the change clearly"
git push
```

## Good Commit Message Examples

```text
Initialize project structure
Add ESP32 and MPU6050 hardware setup
Add I2C scanner firmware
Document raw IMU test results
Add calibration and movement detection notes
Add foot-mounted ZUPT test plan
Update README with current project status
```

## Bad Commit Message Examples

```text
update
final
changes
stuff
working maybe
new
fix
```

## Suggested Branch Usage

For now, using only the `main` branch is acceptable.

Later, separate branches can be used for larger changes.

Suggested branch names:

```text
firmware-tests
esp-now-integration
path-visualization
report-documentation
zupt-tuning
hardware-testing
```

## Before Every Commit

Check:

* The code compiles
* The file names are correct
* The documentation matches the latest test result
* Serial monitor logs are saved if they are important
* No temporary files are committed
* No private keys, tokens, or credentials are committed

## Recommended Commit Frequency

Commit after each meaningful milestone.

Examples:

* After adding a working test sketch
* After documenting a successful test
* After fixing wiring documentation
* After adding ESP-NOW sender/receiver code
* After adding screenshots or serial logs
* After updating project architecture

## Current Project Workflow

The recommended workflow for this project is:

```text
Test hardware physically
        ↓
Save firmware sketch
        ↓
Save serial monitor output
        ↓
Update documentation
        ↓
Commit and push to GitHub
```

This keeps the project traceable and makes debugging easier.
