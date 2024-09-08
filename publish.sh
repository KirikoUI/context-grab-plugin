#!/bin/bash

# Ensure we exit on any command failure
set -e

echo "Building plugin..."
./gradlew buildPlugin

echo "Signing plugin..."
./gradlew signPlugin

echo "Publishing plugin..."
./gradlew publishPlugin

echo "Plugin published successfully!"

# OR https://plugins.jetbrains.com/plugin/25283-context-grab/edit