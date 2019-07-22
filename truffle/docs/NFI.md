# The Truffle Native Function Interface

Truffle includes a way to call native functions, called the Native Function
Interface, or NFI. It's implemented as an internal Truffle language that
language implementors can access via the standard polyglot eval interface and
Truffle interop. NFI is intended to be used for example to implement a
language's FFI, or to call native runtime routines that aren't available in
Java.

NFI uses `libffi`. On a standard JVM it calls it using JNI, and on the Graal
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
symbol = library['SDL_GetRevisionNumber']             # load a symbol from the lirbary
function = symbol.bind('():UINT32')                   # bind the symbol to types to create a function
puts function.call # => 12373                         # call the function
```

## Loading libaries

To load a library, we eval a script written in the '`nfi`' language DSL. It
returns an object that represents the loaded library.

```ruby
library = Polyglot.eval('nfi', '...load command...')
```

The load command can be any of these forms:

* `default`
* `load name`
* `load (flag | flag | ...) "filename"`

`default` returns a pseudo-library that contains all symbols already loaded in
the process, equivalent to `RTLD_DEFAULT` in the Posix interface.

`load name` loads a library from a file. You are responsible for any
cross-platform concerns about library naming conventions and load paths.

`load (flag | flag | ...) name` allows you to specify flags to load the
library. For the default backend of NFI (backends will be described later), and
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

Types expressions can be nested arbitrarily deep.

Two additional special types `env` and `OBJECT` are described in the section
on the native API, later in this document.

Types can be written in any case.

You are responsible for any mapping of types from a foreign language such as C
into NFI types.

## Calling native function objects

To call a native function, we execute it.

```ruby
return_value = function.call(...arguments...)
```

## Type marshalling

| Native type | Conversion when an argument | Conversion when a return value |
| --- | --- | --- |
| Primitive types | As you would expect. | As you would expect. |
| Pointers | Interop pointers and primitive integers are converted to a native pointer. | To an interop pointer. |
| Strings | Interop strings are converted to null-terminated UTF-8. Null interop objects are converted to a native null pointer. Native functions should not write to this string. | An interop string which lazily decodes the string as a null-terminated UTF-8. Native null pointers are converted to a null interop object. |
| Arrays | Java arrays are converted to a native array. | Not allowed, as there is no mechanism to determine the length. |
| Functions | Interop executable objects are converted to a native function pointer. | To an executable interop object. |
| Objects | To an opaque native pointer. | From an opaque native pointer back to the interop object. |

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

The special type `env` adds an additional parameter `TruffleEnv *env` to the
signature. An additional simple type `OBJECT` translates to an opaque
`TruffleObject` type.

`trufflenfi.h` provides declarations for working with these types, that can
then be used by the native code called through the NFI. See `trufflenfi.h`
itself for more documentation on this API.

## Other points

Native functions must use the system's standard ABI.
