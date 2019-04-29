### Points-to analysis reports

The points-to analysis produces two kinds of reports: analysis call tree and image object tree.
This information is produced by an intermediate step in the image building process and represents the static analysis view of the call graph and heap object graph.
These graphs are further transformed in the image building process before they are AOT compiled into the image and written into the image heap, respectively.

#### Call tree

The call tree is a a breadth-first tree reduction of the call graph as seen by the points-to analysis.
The points-to analysis eliminates calls to methods that it determines cannot be reachable at runtime, based on the analysed receiver types.
It also completely eliminates invocations in unreachable code blocks, e.g., blocks guarded by a type check that always fails.
The call tree report is enabled using the `-H:+PrintAnalysisCallTree` option.
It produces a file with the structure:

```
VM Entry Points
├── entry <entry-method> id=<entry-method-id> 
│   ├── directly calls <callee> id=<callee-id> @bci=<invoke-bci> 
│   │   └── <callee-sub-tree>
│   ├── virtually calls <callee> @bci=<invoke-bci> 
│   │   ├── is overridden by <overide-method-i> id=<overide-method-i-id> 
│   │   │   └── <callee-sub-tree>
│   │   └── is overridden by <overide-method-j> id-ref=<overide-method-j-id> 
│   └── interfacially calls <callee> @bci=<invoke-bci>
│       ├── is implemented by <implementation-method-x> id=<implementation-method-x-id> 
│       │   └── <callee-sub-tree>
│       └── is implemented by <implementation-method-y> id-ref=<implementation-method-y-id> 
├── entry <entry-method> id=<entry-method-id> 
│   └── <callee-sub-tree>
└── ...
```

The tags between `<`and `>` are expanded with concrete values, the rest is printed as presented.
The methods are formatted using `<qualified-holder>.<method-name>(<qualified-parameters>):<qualified-return-type>` and are expanded until no more callees can be reached.

Since this is a tree reduction of the call graph each concrete method is expanded exactly once.
The tree representation inherently omits calls to methods that have already been explored in a different branch or previously on the same branch.
This restriction implicitly fixes the recursion problem.
To convey the information that is lost through tree reduction each concrete method is given a unique id.
Thus when a method is reached for the first time it declares an identifier as `id=<method-id>`.
Subsequent discoveries of the same method use an identifier reference to point to the previously expansion location: `id-ref=<method-id>`.
Each `id=<method-id>` and `id-ref=<method-id>` are followed by a blank space to make it easy to search.

Each invoke is tagged with the invocation bci: `@bci=<invoke-bci>`.
For invokes of inline methods the `<invoke-bci>` is a list of bci values, separated with `->`, enumerating the inline locations, backwards to the original invocation location.

#### Image object tree

The image object tree is an exhaustive expansion of the objects included in the native image heap.
The tree is obtained by a depth first walk of the native image heap object graph.
It is enabled using the `-H:+PrintImageObjectTree` option.
The roots are either static fields or method graphs that contain embedded constants.
The printed values are concrete constant objects added to the native image heap.
Produces a file with the structure:

```
Heap roots
├── root <root-field> value:
│   └── <value-type> id=<value-id> toString=<value-as-string> fields:
│       ├── <field-1> value=null
│       ├── <field-2> toString=<field-2-value-as-string> (expansion suppressed)
│       ├── <field-3> value:
│       │   └── <field-3-value-type> id=<field-3-value-id> toString=<field-3-value-as-string> fields:
│       │       └── <object-tree-rooted-at-field-3>
│       ├── <array-field-4> value:
│       │   └── <array-field-4-value-type> id=<array-field-4-value-id> toString=<array-field-4-value-as-string> elements (excluding null):
│       │       ├── [<index-i>] <element-index-i-value-type> id=<element-index-i-value-id> toString=<element-index-i-value-as-string> fields:
│       │       │   └── <object-tree-rooted-at-index-i>
│       │       └── [<index-j>] <element-index-j-value-type> id=<element-index-j-value-id> toString=<element-index-j-value-as-string> elements (excluding null):
│       │           └── <object-tree-rooted-at-index-j>
│       ├── <field-5> value:
│       │   └── <field-5-value-type> id-ref=<field-5-value-id> toString=<field-5-value-as-string>
│       ├── <field-6> value:
│       │   └── <field-6-value-type> id=<field-6-value-id> toString=<field-6-value-as-string> (no fields)
│       └── <array-field-7> value:
│           └── <array-field-7-value-type> id=<array-field-7-id> toString=<array-field-7-as-string> (no elements)
├── root <root-field> id-ref=<value-id> toString=<value-as-string>
├── root <root-method> value:
│   └── <object-tree-rooted-at-constant-embeded-in-the-method-graph>
└── ...
```

The tags between `<`and `>` are expanded with concrete values, the rest is printed as presented.
The root fields are formatted using `<qualified-holder>.<field-name>:<qualified-declared-type>`.
The non-root fields are formatted using `<field-name>:<qualified-declared-type>`.
The value types are formatted using `<qualified-type>`.
The root methods are formatted using `<qualified-holder>.<method-name>(<unqualified-parameters>):<qualified-return-type>`
No-array objects are expanded for all fields (including null).
No-array objects with no fields are tagged with `(no fields)`.
Array objects are expanded for all non-null indexes: `[<element-index>] <object-tree-rooted-at-array-element>`
Empty array objects or with all null elements are tagged with `(no elements)`.

Each constant value is expanded exactly once to compress the format.
When a value is reached from multiple branches it is expanded only the first time and given an identifier: `id=<value-id>`.
Subsequent discoveries of the same value use an identifier reference to point to the previously expansion location: `id-ref=<value-id>`.

##### Suppressing expansion of values

Some values, such as `String`, `BigInteger` and primitive arrays, are not expanded by default and marked with `(expansion suppressed)`.
All the other types are expanded by default.
To force the suppression of types expanded by default you can use `-H:ImageObjectTreeSuppressTypes=<comma-separated-patterns>`.
To force the expansion of types suppressed by default or through the option you can use `-H:ImageObjectTreeExpandTypes=<comma-separated-patterns>`.
When both `-H:ImageObjectTreeSuppressTypes` and `-H:ImageObjectTreeExpandTypes` are specified `-H:ImageObjectTreeExpandTypes` has precedence.

Similarly, some roots, such as `java.lang.Character$UnicodeBlock.map"` that prints a lot of strings, are not expanded at all and marked with `(expansion suppressed)` as well.
All the other roots are expanded by default.
To force the suppression of roots expanded by default you can use `-H:ImageObjectTreeSuppressRoots=<comma-separated-patterns>`.
To force the expansion of roots suppressed by default or through the option you can use `-H:ImageObjectTreeExpandRoots=<comma-separated-patterns>`.
When both `-H:ImageObjectTreeSuppressRoots` and `-H:ImageObjectTreeExpandRoots` are specified `-H:ImageObjectTreeExpandRoots` has precedence.

All the suppression/expansion options above accept a comma-separated list of patterns.
For types the pattern is based on the fully qualified name of the type and refers to the concrete type of the constants.
(For array types it is enough to specify the elemental type; it will match all the arrays of that type, of all dimensions.)
For roots the pattern is based on the string format of the root as described above.
The pattern accepts the `*` modifier:
  - ends-with: `*<str>` - the pattern exactly matches all entries that end with `<str>`
  - starts-with: `<str>*` - the pattern exactly matches all entries that start with `<str>` 
  - contains: `*<str>*` - the pattern exactly matches all entries that contain `<str>` 
  - equals: `<str>` - the pattern exactly matches all entries that are equal to `<str>`  
  - all: `*` - the pattern matches all entries  

###### Examples
Types suppression/expansion:
  - `-H:ImageObjectTreeSuppressTypes=java.io.BufferedWriter` - suppress the expansion of `java.io.BufferedWriter` objects
  - `-H:ImageObjectTreeSuppressTypes=java.io.BufferedWriter,java.io.BufferedOutputStream` - suppress the expansion of `java.io.BufferedWriter` and `java.io.BufferedOutputStream` objects
  - `-H:ImageObjectTreeSuppressTypes=java.io.*` - suppress the expansion of all `java.io.*` objects 
  - `-H:ImageObjectTreeExpandTypes=java.lang.String` - force the expansion of `java.lang.String` objects
  - `-H:ImageObjectTreeExpandTypes=java.lang.String,java.math.BigInteger` - force the expansion of `java.lang.String` and `java.math.BigInteger` objects
  - `-H:ImageObjectTreeExpandTypes=java.lang.*` - force the expansion of all `java.lang.*` objects
  - `-H:ImageObjectTreeSuppressTypes=java.io.* -H:ImageObjectTreeExpandTypes=java.io.PrintStream` - suppress the expansion of all `java.io.*` but not `java.io.PrintStream` objects
  - `-H:ImageObjectTreeExpandTypes=*` - force the expansion of objects of all types, including those suppressed by default

Roots suppression/expansion:
  - `-H:ImageObjectTreeSuppressRoots="java.nio.charset.Charset.lookup(String)"` - suppress the expansion of all constants embedded in the graph of `com.oracle.svm.core.amd64.FrameAccess.wordSize()`
  - `-H:ImageObjectTreeSuppressRoots=java.util.*` - suppress the expansion of all roots that start with `java.util.`
  - `-H:ImageObjectTreeExpandRoots=java.lang.Character$UnicodeBlock.map` - force the expansion of `java.lang.Character$UnicodeBlock.map` static field root
  - `-H:ImageObjectTreeSuppressRoots=java.util.* -H:ImageObjectTreeExpandRoots=java.util.Locale` - suppress the expansion of all roots that start with `java.util.` but not `java.util.Locale`
  - `-H:ImageObjectTreeExpandRoots=*` - force the expansion of all roots, including those suppressed by default

##### Report files

The reports are generated in the `reports` subdirectory, relative to the image building directory.
When executing the `native-image` executable the image build directory defaults to the working directory and can be modified using the `-H:Path=<dir>` option.

The call tree report name has the structure `call_tree_<image_name>_<date_time>.txt`.
The object tree report name has the structure: `object_tree_<image_name>_<date_time>.txt`.
The image name is the name of the generated image, which can be set with the `-H:Name=<name>` option.
The `<date_time>` is in the `yyyyMMdd_HHmmss` format.
