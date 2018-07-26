# Accessing resources in Substrate VM images

By default, the native-image builder will not integrate any of the resources
which are on the classpath during image building into the image it creates. To
make calls such as Class.{getResource,getResourceAsStream} (or the
corresponding ClassLoader methods) return specific resources (instead of null),
the resources that should be accessible at image runtime need to be explicitly
specified with the following option
```bash
-H:IncludeResources=<Java regexp that matches resources to be included in the image>
```

# Resource Bundles on Substrate VM

Java localization support (`java.util.ResourceBundle`) enables Java code to
load L10N resources and show the right user messages suitable for actual
runtime time locale, format and etc. settings.

Substrate VM needs an ahead of time knowledge of resources bundles your application
needs. Resource bundles need to be passed to the `native-image` binary using the option
```bash
-H:IncludeResourceBundles=your.pgk.Bundle,another.pkg.Resource,etc.Bundle
```
then Substrate VM loads and stores appropriate bundles for usage in generate
binary.
