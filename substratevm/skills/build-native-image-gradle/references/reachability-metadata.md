# Reachability Metadata for Gradle

Use this guide to resolve native-image build failures caused by missing reachability metadata for reflection, resources, serialization, or JNI. Follow the workflow below to detect, collect, and manually add metadata as needed.

## Detect Missing Metadata

Add the build option and these runtime options to your Gradle configuration to enable metadata checks and warnings:
```groovy
graalvmNative {
  binaries.all {
    buildArgs.add('--future-defaults=exact-reflection')
    runtimeArgs.add('-XX:+ExactReachabilityMetadata')
    runtimeArgs.add('-XX:MissingRegistrationReportingMode=Warn')
  }
}
```

Keep the split: `--future-defaults=exact-reflection` selects exact reachability metadata at build time and belongs in `buildArgs`, while the `-XX:` options refine that mode at run time and belong in `runtimeArgs`.
A `buildArgs.add('-R:+ExactReachabilityMetadata')` makes the runtime option the default of the executable, but only alongside `--future-defaults=exact-reflection`; without it the build fails with
`Error: The option 'ExactReachabilityMetadata' can only be set for an image built with '--future-defaults=exact-reflection'.`

## Resolution Workflow

### Run the Tracing Agent

Run the tracing agent to collect metadata:
```bash
./gradlew generateMetadata -Pcoordinates=<library-coordinates> -PagentAllowedPackages=<condition-packages>
```

### Add Manual Metadata if Needed

If the agent-collected metadata is incomplete, add manual configuration:

Create `META-INF/native-image/<project-groupId>/manual-metadata/` and add a `reachability-metadata.json` file containing only the sections you need. Native Image automatically picks up metadata from this location.

For metadata layout and file semantics, see the [Reachability Metadata documentation](https://www.graalvm.org/latest/reference-manual/native-image/metadata/).

Minimal `reachability-metadata.json` reflection example:

```json
{
  "reflection": [
    {
      "condition": {
        "typeReached": "com.example.Condition"
      },
      "type": "com.example.Type",
      "methods": [
        {
          "name": "<init>",
          "parameterTypes": []
        }
      ]
    }
  ]
}
```

## Rebuild and Verify

Rebuild and test your project:
```bash
./gradlew nativeCompile
./gradlew nativeTest
```
