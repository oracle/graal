# Interoperability

Sulong supports standard Truffle interop messages. This document explains what
it does when it receives them, how to get it to explicitly send them, and what
messages it sends for normal LLVM operations on foreign objects.

## How Sulong responds

### `IS_EXECUTABLE`

Returns `true` only for `LLVMFunctionDescriptor` objects.

### `HAS_SIZE`

Returns true for `LLVMAddress` objects (arrays), although they have no size.
This is a hack, to allow using LLVM arrays via `JavaInterop`.

### `GET_SIZE`

Throws an unsupported exception, even though `HAS_SIZE` returned `true`.

### `READ`

The name must be a Java `int`.

This is implemented for arrays only (`LLVMAddress`), and returns the array's
element with the given index. Bounds are not checked.

### `WRITE`

The name must be a Java `int`.

Stores the value in the array at the given position (works only for
`LLVMAddress`). Bounds are not checked.

## How to explicitly send messages from Sulong

TODO

## What messages are sent for LLVM operations on foreign objects

`object[index]` sends `READ`

`object[index] = value` sends `WRITE`

`object()` sends `EXECUTE`

A reference to a foreign object being converted to an integer (possibly to make
a native call) sends `UNBOX` and treats the returned value as a native memory
address. This behaviour is likely to change in the future, but currently
enables foreign objects to convert to a native representation if possible.

## Intrinsic functions

### `string.h`

Strings are assumed to be non-null terminated.
* `strlen`: Sends a `HAS_SIZE` followed by a `GET_SIZE`.
* `strcmp`: Sends `READ`s for each character, casts them to `char` and
compares them.

## Currently unsupported operations

It is not possible to access members of objects from foreign languages or share
structs with foreign languages.

## Helper functions

`truffle_managed_malloc` has the same signature as `malloc` but gives you
memory in which you can store references to managed objects.
