# Contributing to Phantom Crowd

Thank you for your interest in contributing to Phantom Crowd! We welcome contributions from the community to help improve this AR civic issue reporting tool.

## How to Contribute

### 1. Fork the Repository
Fork the project to your own GitHub account.

### 2. Create a Feature Branch
Create a new branch for your feature or bug fix:
```bash
git checkout -b feature/amazing-feature
```


### 3. Make Changes
- Write clean, maintainable code.
- Follow the existing code style.
- Ensure you have set up the environment variables (`local.properties`) and Firebase configuration (`google-services.json`).

### 4. Commit Changes
Commit your changes with descriptive commit messages:
```bash
git commit -m "Add feature X to allow Y"
```

### 5. Push to Your Fork
Push your branch to your forked repository:
```bash
git push origin feature/amazing-feature
```

### 6. Submit a Pull Request
Open a Pull Request (PR) from your fork to the main repository. Provide a clear description of your changes and any relevant screenshots.

## Code Style Guidelines

- **Kotlin**: Follow the [official Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Jetpack Compose**:
  - Use PascalCase for Composable functions.
  - Follow [Compose API guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md).
  - Use `MaterialTheme` for styling.
- **Architecture**: Stick to MVVM (Model-View-ViewModel). Place UI logic in ViewModels and keep Composables stateless where possible.

## Reporting Bugs

If you find a bug, please open an issue in the repository with the following details:
- Description of the issue.
- Steps to reproduce.
- Expected vs. actual behavior.
- Device/Emulator details (Model, Android Version).
- Screenshots or logs (if applicable).

Thank you for contributing!
