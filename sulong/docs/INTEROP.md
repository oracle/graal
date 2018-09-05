# Interoperability

Sulong supports standard Polyglot interop messages. This document explains what
it does when it receives them, how to get it to explicitly send them, and what
messages it sends for normal LLVM operations on foreign objects.

Detailed reference documentation of Polyglot interop support in Sulong can be
found in `polyglot.h` (in `mxbuild/sulong-libs/polyglot.h` when building from
source).

## How Sulong responds to messages from other languages

### `HAS_SIZE`, `GET_SIZE`

Values created with `polyglot_from_*_array` behave as polyglot arrays. The size
is explicitly set from the `len` argument.

### `HAS_KEYS`, `KEYS`, `KEY_INFO`

Values created with `polyglot_from_*` behave as objects with named keys. Struct
members are directly translated to member keys. Primitives and pointer values
are readable and writable. Nested structs or arrays are only readable.

### `READ`, `WRITE`

For pointers to structs (created with `polyglot_from_*`), the key must be a
string specifying the member name of the struct. For pointers to arrays (created
with `polyglot_from_*_array`), the key must be an integer number specifying the
array index. The index will be bounds checked.

For struct members of primitive or pointer type, the `READ` message results in a
memory read and the `WRITE` message results in a memory write.

For complex data types (e.g. structs within structs, or arrays of structs),
the `READ` message will do pointer arithmetic to produce a new polyglot value
representing the nested value. `WRITE` is not supported for complex data types.

### `IS_EXECUTABLE`, `EXECUTE`

Function pointers in Sulong respond to the `EXECUTE` message.

## How to explicitly send messages from Sulong

You can use the built-ins defined in `polyglot.h`.

## What messages are sent for LLVM operations on foreign objects

Foreign objects are represented as untyped pointers. The foreign objects can be
accessed from Sulong using various methods:

### primitive arrays

Foreign array values can be accessed by casting them to the corresponding C
pointer type and accessing them. This works for primitive arrays and pointer
arrays.

```
int *array = (int*) value;
int x = array[index];  // sends READ(index), possibly followed by UNBOX
array[index] = value;  // sends WRITE(index, value)
```

### executable objects

Executable foreign objects can be cast to a function pointer type and called.

```
void (*fn)(int) = (void (*)(int)) value;
fn(5);  // sends EXECUTE
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
int x = myStruct->someField;  // sends READ("someField")
myStruct->someField = 5;      // sends WRITE("someField", 5)
```

### explicit access

Other interop messages can be sent directly using the built-ins defined in
`polyglot.h`.
