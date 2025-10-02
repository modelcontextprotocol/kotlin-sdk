# Contributing Guidelines

There are two main ways to contribute to the project &mdash; submitting issues and submitting
fixes/changes/improvements via pull requests.

## Submitting issues

Both bug reports and feature requests are welcome.
Submit issues [here](https://github.com/modelcontextprotocol/kotlin-sdk/issues).

* Search for existing issues to avoid reporting duplicates.
* When submitting a bug report:
    * Test it against the most recently released version. It might have already been fixed.
    * Include the code that reproduces the problem. Provide a minimal, complete, and reproducible example.
    * However, don't put off reporting any weird or rarely appearing issues just because you cannot consistently
      reproduce them.
    * If the bug is related to behavior, explain what behavior you expected and what you got.
* When submitting a feature request:
    * Explain why you need the feature &mdash; what's your use-case, what's your domain.
    * Explaining the problem you face is more important than suggesting a solution.
      Even if you don't have a proposed solution, please report your problem.
    * If there is an alternative way to do what you need, then show the code of the alternative.

## Submitting PRs

We love PRs. Submit PRs [here](https://github.com/modelcontextprotocol/kotlin-sdk/pulls).
However, please keep in mind that maintainers will have to support the resulting code of the project,
so do familiarize yourself with the following guidelines.

* All development (both new features and bug fixes) is performed in the `main` branch.
    * Please base your PRs on the `main` branch.
    * PR should be linked with the issue, excluding minor documentation changes, adding unit tests, and fixing typos.
* If you make any code changes:
    * Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/reference/coding-conventions.html).
    * [Build the project](#building) to ensure it all works and passes the tests.
* If you fix a bug:
    * Write the test that reproduces the bug.
    * Fixes without tests are accepted only in exceptional circumstances if it can be shown that writing the
      corresponding test is too hard or otherwise impractical.
* If you introduce any new public APIs:
    * All new APIs must come with documentation and tests.
    * If you plan API additions, please start by submitting an issue with the proposed API design to gather community
      feedback.
    * [Contact the maintainers](#contacting-maintainers) to coordinate any great work in advance via submitting an
      issue.
* If you fix documentation:
    * If you plan extensive rewrites/additions to the docs, then
      please [contact the maintainers](#contacting-maintainers) to coordinate the work in advance.

## Style guides

A few things to remember:

* Your code should conform to
  the official [Kotlin code style guide](https://kotlinlang.org/docs/reference/coding-conventions.html).
  Code style is managed by [EditorConfig](https://www.jetbrains.com/help/idea/editorconfig.html),
  so make sure the EditorConfig plugin is enabled in the IDE.
* Every public API (including functions, classes, objects and so on) should be documented,
  every parameter, property, return types and exceptions should be described properly.

## Commit messages

* Commit messages should be written in English
* They should be written in present tense using imperative mood
  ("Fix" instead of "Fixes", "Improve" instead of "Improved").
  Add the related bug reference to a commit message (bug number after a hash character between round braces).

See [How to Write a Git Commit Message](https://chris.beams.io/posts/git-commit/)

## Building

### Requirements

* To build MCP Kotlin SDK, JDK version 21 or higher is required. Make sure this is your default JDK (`JAVA_HOME` is set
  accordingly)
* The project can be opened in IntelliJ IDEA without additional prerequisites.

### Building MCP Kotlin SDK from source

* Run `./gradlew assemble` to build the project and produce the corresponding artifacts.
* Run `./gradlew test` to test the module and speed up development.
* Run `./gradlew build` to build the project, which also runs all the tests.
* Run `./gradlew allTests` to run all tests.

*note*: when you change the data model, you might need to regenerate the .api files by running `./gradlew apiDump`.

## Contacting maintainers

* If something cannot be done, not convenient, or does not work &mdash; submit an [issue](#submitting-issues).
* "How to do something" questions &mdash; [StackOverflow](https://stackoverflow.com).
* Discussions and general inquiries &mdash; use [KotlinLang Slack](https://kotl.in/slack).
