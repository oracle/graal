---
layout: docs
toc_group: native-image
link_title: Native Image Inspection Tool
permalink: /reference-manual/native-image/inspect/
---

# Native Image Inspection Tool

Native Image Enterprise Edition comes with a tool outputting the list of methods included in a given executable or shared library compiled with GraalVM Native Image.
The tool is accessible through `$GRAALVM_HOME/bin/native-image-inspect <path_to_binary>` and outputs this list as a JSON array in the following format:

```shell
$GRAALVM_HOME/bin/native-image-inspect helloworld
{
  "methods": [
    {
      "declaringClass": "java.lang.Object",
      "name": "equals",
      "paramTypes": [
        "java.lang.Object"
      ]
    },
    {
      "declaringClass": "java.lang.Object",
      "name": "toString",
      "paramTypes": []
    },
    ...
  ]
}
```

The Native Image compilation process, by default, includes metadata in the executable allowing the inspection tool to emit the list of included methods.

The amount of data included is fairly minimal compared to the overall image size, however users can set the `-H:-IncludeMethodsData` option to disable the metadata emission.
Images compiled with this option will not be able to be inspected by the tool.

## Evolution

The tool is continuously being improved upon. Envisioned new features include:

* Outputting the list of classes and fields included in the image alongside the methods.
* Windows support
