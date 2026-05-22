# AGENTS.md

## Project

CyanBridge is an Android application for smart glasses and voice assistant integration.

## Main architecture principles

- Do not rewrite the whole application unless explicitly requested.
- Keep SDK integration isolated from UI and business logic.
- Keep the voice pipeline modular.
- Keep device sync, audio recording, STT, LLM, TTS, media sync, local storage and plugin layer separated.
- Prefer small, reviewable changes.
- Do not remove existing functionality without explaining why.

## Build commands

Before finishing a coding task, run:

./gradlew assembleDebug
./gradlew test

If a command fails, explain the exact error and the file that caused it.

## Git rules

- Work only in the current branch.
- Do not commit secrets.
- Do not commit local IDE/cache files.
- Before finishing, show changed files, summary of changes and build/test result.

## Security rules

- Never store API keys in source code.
- Never commit tokens, passwords, private keys, or local environment files.
- Keep sensitive configuration in local properties or environment variables.

## Android rules

- Respect Android lifecycle.
- Avoid blocking the main thread.
- Keep permissions explicit.
- Handle Bluetooth and device connection errors gracefully.
- Use fake/mock implementations when real SDK integration is not ready.
