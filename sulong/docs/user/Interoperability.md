# Interoperability

GraalVM supports several other programming languages including JavaScript, Python, Ruby, and R.
While GraalVM's implementation of `lli` is designed to run LLVM bitcode, it also provides the API for programming language interoperability that lets you execute code from any other GraalVM-supported language.

Dynamic languages like JavaScript usually access object members by name. Since
normally names are not preserved in LLVM bitcode, it must be compiled with debug
information enabled (the LLVM toolchain shipped with GraalVM will automatically enable
debugging information).

The following example demonstrates how you can use the API for interoperability
with other programming languages.

Define a C struct for points and implement allocation functions in a file named _cpart.c_:

```c
// cpart.c
#include <graalvm/llvm/polyglot.h>

#include <stdlib.h>
#include <stdio.h>

struct Point {
    double x;
    double y;
};

POLYGLOT_DECLARE_STRUCT(Point)

void *allocNativePoint() {
    struct Point *ret = malloc(sizeof(*ret));
    return polyglot_from_Point(ret);
}

void *allocNativePointArray(int length) {
    struct Point *ret = calloc(length, sizeof(*ret));
    return polyglot_from_Point_array(ret, length);
}

void freeNativePoint(struct Point *p) {
    free(p);
}

void printPoint(struct Point *p) {
    printf("Point<%f,%f>\n", p->x, p->y);
}
```

Make sure `LLVM_TOOLCHAIN` resolves to the GraalVM LLVM toolchain (`lli --print-toolchain-path`),
and then compile _cpart.c_  (the graalvm-llvm library defines the polyglot
API functions used in the example):
```shell
$LLVM_TOOLCHAIN/clang -shared cpart.c -lgraalvm-llvm -o cpart.so
```

You can then access this C/C++ code from other languages. For example, save this JavaScript code in the _jspart.js_ file:
```js
// Load and parse the LLVM bitcode into GraalVM
var cpart = Polyglot.evalFile("llvm" ,"cpart.so");

// Allocate a light-weight C struct
var point = cpart.allocNativePoint();

// Access it as if it was a JS object
point.x = 5;
point.y = 7;

// Pass it back to a native function
cpart.printPoint(point);

// Allocate an array of structs
var pointArray = cpart.allocNativePointArray(15);

// Access this array like it was a JS array
for (var i = 0; i < pointArray.length; i++) {
    var p = pointArray[i];
    p.x = i;
    p.y = 2*i;
}

cpart.printPoint(pointArray[3]);

// Additionally, pass a JS object to a native function
cpart.printPoint({x: 17, y: 42});

// Free the unmanaged data objects
cpart.freeNativePoint(point);
cpart.freeNativePoint(pointArray);
```

Finally, run this JavaScript file:
```shell
js --polyglot jspart.js
Point<5.000000,7.000000>
Point<3.000000,6.000000>
Point<17.000000,42.000000>
```

## Polyglot C API

There are also lower level API functions for directly accessing polyglot values
from C. See the [Polyglot Programming](graalvm.org/docs/reference-manual/polyglot-programming/) reference
and the comments in `polyglot.h` for more details.

For example, this program allocates and accesses a Java array from C:
```c
#include <stdio.h>
#include <graalvm/llvm/polyglot.h>

int main() {
    void *arrayType = polyglot_java_type("int[]");
    void *array = polyglot_new_instance(arrayType, 4);
    polyglot_set_array_element(array, 2, 24);
    int element = polyglot_as_i32(polyglot_get_array_element(array, 2));
    printf("%d\n", element);
    return element;
}
```

Compile it to LLVM bitcode:
```shell
$LLVM_TOOLCHAIN/clang polyglot.c -lgraalvm-llvm -o polyglot
```

Then run it, using the `--jvm` argument to run in the JVM mode, since there is a Java type used:
```shell
lli --jvm polyglot
24
```

## Embedding in Java

GraalVM can also be used to embed LLVM bitcode in Java host programs.

For example, write a Java class `Polyglot.java` that embeds GraalVM to
run the previous example:
```java
import java.io.*;
import org.graalvm.polyglot.*;

class Polyglot {
    public static void main(String[] args) throws IOException {
        Context polyglot = Context.newBuilder().
        		               allowAllAccess(true).build();
        File file = new File("polyglot");
        Source source = Source.newBuilder("llvm", file).build();
        Value cpart = polyglot.eval(source);
        cpart.execute();
    }
}
```

Compile it and run:
```shell
javac Polyglot.java
java Polyglot
24
```

See the [Embedding Languages](graalvm.org/docs/reference-manual/embed-languages/) reference for
more information.
