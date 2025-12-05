#!/bin/bash
set -e

# Run build inside the app module
./gradlew :app:clean :app:assembleDebug
