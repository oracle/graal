---
layout: docs
toc_group: native-image
link_title: Accessing Resources in Native Images
permalink: /reference-manual/native-image/Resources/
---
# Accessing Resources in Native Images

By default, the native image builder will not integrate any of the resources which are on the classpath during the generation into the final image.
To make calls such as `Class.getResource()` or `Class.getResourceAsStream()` (or the corresponding ClassLoader methods) return specific resources (instead of null), the resources that should be accessible at image run time need to be explicitly specified. This can be done via a configuration file such as the following:

```json
{
  "resources": {
    "includes": [
      {"pattern": "<Java regexp that matches resource(s) to be included in the image>"},
      {"pattern": "<another regexp>"},
      ...
    ],
    "excludes": [
      {"pattern": "<Java regexp that matches resource(s) to be excluded from the image>"},
      {"pattern": "<another regexp>"},
      ...
    ]
  }
}
```

The configuration file's path must be provided to `native-image` with `-H:ResourceConfigurationFiles=/path/to/resource-config.json`.
Alternatively, individual resource paths can also be specified directly to `native-image`:

```shell
native-image -H:IncludeResources=<Java regexp that matches resources to be included in the image> -H:ExcludeResources=<Java regexp that matches resources to be excluded from the image> ...
```
The `-H:IncludeResources` and `-H:ExcludeResources` options can be passed several times to define more than one regexp to match or exclude resources, respectively.

To see which resources get ultimately included into the image, you can enable the related logging info with `-H:Log=registerResource:`.

### Example Usage

Given this project structure:
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
Then:

*  All resources can be loaded with `.*/Resource.*txt$`, specified as `{"pattern":".*/Resource.*txt$"}` in a configuration file, or `-H:IncludeResources='.*/Resource.*txt$'` on the command line.
*  `Resource0.txt` can be loaded with `.*/Resource0.txt$`.
*  `Resource0.txt` and `Resource1.txt` can be loaded with `.*/Resource0.txt$` and `.*/Resource1.txt$`
   (or alternatively with a single `.*/(Resource0|Resource1).txt$`).
*  Also, if we want to include everything except the `Resource2.txt` file, we can simply exclude it with `-H:IncludeResources='.*/Resource.*txt$'` followed by `-H:ExcludeResources='.*/Resource2.txt$'`.

See also the [guide on assisted configuration of Java resources and other dynamic features](BuildConfiguration.md#assisted-configuration-of-native-image-builds).

## Locales

It is also possible to specify which locales should be included in the image and what should be the default one.
For example, to switch the default locale to German and also include French and English, one can use the following hosted options.
```shell
native-image -H:DefaultLocale=de -H:IncludeLocales=fr,en
```
The locales are specified using [language tags](https://docs.oracle.com/javase/tutorial/i18n/locale/matching.html). All
locales can be included via ``-H:+IncludeAllLocales``, but please note that it increases the size of the resulting
binary.

## Resource Bundles in Native Image

Java localization support (`java.util.ResourceBundle`) enables Java code to load L10N resources and show the right user messages suitable for actual runtime settings like time locale and format, etc.

Native Image needs ahead-of-time knowledge of the resource bundles your application needs so that it can load and store the appropriate bundles for usage in the generated binary.
The bundles can be specified in the resource configuration file (see above), in the `bundles` section:

```json
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
```shell
native-image -H:IncludeResourceBundles=your.pgk.Bundle,another.pkg.Resource,etc.Bundle ...
```
By default, the requested bundles are included for all requested locales.
In order to optimize this, it is possible to use ``IncludeResourceBundles`` with locale specific substring, for example ``-H:+IncludeResourceBundles=com.company.bundles.MyBundle_fr-FR`` will include the bundle only in French.

### JVM Mode of Localization

Resource Bundle lookup is a complex and dynamic mechanism which utilizes a lot of the infrastructure of JVM.
As a result of that, it causes image size increase for smaller applications such as Hello World.
Therefore, an optimized mode is set by default in which this lookup is simplified utilizing the fact the all bundles are known ahead of time.
In case you would like to use the original JVM lookup, use the `-H:-LocalizationOptimizedMode` option.
