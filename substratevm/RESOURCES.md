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
