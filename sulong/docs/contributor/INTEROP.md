# Cross-Language Interoperability

The GraalVM LLVM runtime supports standard Polyglot interop messages. For a general
documentation of Polyglot interop, see
[InteropLibrary](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html).
This document explains what the various interop messages mean in the context of the
GraalVM LLVM runtime.

Detailed reference documentation of Polyglot interop support in the GraalVM LLVM
runtime can be found in [`polyglot.h`](../../projects/com.oracle.truffle.llvm.libraries.bitcode/include/polyglot.h)
(located in `$GRAALVM_HOME/jre/languages/llvm/include/polyglot.h` in the GraalVM
distribution).

To use the functions from `polyglot.h`, binaries have to link against `-lpolyglot-mock`.

## How the LLVM runtime responds to messages from other languages

### `getMembers`, `readMember`, `writeMember`

Values created with `polyglot_from_*` behave as objects with named keys. Struct
members are directly translated to member keys. Primitives and pointer values
are readable and writable. Nested structs or arrays are only readable.

For struct members of primitive or pointer type, the `readMember` message results in a
memory read and the `writeMember` message results in a memory write.

For complex data types (e.g. structs or arrays within structs), the `readMember`
message will do pointer arithmetic to produce a new polyglot value representing
the nested value. `writeMember` is not supported for complex data types.

### `getArraySize`, `readArrayElement`, `writeArrayElement`

Values created with `polyglot_from_*_array` behave as polyglot arrays. The size
is explicitly set from the `len` argument.

The `readArrayElement` and `writeArrayElement` messages behave analogous to
the `readMember` and `writeMember` messages for structs (see above).

### `isExecutable`, `execute`

Function pointers respond to the `execute` message.

Primitive arguments are converted using the `as*` message (e.g. `asInt` for i32).
Typed pointer arguments and return values are implicitly converted using
`polyglot_from_*`/`polyglot_as_*`. Untyped pointer arguments are passed as is.

### `isNull`

The native `NULL` pointer responds with `true`, all other native pointers respond
with `false`.

### `hasIdentity`, `isIdentical`

All pointers have an identity, so `hasIdentity` always returns `true`. Pointers are
considered identical if they point exactly to the same thing. This is exposing
the comparison operation from bitcode (e.g. `==` in C) to foreign languages.

Pointers that point to foreign objects are considered identical if the foreign
objects are `isIdentical`, and they point to the same offset inside the foreign
object. If the foreign object doesn't support identity, they are considered
identical if they are exactly the same object.

## What messages are sent for LLVM operations on foreign objects

Foreign objects are represented as pointers. The foreign objects can be accessed
using various methods:

### executable objects

Executable foreign objects can be cast to a function pointer type and called.

```
void (*fn)(int) = (void (*)(int)) value;
fn(5);  // sends `execute` message
```

### structs or arrays of structs

For accessing user-defined structs, foreign values can be converted to pointers
with explicit type information.

```
struct MyStruct {
  int someField;
};

POLYGLOT_DECLARE_STRUCT(MyStruct)

struct MyStruct *myStruct = polyglot_as_MyStruct(value);
int x = myStruct->someField;  // sends readMember("someField")
myStruct->someField = 5;      // sends writeMember("someField", 5)
```

### explicit access

Other interop messages can be sent directly using the built-ins defined in
`polyglot.h`.

## Simulating native pointers with foreign objects

When implementing the native interface of other languages using the GraalVM LLVM
runtime, it is sometimes necessary to make language objects behave according to
a certain native layout (e.g. `struct pyobject`).

There are two mechanisms for achieving this.

### NativeTypeLibrary

Any foreign objects implementing the `NativeTypeLibrary` will behave as if it were
a native pointer pointing to the type returned by the `getNativeType` message.
The value returned by `getNativeType` should be a `polyglot_typeid` as returned
by a `polyglot_*_typeid` function (see `polyglot.h`).

The foreing object will then behave as if it was cast by `polyglot_as_typed(type, ...)`
to that type.

### `isPointer`/`asPointer`

Objects that respond to the `isPointer` interop message with `true` are treated
as equal to the native pointer that's returned by the `asPointer` message. That is,
they will compare equal to native pointers of that value, or to other foreign
objects that return the same value for `asPointer`.

It is expected that the `asPointer` message is cheap. If it is possible that an
object can be converted to a native pointer, but this has not happened yet, the
object should return `false` to `isPointer`.

### `toNative`

If a pointer to a foreign object has to be converted to the numeric value of that
pointer for any reason, the `toNative` interop message will be sent to the object.
It is expected that the object converts itself to a native form, and if successful
it should from now on respond to `isPointer` with `true`.

Reasons for transforming to native include complex arithmetic (anything that's
more than simple addition, subtraction or bitmasks), or trying to store the pointer
to native memory. Note that a simple conversion to i64 does not force a pointer
to native.
