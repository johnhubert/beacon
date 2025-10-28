# UI Conventions

Prefer TypeScript and React Native for UI. Always use latest available libraries, check that you are using
the latest, upgrade to latest if not. Use a hot reload mechanism using Metro Go to allow for testing ios
and android on the local network.

# Backend Conventions

Use springboot and latest java version for backend microservices
All methods of interfaces must have javadoc documentation describing their function in plain language.
All interfaces must have javadoc documentation describing their function in plain language.
Concrete implementations of interfaces should have a quick javadoc describing their implementation.
Any new methods in concrete implementations that weren't inherited from the interface need Javadoc as well
All complex implementations, methods over 10 lines of code, should have periodic clear comments describing any
non-trivial functionality.

# General Conventions

NEVER check in any secrets, keys, etc
