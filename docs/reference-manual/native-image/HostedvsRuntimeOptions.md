---
layout: ni-docs
toc_group: contributing
link_title: Hosted and Runtime Options
permalink: /reference-manual/native-image/overview/HostedvsRuntimeOptions/
redirect_from: /$version/reference-manual/native-image/HostedvsRuntimeOptions/
---

# Native Image Hosted and Runtime Options

Along with all the options listed in the [Native Image Build Options](BuildOptions.md), we also distinguish between hosted and runtime options.

* **Hosted options**: configure a native image build, for example, influence what is put into the image and how the image is built. These are set using the prefix `-H:` on the command line.

* **Runtime options**: get their initial value during the image build, using the prefix `-R:` on the command line for the `native-image` builder. At run time, the default prefix is `-XX:` (but this is application-specific and not mandated by Native Image).

For developer documentation on how to define and use options, read the documentation of the `com.oracle.svm.core.option` package.

## List of Useful Options

### Graph Dumping
Native Image re-used the GraalVM options for graph dumping, logging, counters, and everything else in the GraalVM debug environment.
These GraalVM options can be used both as hosted options (if you want to dump graphs of the native image builder), and as runtime options (if you want to dump graphs during dynamic compilation at runtime).

The Graal compiler options that work as expected include `Dump`, `DumpOnError`, `Log`, `MethodFilter`, and the options to specify file names and ports for the dump handlers.

For example:
* To dump the compiler graphs of the `native-image` builder: `-H:Dump= -H:MethodFilter=ClassName.MethodName`.

* To dump the compile graphs at runtime, specify the dump flags at runtime: `-XX:Dump= -XX:MethodFilter=ClassName.MethodName`.

### Software Bill of Materials (SBOM)

GraalVM Enterprise Native Image can embed a Software Bill of Materials (SBOM) into a native executable by using an experimental option.
The option takes the desired SBOM format as input, and the embedded SBOM can be passively obtained by using the [Native Image Inspection Tool](InspectTool.md).
Currently, the CycloneDX format is supported. Users may embed a CycloneDX SBOM into a native executable by using the `--enable-sbom` option during compilation.
The SBOM is stored in a compressed format (`gzip`) with the exported `sbom` symbol referencing its start address and the `sbom_length` symbol its size.

### Debug Options

These options enable additional checks in the generated binary to help with debugging:

* `-H:[+|-]HostedAssertions`
  enables or disables Java assert statements in the `native-image` builder.
This flag is translated to either `-ea -esa` or `-da -dsa` for the HotSpot VM.
* `-H:[+|-]RuntimeAssertions`
  enables or disables Java assert statements at run time.
* `-H:TempDirectory=FileSystemPath`
  generates a directory for temporary files during a native image generation.
If this option is specified, the temporary files are not deleted so that you can inspect them after the native image generation.

### Control the Main Entry Points

* `-H:Kind=[EXECUTABLE | SHARED_LIBRARY]`:
  generates an executable with a main entry point, or a shared library with all entry points that are marked via `@CEntryPoint`.
* `-H:Class=ClassName`:
  the class containing the default entry point method.
Ignored if `Kind == SHARED_LIBRARY`.
* `-H:Projects=Project1,Project2`:
  the project that contains the application (and transitively all projects that it depends on).
* `-H:Name=FileName`:
  the name of the executable file that is generated.
* `-H:Path=FileSystemPath`:
  the directory where the generated executable is placed.

### Further Reading

* [Native Image Build Options](BuildOptions.md)