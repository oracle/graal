# The Truffle Native Function Interface

Truffle includes a way to call native functions, called the Native Function
Interface, or NFI. It's implemented as an internal Truffle language that
language implementors can access via the standard polyglot eval interface and
Truffle interop. NFI is intended to be used for example to implement a
language's FFI, or to call native runtime routines that aren't available in
Java.

NFI uses `libffi`. On a standard JVM it calls it using JNI, and on the GraalVM
Native Image it uses System Java. In the future it may be optimised by the
GraalVM Compiler in native images so that native calls are made directly from
compiled code.

## Stability

The NFI is an internal language designed for language implementors. It is not
considered stable and the interface and beavhiour may change without warning.
It is not intended to be used directly by end-users.

## Basic concepts

The NFI is accessed via the polyglot interface of whatever language you are
using. This could be Java, or could be a Truffle language. This lets you use
the NFI from both your Java language implementation code, or from your guest
language to reduce the ammount of Java that you need to write.

The entry point is the polyglot `eval` interface. This runs a special DSL, and
returns Truffle interop objects which can then expose more methods.

We'll write examples using Ruby's polyglot interface, but any other JVM or
Truffle language could be used instead.

## Basic example

Here's a full basic working example, before we go into the details:

```ruby
library = Polyglot.eval('nfi', 'load "libSDL2.dylib"')  # load a library
symbol = library['SDL_GetRevisionNumber']               # load a symbol from the lirbary
function = symbol.bind('():UINT32')                     # bind the symbol to types to create a function
puts function.call # => 12373                           # call the function
```

## Loading libaries

To load a library, we eval a script written in the '`nfi`' language DSL. It
returns an object that represents the loaded library.

```ruby
library = Polyglot.eval('nfi', '...load command...')
```

The load command can be any of these forms:

* `default`
* `load "filename"`
* `load (flag | flag | ...) "filename"`

`default` returns a pseudo-library that contains all symbols already loaded in
the process, equivalent to `RTLD_DEFAULT` in the Posix interface.

`load "filename"` loads a library from a file. You are responsible for any
cross-platform concerns about library naming conventions and load paths.

`load (flag | flag | ...) "filename"` allows you to specify flags to load the
library. For the default backend (backends will be described later), and
when running on a Posix platform, the flags available are `RTLD_GLOBAL`,
`RTLD_LOCAL`, `RTLD_LAZY`, and `RTLD_NOW`, which have the conventional Posix
semantics. The default is `RTLD_NOW` if neither `RTLD_LAZY` nor `RTLD_NOW`
were specified.

## Loading symbols from libraries

To load a symbol from a library, we read the symbol as a property from the
library object we previously loaded.

```ruby
symbol = library['symbol_name']
```

## Producing native function objects from symbols

To get an executable object that we can call in order to call the native
function, we *bind* the symbol object that we previously loaded, by calling
the `bind` method on it. We supply a type signature that needs to match the
native function's actual type signature.

```ruby
function = symbol.bind('...signature...')
```

The format of the signature is `(arg, arg, ...) : return`, where `arg` and
`return` are types.

Types can be one of the simple types:

* `VOID`
* `UINT8`
* `SINT8`
* `UINT16`
* `SINT16`
* `UINT32`
* `SINT32`
* `UINT64`
* `SINT64`
* `FLOAT`
* `DOUBLE`
* `POINTER`
* `STRING`
* `OBJECT`
* `ENV`

Array types are formed by placing another type in square brackets. For example
`[UINT8]`. These are C-style arrays.

Function pointer types are formed by writing a nested signature. For example
the signature of `qsort` would be
`(POINTER, UINT64, UINT64, (POINTER, POINTER) : SINT32) : VOID`.

For a function with a signature with variadic arguments, you specify `...`
where the variadic arguments start, but then you must specify the actual types
that you will be calling the function with. You may therefore need to bind the
same symbol multiple times in order to call it with different types or a
different number of arguments. For example to call `printf` with `%d %f` you
would use the type signature `(STRING, ...SINT32, DOUBLE) : SINT32`.

Type expressions can be nested arbitrarily deep.

Two additional special types `ENV` and `OBJECT` are described in the section
on the native API, later in this document.

Types can be written in any case.

You are responsible for any mapping of types from a foreign language such as C
into NFI types.

## Calling native function objects

To call a native function, we execute it.

```ruby
return_value = function.call(...arguments...)
```

## Calling back from native code to managed functions

Using nested signatures, a function call can get function pointers as arguments.
The managed caller needs to pass a Polyglot executable object, that will be
converted to a native function pointer. When calling this function pointer from
the native side, the `execute` message is sent to the Polyglot object.

```C
void native_function(int32_t (*fn)(int32_t)) {
  printf("%d\n", fn(15));
}
```

```ruby
native_function = library['native_function'].bind("((SINT32):SINT32):VOID")
native_function.call(->(x) { x + 1 })
```

The arguments and return values of callback functions are converted the same as
for regular function calls, with the conversion in the other direction, i.e.
arguments are converted from native to managed, and return values are converted
from managed to native.

Callback function pointers can themselves have function pointer arguments. That
works as you would expect: The function accepts a native function pointer as
argument, and it is converted to a Truffle executable object. Sending the
`execute` message to that object calls the native function pointer, same as
calling a regular NFI function.

Function pointer types are also supported as return types.

## Combined loading and binding

You can optionally combine loading a library with loading symbols and binding
them. This is achieved with an extended `load` command, which then returns an
object with the already bound functions as methods.

These two examples are equivalent:

```ruby
library = Polyglot.eval('nfi', 'load libSDL2.dylib')
symbol = library['SDL_GetRevisionNumber'] 
function = symbol.bind('():UINT32')
puts function.call # => 12373
```

```ruby
library = Polyglot.eval('nfi', 'load libSDL2.dylib { SDL_GetRevisionNumber():UINT32; }')
puts library.SDL_GetRevisionNumber # => 12373
```

The definitions in the curly braces `{}` can contain multiple function
bindings, so that many functions can be loaded from a library at once.

## Backends

The load command can be prefixed by `with` in order to select a specific NFI
backend. Muliple NFI backends are available. The default is called `native`,
and will be used if there is no `with` prefix, or the selected backend is not
available.

Depending on the configuration of components you are running, available
backends may include:

* `native`
* `llvm`, which uses the GraalVM LLVM runtime to run the 'native' code

## Native API

The NFI can be used with unmodified, already compiled native code, but it can
also be used with a Truffle specific API being used by the native code.

The special type `ENV` adds an additional parameter `TruffleEnv *env` to the
signature. An additional simple type `OBJECT` translates to an opaque
`TruffleObject` type.

`trufflenfi.h` provides declarations for working with these types, that can
then be used by the native code called through the NFI. See `trufflenfi.h`
itself for more documentation on this API.

## Type marshalling

This section describes in detail how argument values and return values are
converted for all types in the function signature.

The following table shows the possible types in NFI signatures with their
corresponding C language types on the native side, and what polyglot values
these arguments map to on the managed side:

| NFI type         | C language type                         | Polyglot value                                                       |
| ---              | ---                                     | ---                                                                  |
| `VOID`           | `void`                                  | Polyglot object with `isNull == true` (only valid as return type).   |
| `SINT8/16/32/64` | `int8/16/32/64_t`                       | Polyglot `isNumber` that `fitsIn...` the corresponding integer type. |
| `UINT8/16/32/64` | `uint8/16/32/64_t`                      | Polyglot `isNumber` that `fitsIn...` the corresponding integer type. |
| `FLOAT`          | `float`                                 | Polyglot `isNumber` that `fitsInFloat`.                              |
| `DOUBLE`         | `double`                                | Polyglot `isNumber` that `fitsInDouble`.                             |
| `POINTER`        | `void *`                                | Polyglot object with `isPointer == true` or `isNull == true`.        |
| `STRING`         | `char *` (zero-terminated UTF-8 string) | Polyglot `isString`.                                                 |
| `OBJECT`         | `TruffleObject`                         | Arbitrary object.                                                    |
| `[type]`         | `type *` (array of primitive)           | Java host primitive array.                                           |
| `(args):ret`     | `ret (*)(args)` (function pointer type) | Polyglot function with `isExecutable == true`.                       |
| `ENV`            | `TruffleEnv *`                          | nothing (injected argument)                                          |

The following sections describe the type conversions in detail.

The type conversion behavior with function pointers can be slightly confusing,
because the direction of the arguments is reversed. When in doubt, always try to
figure out in which direction arguments or return values flow, from managed to
native or from native to managed.

### `VOID`

This type is only allowed as return type, and is used to denote functions that
do not return a value.

Since in the Polyglot API, all executable objects have to return a value, a
Polyglot object with `isNull == true` will be returned from native functions
that have a `VOID` return type.

The return value of managed callback functions with return type `VOID` will
be ignored.

### Primitive numbers

The primitive number types are converted as you might expect. The argument needs
to be a Polyglot number, and its value needs to fit in the value range of the
specified numeric type.

One thing to note is the handling of the unsigned integer types. Even though the
Polyglot API does not specify separate messages for values fitting in unsigned
types, the conversion is still using the unsigned value ranges. For example, the
value `0xFF` passed from native to managed through a return value of type `SINT8`
will result in a Polyglot number `-1`, which `fitsInByte`, but the same value
returned as `UINT8` results in a Polyglot number `255`, which does *not*
`fitsInByte`. Also, passing `-1` to an argument of type `UINT8` is a
type error, but passing `255` is allowed, even though it does not `fitsInByte`.

Since in the current Polyglot API it is not possible to represent numbers
outside of the signed 64-bit range, the `UINT64` type is currently handled with
*signed* semantics. This is a known bug in the API, and will change in a future
release.

### `POINTER`

This type is a generic pointer argument. On the native side, it does not matter
what exact pointer type the argument is.

Polyglot object passed to `POINTER` arguments will be converted to a native
pointer if possible (using the `isPointer`, `asPointer` and `toNative` messages
as necessary). Objects with `isNull == true` will be passed as a native `NULL`.

`POINTER` return values will produce a Polyglot object with `isPointer == true`.
The native `NULL` pointer will additionally have `isNull == true`.

In addition, the returned pointer object will also have a method `bind`, and
behaves the same as symbols loaded from an NFI library. When calling `bind` on
such a pointer, it is the user's responsibility that to ensure that the pointer
really points to a function with a matching signature.

### `STRING`

This is a pointer type with special conversion semantics for strings.

Polyglot strings passed from managed to native using the `STRING` type will be
converted to a zero-terminated UTF-8 encoded string. For `STRING` arguments, the
pointer is owned by the caller, and is guaranteed to stay alive for the duration
of the call only. `STRING` values returned from managed function pointers to a
native caller are also owned by the caller. They have to be freed with `free`
after use.

Polyglot pointer values or null values can also be passed to `STRING` arguments.
The semantics is the same as for `POINTER` arguments. The user is responsible
for ensuring that the pointer is a valid UTF-8 string.

`STRING` values passed from native functions to managed code behave like
`POINTER` return values, but in addition they have `isString == true`. The user
is responsible for the ownership of the pointer, it might be necessary to `free`
the return value, depending on the semantics of the called native function.
After freeing the returned pointer, the returned polyglot string is invalid and
reading it results in undefined behavior. In that sense, the returned Polyglot
string is not a safe object, similar to a raw pointer. It is recommented that
the user of the NFI copies the returned string before passing it along to
untrusted managed code.

### `OBJECT`

This argument corresponds to the C type `TruffleObject`. This type is defined
in `trufflenfi.h`, and is an opaque pointer type. A value of type
`TruffleObject` represents a reference to an arbitrary managed object.

Native code can do nothing with values of type `TruffleObject` except passing
them back to managed code, either through return values or passing them to
callback function pointers.

The lifetime of `TruffleObject` references needs to be managed manually. See the
documentation in `trufflenfi.h` for API functions to manage the lifetime of
`TruffleObject` references.

A `TruffleObject` passed as argument is owned by the caller, and guaranteed to
stay alive for the duration of the call. A `TruffleObject` reference returned
from a callback function pointer is owned by the caller, and needs to be freed
after use. Returning a `TruffleObject` from a native function does *not*
transfer ownership (but there is an API function in `trufflenfi.h` to do that).

### `[...]` (native primitive arrays)

This type is only allowed as an argument from managed code to a native function,
and only arrays of primitive numeric types are supported.

On the managed side, only Java host objects containing a Java primitive array
are supported. On the native side, the type is a pointer to the contents of the
array. It is the user's responsibility to pass along the array length as a
separate argument.

The pointer is valid for the duration of the native call only.

Modifications to the contents are propagated back to the Java array after
returning from the call. The effects of concurrent access to the Java array
during the native call are unspecified.

### `(...):...` (function pointer)

On the native side, a nested signature type corresponds to a function pointer
with the given signature, calling back to managed code.

Polyglot executable objects passed from managed to native using a function
pointer type be converted to a function pointer that can be called by the native
code. For function pointer arguments, the function pointer is owned by the
caller, and is guaranteed to stay alive for the duration of the call only.
Function pointers return values are owned by the caller, and have to be freed
manually. See `polyglot.h` for API functions to managed the lifetime of function
pointer values.

Polyglot pointer values or null values can also be passed to function pointer
arguments. The semantics is the same as for `POINTER` arguments. The user is
responsible for ensuring that the pointer is a valid function pointer.

Function pointer return types are the same as regular `POINTER` return types,
but in addition they are already *bound* to the given signature type. They
support the `execute` message, and behave the same as regular NFI functions.

### `ENV`

This type is a special argument of type `TruffleEnv *`. It is only valid as
argument type, not as a return type. It is an injected argument on the native
side, there is no corresponding argument on the managed side.

When used as argument type of a native function, the native function will get
an environment pointer on this position. That environment pointer can be used
to call API functions (see `trufflenfi.h`). The argument is injected, for
example, if the signature is `(SINT32, ENV, SINT32):VOID`, this function object
is expected to be called with two integer arguments, and the corresponding
native function will be called with three arguments, first the first real
argument, then the injected `ENV` argument, and then the second real argument.

When the `ENV` type is used as argument type for a function pointer parameter,
that function pointer must be called with a valid NFI environment as argument.
If the caller already has an environment, threading it through to callback
function pointers is more efficient than calling them without an `ENV` argument.

## Other points

Native functions must use the system's standard ABI.
