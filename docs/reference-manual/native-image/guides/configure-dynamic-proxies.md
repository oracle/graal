---
layout: ni-docs
toc_group: how-to-guides
link_title: Configure Dynamic Proxies Manually
permalink: /reference-manual/native-image/guides/configure-dynamic-proxies/
redirect_to: /reference-manual/native-image/metadata/#dynamic-proxy
---
# Configure Dynamic Proxies Manually

You can generate dynamic proxy classes at native executable build time by specifying the list of interfaces that they implement.
Native Image provides two options: 
- `-H:DynamicProxyConfigurationFiles=<comma-separated-config-files>`
- `-H:DynamicProxyConfigurationResources=<comma-separated-config-resources>`

These options accept JSON files whose structure is an array of arrays of fully qualified interface names. For example:

```json
[
 { "interfaces": [ "java.lang.AutoCloseable", "java.util.Comparator" ] },
 { "interfaces": [ "java.util.Comparator" ] },
 { "interfaces": [ "java.util.List" ] }
]
```
> Note: The order of the specified proxy interfaces is significant: two requests for a `Proxy` class with the same combination of interfaces but in a different order will result in two distinct behaviors (for more detailed information, refer to class [`Proxy`](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/reflect/Proxy.html)).

The `java.lang.reflect.Proxy` API also enables you to create a dynamic proxy that does not implement any user provided interfaces.
In this case the generated dynamic proxy class implements `java.lang.reflect.Proxy` only.

### Related Documentation

* [Dynamic Proxy](../DynamicProxy.md)