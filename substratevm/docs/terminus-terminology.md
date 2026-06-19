# Project Terminus Terminology

Project Terminus uses two related sets of terms:

- Lifecycle terms describe when code or state is used.
- Context terms describe which VM context owns code or state.

For the broader design, see [Project Terminus](project-terminus.md).
For API migration guidance, see [Project Terminus Migration Guide](terminus-migration.md).

These terms are related, but they are not interchangeable.
In particular, prefer **build-time code** over "hosted code" when discussing code that exists only while building an image.
Do not use "hosted context" as a synonym for the builder context, because build-time code can also run in the guest context.

## Context Diagram

![Project Terminus contexts](images/terminus-terminology.svg)

<details>
<summary>Diagram maintenance</summary>

The editable diagram source is embedded in [terminus-terminology.svg](images/terminus-terminology.svg).
Open the SVG in diagrams.net or draw.io to edit it.
After editing the diagram, regenerate the SVG from the _images_ directory:

```shell
drawio --export --embed-diagram --output terminus-terminology.svg terminus-terminology.svg
```

</details>

## Terms

### Build Time

Build time is the execution of Native Image that processes classes, JAR files, configuration, and metadata to build a native executable.

Build-time code can run in either the builder context or the guest context.
For example, Native Image builder internals run in the builder context, while user-supplied features are build-time code that can run in the guest context.

### Run Time

Run time is the execution of the generated native executable.
Run-time code and data are the code and data that are part of that executable.

### Builder Context

The builder context is the VM context that runs the Native Image builder.
It owns builder implementation classes, builder services, and host-side image-building state.

Project Terminus uses this term instead of "hosted context" because "hosted" is ambiguous.
Some build-time code belongs to the guest context.

### Guest Context

The guest context is the VM context that owns application and run-time world state during image building.
It contains application classes, referenced JDK classes, and Native Image run-time classes needed by the generated executable.

Hosted code must not assume that every guest-owned type has a corresponding host `Class<?>`.
Use guest-aware APIs such as `GuestAccess`, `VMAccess`, and JVMCI metadata types when working with guest-owned state.

### Hosted Code

Hosted code is an older term for code that runs while building an image.
Prefer the more precise term **build-time code**.

When the context matters, say **builder-context build-time code** or **guest-context build-time code**.

## Usage Guidance

- Use **build time** and **run time** for lifecycle discussions.
- Use **builder context** and **guest context** for VM ownership discussions.
- Use **guest-owned** for types, methods, fields, constants, or objects owned by the guest context.
- Use **host-owned** or **builder-owned** for state owned by the builder context.
- Avoid "hosted context"; choose builder context or guest context instead.
- Avoid assuming that build-time code always runs in the builder context.
