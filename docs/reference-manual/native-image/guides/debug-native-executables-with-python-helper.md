---
layout: ni-docs
toc_group: how-to-guides
link_title: Debug Native Executables with a Python Helper Script
permalink: /reference-manual/native-image/guides/debug-native-image-process-with-python-helper-script/
---
# Debug Native Executables with a Python Helper Script

Additionally to the [GDB debugging](debug-native-executables-with-gdb.md), you can debug a `native-image` process using a Python helper script, _gdb-debughelpers.py_.
The [GDB Python API](https://sourceware.org/gdb/current/onlinedocs/gdb/Python.html) is used to provide a reasonably good experience for debugging native executables or shared libraries.
It requires GDB with Python support.
The debugging extension is tested against GDB 13.2 and supports the new debuginfo generation introduced in GraalVM for JDK 17 and later.

> Note: The _gdb-debughelpers.py_ file does not work with versions older than version 13.2 of `gdb` or versions older than GraalVM for JDK 17.

The Python script _gdb-debughelpers.py_ can be found in the _&lt;GRAALVM\_HOME&gt;/lib/svm/debug_ directory.
If debuginfo generation is enabled (see [Build a Native Executable with Debug Information](debug-native-executables-with-gdb.md#build-a-native-executable-with-debug-information)), the script is copied to the build directory.
The `native-image` tool adds the debugging section `.debug_gdb_scripts` to the debug info file, which causes GDB to automatically load _gdb-debughelpers.py_ from the current working directory.

For [security reasons](https://sourceware.org/gdb/current/onlinedocs/gdb/Auto_002dloading-safe-path.html)
the first time GDB encounters a native executable or shared library that requests a specific Python file to be loaded it will print a warning:

>     warning: File "<CWD>/gdb-debughelpers.py" auto-loading has been declined by your `auto-load safe-path' set to "$debugdir:$datadir/auto-load".
>
>     To enable execution of this file add
>             add-auto-load-safe-path <CWD>/gdb-debughelpers.py
>     line to your configuration file "<HOME>/.gdbinit".
>     To completely disable this security protection add
>             add-auto-load-safe-path /
>     line to your configuration file "<HOME>/.gdbinit".
>     For more information about this security protection see the
>     "Auto-loading safe path" section in the GDB manual.  E.g., run from the shell:
>             info "(gdb)Auto-loading safe path"

To solve this, either add the current working directory to _~/.gdbinit_ as follows:

    echo "add-auto-load-safe-path <CWD>/gdb-debughelpers.py" >> ~/.gdbinit

or pass the path as a command line argument to `gdb`:

    gdb -iex "set auto-load safe-path <CWD>/gdb-debughelpers.py" <binary-name>

Both enable GDB to auto-load _gdb-debughelpers.py_ from the current working directory.

Auto-loading is the recommended way to provide the script to GDB.
However, it is possible to manually load the script from GDB explicitly with:

    (gdb) source gdb-debughelpers.py

## Pretty Printing Support

Loading _gdb-debughelpers.py_ registers a new pretty printer to GDB, which adds an extra level of convenience for debugging native executables or shared libraries.
This pretty printer handles the printing of Java Objects, Arrays, Strings, and Enums for debugging native executables or shared libraries.
If the Java application uses `@CStruct` and `@CPointer` annotations to access C data structures, the pretty printer will also try to print them as if they were Java data structures.
If the C data structures cannot be printed by the pretty printer, printing is performed by GDB.

The pretty printer also prints of the primitive value of a boxed primitive (instead of a Java Object).

Whenever printing is done via the `p` alias of the `print` command the pretty printer intercepts that call to perform type casts to the respective runtime types of Java Objects.
This also applies for auto-completion when using the `p` alias.
This means that if the static type is different to the runtime type, the `print` command uses the static type, which leaves the user to discover the runtime type and typecast it.
Additionally, the `p` alias understands Java field and array access and function calls for Java Objects.

#### Limitations

The `print` command still uses its default implementation, as there is no way to overwrite it, while still keeping the capability of the default `print` command.
Overriding would cause printing non-Java Objects to not work properly.
Therefore, only the `p` alias for the `print` command is overwritten by the pretty printer, such that the user can still make use of the default GDB `print` command.

### Options to Control the Pretty Printer Behavior

In addition to the enhanced `p` alias, _gdb-debughelpers.py_ introduces some GDB parameters to customize the behavior of the pretty printer.
Parameters in GDB can be controlled with `set <param> <value>` and `show <param>` commands, and thus integrate with GDB's customization options.

* #### svm-print on/off

Use this command to enable/disable the pretty printer.
This also resets the `print` command alias `p` to its default behavior.
Alternatively pretty printing can be suppressed with the
[`raw` printing option of GDB's `print` command](https://sourceware.org/gdb/current/onlinedocs/gdb/Output-Formats.html):

    (gdb) show svm-print
    The current value of 'svm-print' is "on".
    
    (gdb) print str
    $1 = "string"
    
    (gdb) print/r str
    $2 = (java.lang.String *) 0x7ffff689d2d0
    
    (gdb) set svm-print off
    1 printer disabled
    1 of 2 printers enabled
    
    (gdb) print str
    $3 = (java.lang.String *) 0x7ffff689d2d0

* #### svm-print-string-limit &lt;int&gt;

Customizes the maximum length for pretty printing a Java String.
The default value is `200`.
Set to `-1` or `unlimited` for unlimited printing of a Java String.
This does not change the limit for a C String, which can be controlled with GDB's `set print characters` command.

* #### svm-print-element-limit &lt;int&gt;

Customizes the maximum number of elements for pretty printing a Java Array, ArrayList, and HashMap.
The default value is `10`.
Set to `-1` or `unlimited` to print an unlimited number of elements.
This does not change the limit for a C array, which can be controlled with GDB's `set print elements` command.
However, GDB's parameter `print elements` is the upper bound for `svm-print-element-limit`.

* #### svm-print-field-limit &lt;int&gt;

Customizes the maximum number of elements for pretty printing fields of a Java Object.
The default value is `50`.
Set to `-1` or `unlimited` to print an unlimited number of fields.
GDB's parameter `print elements` is the upper bound for `svm-print-field-limit`.

* #### svm-print-depth-limit &lt;int&gt;

Customizes the maximum depth of recursive pretty printing.
The default value is `1`.
The children of direct children are printed (a sane default to make contents of boxed values visible).
Set to `-1` or `unlimited` to print unlimited depth.
GDB's parameter `print max-depth` is the upper bound for `svm-print-depth-limit`.

* #### svm-use-hlrep on/off

Enables/disables pretty printing for higher level representations.
It provides a more data-oriented view on some Java data structures with a known internal structure such as Lists or Maps.
Currently supports ArrayList and HashMap.

* #### svm-infer-generics &lt;int&gt;

Customizes the number of elements taken into account to infer the generic type of higher level representations.
The default value is `10`.
Set to `0` to not infer generic types and `-1` or `unlimited` to infer the generic type of all elements.

* #### svm-print-address absolute/on/off

Enables/disables printing of addresses in addition to regular pretty printing.
When `absolute` mode is used even compressed references are shown as absolute addresses.
Printing addresses is disabled by default.

* #### svm-print-static-fields on/off

Enables/disables printing of static fields for a Java Object.
Printing static fields is disabled by default.

* #### svm-complete-static-variables on/off

Enables/disables auto-completion of static field members for the enhanced `p` alias.
Auto-completion of static fields is enabled by default.

* #### svm-selfref-check on/off

Enables/disables self-reference checks for data structures.
The pretty printer detects a self-referential data structure and prevents further expansion to avoid endless recursion.
Self-reference checks are enabled by default.
For testing, this feature can be temporary disabled (usually you wouldn't want to do this).

### Related Documentation

* [Debug Info Feature](../DebugInfo.md)
* [Debug Native Executables with GDB](debug-native-executables-with-gdb.md)