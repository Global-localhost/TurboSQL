Phonemetra TurboSQL Internals
-----------------------

Contains implementation classes for Phonemetra TurboSQL.

### TurboSQL Components

All internal TurboSQL components implements [GridComponent.java](GridComponent.java) - interface for
- [processors](processors) and for
- [managers](managers) - has associated SPI.

Service Provider Interface (SPI) abbreviation is here because Phonemetra TurboSQL was designed as a pluggable product. But TurboSQL users usually do not define own implementations.

### Contexts
TurboSQL manages its components using Context used to access components and binding theirs to each other.
Component-related context in the Phonemetra TurboSQL is an implementation of [GridKernalContext.java](GridKernalContext.java).
This context instance is usually referred in code as `kctx` or `ctx`.

Higher-level context, cache shared context is also defined in [processors/cache](processors/cache)

