#### SVM support for `java.util.zip.ZipFile`

In JDK 8 `ZipFile` is implemented using `zlib` via JNI.
In JDK 9 `ZipFile` was refactored to a Java only implementation.
For `ZipFile` support in SVM we chose to use the JDK 9 implementation.
Using the JDK 8 `zlib` via JNI approach proved to be difficult.
While SVM has full support for JNI, extracting all the `zlib` wrapper code from `JDK` cannot be done cleanly.
The main file `/openjdk8u/jdk/src/share/native/java/util/zip/ZipFile.c` depends on `jvm.h` which brings in a lot of VM code.
The `ZipFile.c` implementation relies on code from `jvm.h` for file operations (e.g., `JVM_Open`, `JVM_Close`).
While it would be possible to extract only the used parts from `jvm.h` and write a custom `zlib` wrapper this would produce hard to maintain `C` code especially across different Java versions and OSs.

Thus we copied over the JDK 9 classes and replaced the JDK 8 classes via substitution:
- `com.oracle.svm.core.jdk.zipfile.ZipFile` is based on `java.base/java.util.zip.ZipFile`
- `com.oracle.svm.core.jdk.zipfile.ZipEntry` is based on `java.base/java.util.zip.ZipEntry`
- `com.oracle.svm.core.jdk.zipfile.ZipUtils` is based on `java.base/java.util.zip.ZipUtils`
- `com.oracle.svm.core.jdk.zipfile.ZipConstants` is based on `java.base/java.util.zip.ZipConstants`
- `com.oracle.svm.core.jdk.zipfile.ZipConstants64` is based on `java.base/java.util.zip.ZipConstants64`
- `com.oracle.svm.core.jdk.zipfile.ZipCoder` is based on `java.base/java.util.zip.ZipCoder`

These classes exist in JDK 8 as well and using more fine grained substitutions would have been possible (e.g., substituting only those methods and fields that are different).
However the number of changes in JDK9 is significant and this approach would have resulted in very fragmented substitution code.

By copying over the files the changes are limited to the insertion of `@Substitute` and `@TargetClass(className = "java.util.zip.*")` annotations.
The approach is to modify the copied files as little as possible to reduce maintenance overhead.
If the `JDK9` classes evolve a simple diff can yield the differences and the copied classes can be updated.
One exception to this is the introduction of `Target_java_util_zip_Inflater` to expose the package private method `ended()` which is accessed using  `KnownIntrinsics.unsafeCast(inf, Target_java_util_zip_Inflater.class).ended()` in `ZipFile`.
