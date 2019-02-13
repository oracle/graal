# Accessing resources in Substrate VM images

By default, the native-image builder will not integrate any of the resources which are on the classpath during image building into the image it creates.
To make calls such as Class.{getResource,getResourceAsStream} (or the corresponding ClassLoader methods) return specific resources (instead of null), the resources that should be accessible at image runtime need to be explicitly specified with the following option
```bash
-H:IncludeResources=<Java regexp that matches resources to be included in the image>
```
You can pass `-H:IncludeResources` several times to define more than one regexp to match resources.
To see which resources get included into the image you can enable the related logging info with `-H:Log=registerResource:`.

Example usage:

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

*  All resources can be loaded with `-H:IncludeResources='.*/Resource.*txt$'`
*  `Resource0.txt` can be loaded with `-H:IncludeResources='.*/Resource0.txt$'`
*  `Resource0.txt` and `Resource1.txt` can be loaded with `-H:IncludeResources='.*/Resource0.txt$' -H:IncludeResources='.*/Resource1.txt$'`
   (or alternatively with a single `-H:IncludeResources='(.*/Resource0.txt$)|(.*/Resource1.txt$)'`)

# Resource Bundles on Substrate VM

Java localization support (`java.util.ResourceBundle`) enables Java code to load L10N resources and show the right user messages suitable for actual runtime time locale, format and etc. settings.

Substrate VM needs an ahead of time knowledge of resources bundles your application needs.
Resource bundles need to be passed to the `native-image` binary using the option
```bash
-H:IncludeResourceBundles=your.pgk.Bundle,another.pkg.Resource,etc.Bundle
```
then Substrate VM loads and stores appropriate bundles for usage in generate binary.
