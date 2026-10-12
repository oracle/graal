# Native Image Class Initialization

- When upgrading the vendored JLine library review changes to static initializers that run at image build time:
    - OS-dependent code must not run at image build time.
    - Review system property reads. They usually belong at runtime, except for intentional build-time knobs, such as `org.graalvm.shadowed.org.jline.terminal.ffm.disable`.
