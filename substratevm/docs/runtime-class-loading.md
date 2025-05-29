# Runtime Class Loading

Runtime class loading lets `ClassLoader.defineClass` work at runtime, creating new `DynamicHub`s.

## Requirements

It requires:
* `ClosedTypeWorld` to be disabled since we are adding new types
* `ClassForNameRespectsClassLoader` to be enabled for the classloader mechanisms to work and reach `defineClass`
* `SupportPredefinedClasses` to be disabled as a simplification to avoid having to worry about the intersection of those features

## Status

At the moment it only supports loading some trivial classes with no fields or methods.
Inner classes and classes that are part of a nest are not supported.

Parallel class loading is explicitly disabled and not supported at the moment.

