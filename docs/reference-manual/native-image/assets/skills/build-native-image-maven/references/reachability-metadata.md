# Reachability Metadata for Maven

Use this guide to resolve native-image build failures caused by missing reachability metadata for reflection, resources, serialization, or JNI. Follow the workflow below to detect, collect, and manually add metadata as needed.

## Detect Missing Metadata

Add the build option and these runtime options to your Maven plugin configuration to enable metadata checks and warnings:
```xml
<configuration>
  <buildArgs>
    <buildArg>--future-defaults=exact-reflection</buildArg>
  </buildArgs>
  <runtimeArgs>
    <runtimeArg>-XX:+ExactReachabilityMetadata</runtimeArg>
    <runtimeArg>-XX:MissingRegistrationReportingMode=Warn</runtimeArg>
  </runtimeArgs>
</configuration>
```

Keep the split: `--future-defaults=exact-reflection` selects exact reachability metadata at build time and belongs in `<buildArgs>`, while the `-XX:` options refine that mode at run time and belong in `<runtimeArgs>`.
A `<buildArg>-R:+ExactReachabilityMetadata</buildArg>` makes the runtime option the default of the executable, but only alongside `--future-defaults=exact-reflection`; without it the build fails with
`Error: The option 'ExactReachabilityMetadata' can only be set for an image built with '--future-defaults=exact-reflection'.`

## Resolution Workflow

### Run the Tracing Agent

Run the tracing agent to collect metadata:
```bash
./mvnw -Pnative -Dagent=true test
./mvnw -Pnative native:metadata-copy
./mvnw -Pnative package
```

Configure metadata copy in your plugin:
```xml
<agent>
  <enabled>true</enabled>
  <metadataCopy>
    <disabledStages>
      <stage>main</stage>
    </disabledStages>
    <merge>true</merge>
    <outputDirectory>META-INF/native-image</outputDirectory>
  </metadataCopy>
</agent>
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
./mvnw -Pnative package
./mvnw -Pnative test
```

If a library still fails after repository, agent, and manual entries, capture the exact missing symbol from the error output and add only that entry.
