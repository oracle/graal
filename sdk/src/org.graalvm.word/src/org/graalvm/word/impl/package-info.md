This package provides implementation support for word types as well as extra operations like
`org.graalvm.word.impl.Word.objectToUntrackedPointer(Object)` which converts an Object
reference to a raw pointer. Like _restricted_ methods in the Java SE API (e.g.
`java.lang.foreign.MemorySegment.reinterpret(long)`), extra steps are required to use the
classes in this package (i.e., exporting `org.graalvm.word.impl` on the command line with
`--add-exports`.

Note: This file is not `package-info.java` to avoid being interpreted as denoting
`org.graalvm.word.impl` as a public package to `mx sigtest`.