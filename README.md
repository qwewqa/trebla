# Trebla
[![Native Image](https://github.com/qwewqa/trebla/workflows/Native%20Image/badge.svg?event=push)](https://github.com/qwewqa/trebla/actions?query=workflow%3A%22Native+Image%22)

A programming language and compiler for creating rhythm game engines on [Sonolus](https://sonolus.com).

## Quick Start
1. Download the corresponding executable for your operating system from [releases](https://github.com/qwewqa/trebla/releases).
Unstable builds are available as [build artifacts](https://github.com/qwewqa/trebla/actions?query=workflow%3A%22Native+Image%22).
2. Add the executable to the PATH if desired.
3. Check the documentation at https://trebla.qwewqa.xyz for more information.

## Building from Source
Trebla was built and tested with JDK 11, but JDK 8 or higher should suffice.

1. Clone via git.
    ```
    git clone https://github.com/qwewqa/trebla.git
    ```
2. Run via gradle.
    ```
    gradlew run --args="[arguments]"
    ```
    See `gradlew tasks` for other options.
    
## GraalVM Native Image
Generating Native Image
```
native-image -jar trebla.jar -H:+AddAllCharsets
```
    
### Documentation
Documentation is built using [mkdocs](https://www.mkdocs.org/)
and the [material theme](https://squidfunk.github.io/mkdocs-material/).
