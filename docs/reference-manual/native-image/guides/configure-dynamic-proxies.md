---
layout: ni-docs
toc_group: how-to-guides
link_title: Configure Dynamic Proxies Manually
permalink: /reference-manual/native-image/guides/configure-dynamic-proxies/
redirect_to: /reference-manual/native-image/metadata/#reflection
---

# Configure Dynamic Proxies Manually

You can generate dynamic proxy classes at build time by specifying the list of interfaces that they implement.
This can be done by adding a reflection entry in the _reachability-metadata.json_ configuration file. For example:

```json
{
  "reflection": [
    {
      "type": {
        "proxy": ["java.lang.AutoCloseable", "java.util.Comparator"]
      }
    },
    {
      "type": {
        "proxy":["java.util.Comparator"]
      }
    },
    {
      "type": {
        "proxy":["java.util.List"]
      }
    }
  ]
}

```
> Note: The order of the specified proxy interfaces is significant: two requests for a `Proxy` class with the same combination of interfaces but in a different order will result in two distinct behaviors (for more detailed information, refer to class [`Proxy`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/Proxy.html)).

The `java.lang.reflect.Proxy` API also enables you to create a dynamic proxy that does not implement any user provided interfaces.
In this case the generated dynamic proxy class implements `java.lang.reflect.Proxy` only.

### Related Documentation

* [Reflection](../ReachabilityMetadata.md#reflection)