---
layout: ni-docs
toc_group: dynamic-features
link_title: Accessing Resources
permalink: /reference-manual/native-image/dynamic-features/Resources/
redirect_from: /$version/reference-manual/native-image/Resources/
---

# Accessing Resources in Native Image

By default, the `native-image` builder will not integrate any of the resources that are on the classpath into the native executable.
To make calls such as `Class.getResource()` or `Class.getResourceAsStream()` (or their corresponding `ClassLoader` methods) return specific resources (instead of `null`), you must specify the resources that should be accessible at runtime. 
This can be achieved using a configuration file with the following content:

```json
{
  "resources": {
    "includes": [
      {"pattern": "<Java regexp that matches resource(s) to be included in the executable>"},
      {"pattern": "<another regexp>"},
      ...
    ],
    "excludes": [
      {"pattern": "<Java regexp that matches resource(s) to be excluded from the executable>"},
      {"pattern": "<another regexp>"},
      ...
    ]
  }
}
```

Provide the configuration file's path to the `native-image` tool using the option `-H:ResourceConfigurationFiles=/path/to/resource-config.json`.
Alternatively, you can specify individual resource paths directly to the `native-image` tool as follows:

```shell
native-image -H:IncludeResources="<Java regexp that matches resources to be included in the executable>" -H:ExcludeResources="<Java regexp that matches resources to be excluded from the executable>" ...
```
You can pass the `-H:IncludeResources` and `-H:ExcludeResources` options several times to define more than one regexp to include or exclude resources, respectively.

To see which resources are included in the native executable, use the option `-H:Log=registerResource:<log level>`. The `<log level>` argument must be in the range `1` to `5` (from least detailed to most detailed). A `log level` of `3` provides brief details of the included resources.

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

* All resources can be loaded with `".*/Resource.*txt$"`, specified as `{"pattern":".*/Resource.*txt$"}` in a configuration file, or `-H:IncludeResources=".*/Resource.*txt$"` on the command line.
* _Resource0.txt_ can be loaded with `.*/Resource0.txt$`.
* _Resource0.txt_ and _Resource1.txt_ can be loaded with `.*/Resource0.txt$` and `.*/Resource1.txt$`
 (or alternatively with a single `.*/(Resource0|Resource1).txt$`).
* Also, if we want to include everything except the _Resource2.txt_ file, we can simply exclude it using `-H:IncludeResources=".*/Resource.*txt$"` followed by `-H:ExcludeResources=".*/Resource2.txt$"`.

Check [this guide](guides/include-resources.md) which illustrates how to include a resource into a native executable.  

## Locales

It is also possible to specify which locales should be included in the executable and which should be the default.
For example, to switch the default locale to Swiss German and also include French and English, use the following  options:
```shell
native-image -Duser.country=CH -Duser.language=de -H:IncludeLocales=fr,en
```
The locales are specified using [language tags](https://docs.oracle.com/javase/tutorial/i18n/locale/matching.html). You can include all
locales via ``-H:+IncludeAllLocales``, but note that it increases the size of the resulting
executable.

## Resource Bundles

Java localization support (`java.util.ResourceBundle`) enables Java code to load L10N resources and show the user messages suitable for runtime settings such as time, locale, and format.

Native Image needs knowledge ahead-of-time of the resource bundles your application needs so that it can load and store the appropriate bundles for usage in the generated executable.
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

Alternatively, bundles can be specified directly as options to the `native-image` tool as follows:
```shell
native-image -H:IncludeResourceBundles=your.pgk.Bundle,another.pkg.Resource,etc.Bundle ...
```
By default, requested bundles are included for all requested locales.
To optimize this, it is possible to use `IncludeResourceBundles` with a locale-specific substring, for example `-H:+IncludeResourceBundles=com.company.bundles.MyBundle_fr-FR` will only include the bundle in French.

### Resources in Java Modules

Wherever resources are specified with `<Java regexp that matches resources to be included in the image>` or resource bundles are specified via bundle name, it is possible to specify the exact modules from which these resources or bundles should be taken. To do so, specify the module name before the resource-regex or bundle name with `:` as the separator. For example:

```json
{
   "resources": {
      "includes": [
         {
            "pattern": "library-module:^resource-file.txt$"
         }
      ]
   },
   "bundles": [
      {"name":"main-module:your.pkg.Bundle"}
   ]
}
```

This will cause the `native-image` tool to only include `resource-file.txt` from the Java module `library-module`. If other modules or the classpath contains resources that match the pattern `^resource-file.txt$` only the one in module `library-module` is registered for inclusion in the executable. Similarly, if other bundles are accessible with the same bundle name `your.pkg.Bundle` only the one from `main-module` is included. Native image will also ensure that the modules are guaranteed to be accessible at runtime. That is, the following code pattern:
```java
InputStream resource = ModuleLayer.boot().findModule(moduleName).getResourceAsStream(resourcePath);
```
will always work as expected for resources registered as described above (even if the module does not contain any code that is considered reachable by static analysis).

## Java VM Mode of Localization

Resource Bundle lookup is a complex and dynamic mechanism which utilizes a lot of Java VM infrastructure.
As a result, it causes the size of the executable to increase for smaller applications such as `HelloWorld`.
Therefore, an optimized mode is set by default in which this lookup is simplified utilizing the fact that all bundles are known ahead of build time.
For the original Java VM lookup, use the `-H:-LocalizationOptimizedMode` option.

### Further Reading

* [Include Resources in a Native Executable](guides/include-resources.md)
* [Native Image Build Configuration](BuildConfiguration.md)