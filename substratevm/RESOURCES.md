# Accessing resources in Substrate VM images
(See also the [guide on assisted configuration of Java resources and other dynamic features](CONFIGURE.md))

By default, the native-image builder will not integrate any of the resources which are on the classpath during image building into the image it creates.
To make calls such as `Class.getResource()`, `Class.getResourceAsStream()` (or the corresponding ClassLoader methods) return specific resources (instead of null), the resources that should be accessible at image runtime need to be explicitly specified. This can be done via a configuration file such as the following:

```
{
  "resources": [
    {"pattern": "<Java regexp that matches resource(s) to be included in the image>"},
    {"pattern": "<another regexp>"},
    ...
  ]
}
```

The configuration file's path must be provided to `native-image` with `-H:ResourceConfigurationFiles=/path/to/resource-config.json`. Alternatively, individual resource paths can also be specified directly to `native-image`:
```bash
native-image -H:IncludeResources=<Java regexp that matches resources to be included in the image> ...
```
The `-H:IncludeResources` option can be passed several times to define more than one regexp to match resources.

To see which resources get included into the image you can enable the related logging info with `-H:Log=registerResource:`.

## Example usage:

Given this project structure
```
my-app-root
└── src
    ├── main
    │   └── com.my.app
    │       ├── Resource0.txt
    │       └── Resource1.txt
    └── resources
        ├── Resource2.txt
        └── Resource3.txt
```
then:

*  All resources can be loaded with `.*/Resource.*txt$`, specified as `{"pattern":".*/Resource.*txt$"}` in a configuration file, or `-H:IncludeResources='.*/Resource.*txt$'` on the command line
*  `Resource0.txt` can be loaded with `.*/Resource0.txt$`
*  `Resource0.txt` and `Resource1.txt` can be loaded with `.*/Resource0.txt$` and `.*/Resource1.txt$`
   (or alternatively with a single `.*/(Resource0|Resource1).txt$`)

# Resource Bundles on Substrate VM

Java localization support (`java.util.ResourceBundle`) enables Java code to load L10N resources and show the right user messages suitable for actual runtime time locale, format and etc. settings.

Substrate VM needs an ahead of time knowledge of resources bundles your application needs. Resource bundles need to be provided to the `native-image` tool so that Substrate VM loads and stores appropriate bundles for usage in the generated binary. The bundles can be specified in the resource configuration file (see above), in the `bundles` section:

```
{
  "bundles": [
    {"name":"your.pkg.Bundle"},
    {"name":"another.pkg.Resource"},
    {"name":"etc.Bundle"}
  ],
  "resources": <see above>
}
```

Alternatively, bundles can be specified directly as options to `native-image` as follows:
```bash
native-image -H:IncludeResourceBundles=your.pgk.Bundle,another.pkg.Resource,etc.Bundle ...
```
