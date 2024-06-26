---
layout: docs
toc_group: dynamic-features
link_title: Accessing Resources
permalink: /reference-manual/native-image/dynamic-features/Resources/
redirect_from: /reference-manual/native-image/Resources/
---

# Accessing Resources in Native Image

By default, the `native-image` tool will not integrate any of the resources that are on the classpath into a native executable.
To make calls such as `Class.getResource()` or `Class.getResourceAsStream()` (or their corresponding `ClassLoader` methods) return specific resources (instead of `null`), you must specify the resources that should be accessible at runtime.

There are several ways a resource can be registered for inclusion and be made accessible at runtime:
1. Native Image automatically includes a [resource configuration file](#resource-configuration-file) when placed in the _META-INF/native-image/_ directory. This approach is a great way for libraries and frameworks to provide an out-of-the-box experience.
2. For more advanced use cases where a resource configuration file is insufficient, resources can be registered programmatically using the [public API](#public-api).
3. For testing during development, the [command-line option](#command-line-option) provides a useful approach.

## Resource Configuration File

A resource configuration file contains information about the resources that you need to include in a native executable, encoded with patterns matching actual resources on your file system.
Such a configuration file has to be named _resource-config.json_ and placed in the _META-INF/native-image/_ directory so that `native-image` will automatically make use of it.
You can either generate it using the [Tracing Agent](AutomaticMetadataCollection/#tracing-agent), and then manually refine it, or create it from scratch.
You can choose one of the formats to specify the required resources (or combine them if necessary):
1. Globs (**recommended**)
2. Regular expressions (Regex) (**discouraged**)

See below a valid configuration file structure (described in more details in [resource-config-schema](assets/resource-config-schema-v1.1.0.json)):

```json
{
  "globs": [
      {
       "glob": "<Glob pattern restricted to only support * and ** wildcards besides literals>"
      },
      {
       "glob": "<another glob pattern>"
      },
      ...
  ],
  "resources": [
      {
       "pattern": "<Java regex that matches resource(s) to be included in the executable>"
      },
      {
       "pattern": "<another regex>"
      },
      ...
  ]
}
```

Once created, the `native-image` tool automatically includes a resource configuration file when placed in the _META-INF/native-image/_ directory. 
Alternatively, the configuration file's path can be passed to `native-image` using the option `-H:ResourceConfigurationFiles=/path/to/resource-config.json`.

### Globs

You can write a glob pattern to specify any required resources in the `globs` section of the configuration file.
Also, if you use the [Tracing Agent](AutomaticMetadataCollection/#tracing-agent) to generate the required configuration, it prints all entries in this format.

Globs are the recommended way to provide resources for `native-image` because they:
* Have custom handling in `native-image` that can speed up a resource registration process
* Are less expressive and therefore less error-prone than regular expressions
* Provide better support for resource-related checks at runtime

There are several rules to be observed when specifying a resource path:
* The `native-image` tool supports only _star_ (\*) and _globstar_ (*\*) wildcard patterns.
  * Per definition, _star_ can match any number of any characters on one level while _globstar_ can match any number of any levels.
  * If there is a need to treat a star literally (without special meaning), it can be escaped using `\ `.
* In the glob, a _level_ represents part of the pattern separated with `/`.
* When writing glob patterns the following rules must be observed:
  * Glob cannot be empty (for example, _""_ )
  * Glob cannot end with a trailing slash (`/`) (for example, _"foo/bar/"_)
  * Glob cannot contain more than two consecutive (non-escaped) `*` characters on one level (for example, _"foo/*\*\*/"_ )
  * Glob cannot contain empty levels (for example, _"foo//bar"_)
  * Glob cannot contain two consecutive globstar wildcards (example, _"foo/\*\*\/\*\*\"_)
  * Glob cannot have other content on the same level as globstar wildcard (for example, _foo/*\*bar/x_)

### Regular Expressions (Regex)

Alternatively, you can write standard Java regular expressions (regex) patterns to specify required resources in the `resources` section of the configuration file.
This approach should only be used in extreme cases if the expressive power of globs is not sufficient.

### Example Usage

Given this project structure:
```
app-root
└── src
    └── main
        └── resources
            ├── Resource0.txt
            └── Resource1.txt
```
Then:

* All resources can be loaded with:
 * `"**/Resource*txt"`, specified as `{"glob":"**/Resource*txt"}` in a configuration file (**recommended**),
 * `".*/Resource.*txt"`, specified as `{"pattern":".*/Resource.*txt"}` in a configuration file, or with `-H:IncludeResources=".*/Resource.*txt"` on the command line.
* _Resource0.txt_ can be loaded with:
  * `**/Resource0.txt` - globs format
  * or with `.*/Resource0.txt` - regular expressions format
* _Resource0.txt_ and _Resource1.txt_ can be loaded with:
  * `**/Resource0.txt` and `**/Resource1.txt`- globs format
  * `.*/Resource0.txt` and `.*/Resource1.txt` (or alternatively with a single `.*/(Resource0|Resource1).txt$`) - regular expressions format

Check [this guide](guides/include-resources.md) which illustrates how to include a resource into a native executable.

## Public API

You can also register resources programmatically, using the [Native Image Feature API](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html).
With this approach, you cannot specify a resource using patterns, but only with its literal name.
Note that resource registration cannot be performed after the [beforeAnalysis](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html#beforeAnalysis(org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess)) phase.

## Command-Line Option

Alternatively, you can specify individual resource paths directly to the `native-image` tool as follows:

```shell
native-image -H:IncludeResources="<Java regex that matches resources to be included in the executable>" ...
```
Note that with this approach, you can only specify patterns written in the regex format, and therefore all advantages of using globs will not be accessible.

## Embedded Resources Information

There are two ways to see which resources were included in a native executable:
1. Use the option `--emit build-report` to generate a build report for your native executable.
   There you can find information about all included resources under the `Resources` tab.
2. Use the option `-H:+GenerateEmbeddedResourcesFile` to generate a JSON file  _embedded-resources.json_, listing all included resources.

For each registered resource you get:
* **Module** (or `unnamed` if a resource does not belong to any module)
* **Name** (resource path)
* **Origin** (location of the resource on the system)
* **Type** (whether the resource is a file, directory, or missing)
* **Size** (actual resource size)

> Note: The size of a resource directory represents only the size of the names of all directory entries (not a sum of the content sizes).

## Resource Bundles

Java localization support (`java.util.ResourceBundle`) enables Java code to load L10N resources and show the user messages suitable for runtime settings such as time, locale, and format.

Native Image needs knowledge ahead-of-time of the resource bundles your application uses so that it can load and store the appropriate bundles for usage in the generated executable.
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

By default, resource bundles are included for all requested locales.
To optimize this, use `IncludeResourceBundles` with a locale-specific substring, for example, `-H:+IncludeResourceBundles=com.company.bundles.MyBundle_fr-FR`. It will only include the bundle for _French (France)_.

## Locales

It is also possible to specify which locales should be included in a native executable and which should be the default.
For example, to switch the default locale to Swiss German and also include French and English, use the following options:
```shell
native-image -Duser.country=CH -Duser.language=de -H:IncludeLocales=fr,en
```
The locales are specified using [language tags](https://docs.oracle.com/javase/tutorial/i18n/locale/matching.html). 
You can include all locales via `-H:+IncludeAllLocales`, but note that it increases the size of the resulting executable.

## Resources in Java Modules

For every resource (either specified with globs or regular expressions) or resource bundle, it is possible to specify the module from which the resource or resource bundle should be taken.

* For glob-based resource patterns, you can specify a module name in the separate `module` field in each entry.
* For a regex-based resource patterns or bundles, you can specify a module name before the resource/bundle name with `:` as a separator.

For example:
```json
{
   "globs": [
      {
        "module:": "library-module",
        "glob": "resource-file.txt" 
      }
   ],
   "resources": [
      {
        "pattern": "library-module:^resource-file.txt$"
      }
   ],
   "bundles": [
      {
        "name":"main-module:your.pkg.Bundle"
      }
   ]
}
```

This will cause the `native-image` tool to only include `resource-file.txt` from the Java module `library-module`.
If other modules or the classpath contains resources that match the pattern `resource-file.txt`, only the one in `library-module` is registered for inclusion in the executable.
Similarly, if other resource bundles are accessible with the same bundle name `your.pkg.Bundle`, only the one from `main-module` is included.
Native Image will also ensure that the modules are guaranteed to be accessible at runtime.

The following code pattern
```java
InputStream resource = ModuleLayer.boot().findModule(moduleName).getResourceAsStream(resourcePath);
```
will always work as expected for resources registered as described above (even if the module does not contain any code that is considered reachable by static analysis).

## Java VM Mode of Localization

Resource Bundle lookup is a complex and dynamic mechanism which utilizes a lot of Java VM infrastructure.
As a result, it causes the size of the executable to increase for smaller applications such as `HelloWorld`.
Therefore, an optimized mode is set by default in which this lookup is simplified utilizing the fact that all resource bundles are known ahead of build time.
For the original Java VM lookup, use the `-H:-LocalizationOptimizedMode` option.

### Further Reading

* [Include Resources in a Native Executable](guides/include-resources.md)
* [Native Image Build Configuration](BuildConfiguration.md)