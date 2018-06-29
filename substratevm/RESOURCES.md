# Accessing resources in Substrate VM images

Per-default the native-image builder will not integrate any of the resources
that are on the classpath during image building into the image it creates. To
make calls like Class.{getResource,getResourceAsStream} or the corresponding
ClassLoader variants working in images, the resources that should be accessible
at image-runtime need to be explicitly specified with the following option
```bash
-H:IncludeResources=<Java regexp to match resources to be included in the image>
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
