# Contributing to MultiPing

Thank you for your interest in contributing!

## How to contribute

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes
4. Test on a physical device (emulators may not support ICMP ping)
5. Submit a Pull Request

## Code style

- Kotlin idioms preferred
- Follow existing patterns in the codebase
- No new proprietary dependencies — F-Droid compatibility must be maintained

## Reporting bugs

Open an issue on GitHub with:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behaviour

## Translations

To add a new language:
1. Copy `app/src/main/res/values-en/strings.xml`
2. Rename folder to `values-XX` (ISO 639-1 code)
3. Translate all strings
4. Add the language to `MainActivity.kt` language dialog
