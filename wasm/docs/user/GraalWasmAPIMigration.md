# GraalWasm Polyglot API Migration and Usage Guide

In GraalWasm version 25.0, the GraalWasm Polyglot Embedding API has been changed to more closely align with the WebAssembly JS API and its usage patterns.
This document outlines the main differences and serves as a migration guide.

## Overview of the Differences between the Old and New API

| Difference                         | **New API (since 25.0)**                                                                      | **Old API (before 25.0)**                                                 |
|------------------------------------|-----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| **Key Differences**                | Explicit module instantiation. Exports are accessed via the `exports` member of the instance  | Implicit module instantiation. Exports are direct members of the instance |
| **Result of `context.eval(src)`**  | Returns a compiled _module_ object (not yet instantiated) [^3]                                | Returns an already instantiated module _instance_ [^3]                    |
| **Module Instantiation**           | Explicitly instantiated by calling `newInstance()` on the _module_ returned by `context.eval` | Implicitly instantiated by `context.eval()`                               |
| **Repeated Instantiation**         | Same module can be instantiated multiple times per context                                    | Modules can be instantiated only once per context (cached by name)        |
| **Import Linking**                 | Linking is explicit and eager, performed by `module.newInstance()`                            | Linking is implicit and lazy, occurs on first export access               |
| **Start Function Invocation**      | Runs during `module.newInstance()`                                                            | Runs on first export access if not yet linked                             |
| **Accessing Exports**              | Via `instance.getMember("exports").getMember("exportName")`                                   | Via `instance.getMember("exportName")`                                    |
| **Invoking a Function[^1]**        | `instance.getMember("exports").invokeMember("functionName", ...args);`, or:                   | `instance.invokeMember("functionName", ...args)`, or:                     |
| **Invoking a Function[^2]**        | `Value fn = instance.getMember("exports").getMember("functionName"); fn.execute(...args)`     | `Value fn = instance.getMember("functionName"); fn.execute(...args)`      |
| **Module Instance Members**        | The `"exports"` object member                                                                 | The exports of the module _instance_                                      |
| **Importing Host Functions**       | Supported via optional import object passed to `module.newInstance(importObject)`             | Not supported                                                             |
| **Cyclic Import Dependencies**     | Not allowed                                                                                   | Allowed                                                                   |

[^1] Recommended approach for invoking a function once or only a few times: Simply use `exports.invokeMember("functionName", ...args)` (combines lookup and function call).
[^2] Recommended approach for invoking a function repeatedly: First, use `Value function = exports.getMember("functionName");` to look up the function, then call it using `function.execute(...args)`.
[^3] Note: To ease migration, you can set the option `wasm.EvalReturnsInstance=true` to revert to the old `Context.eval` behavior for the time being.

## Module Instantiation

Old approach ≤24.2 (implicit instantiation), or with `wasm.EvalReturnsInstance=true`[^3]:
```java
Source source = Source.newBuilder("wasm", ByteSequence.create(bytes), "example").build();

// Evaluate the Source named "example", returns a module instance (cf. WebAssembly.Instance).
Value instance = context.eval(source);
```

New approach ≥25.0 (explicit instantiation):
```java
Source source = Source.newBuilder("wasm", ByteSequence.create(bytes), "example").build();

// Evaluate the Source named "example", returns a module object (cf. WebAssembly.Module).
Value module = context.eval(source);

// Instantiate the example module (optional: provide an import object).
Value instance = module.newInstance();
Value anotherInstance = module.newInstance();
```

## Accessing Exports

Old approach ≤24.2 (no _exports_ member indirection):
```java
// Compile and instantiate the module.
Value instance = context.eval(source);

// Invoke an exported function.
assert instance.invokeMember("factorial", 6).asInt() == 720;

// Or if you need to call a function multiple times:
Value factorial = instance.getMember("factorial");
assert factorial.execute(7).asInt() == 5040;
assert factorial.execute(8).asInt() == 40320;
```

New approach ≥25.0 (via _exports_ member indirection):
```java
// Compile and instantiate the module
Value module = context.eval(source);
Value instance = module.newInstance();

// Get the exports member from the module instance.
Value exports = instance.getMember("exports");

// Invoke an exported function.
assert exports.invokeMember("factorial", 6).asInt() == 720;

// Or if you need to call a function multiple times:
Value factorial = exports.getMember("factorial");
assert factorial.execute(7).asInt() == 5040;
assert factorial.execute(8).asInt() == 40320;
```

## Importing Host Functions

Old approach ≤24.2: N/A

New approach ≥25.0 (using importObject argument to `newInstance()`):
```java
// Evaluate the example Source, returns a module object (cf. WebAssembly.Module).
Value module = context.eval(exampleSource);

var imports = ProxyObject.fromMap(Map.of(
    "system", ProxyObject.fromMap(Map.of(
        "println",
            (ProxyExecutable) (args) -> {
                String text = (/* read string from memory */);
                System.out.println(text);
                return null; /* for void functions, simply return null */
            }
        ))
    ));

// Instantiate the example module with imports obtained from the `imports` object.
Value instance = module.newInstance(imports);
```


## Comparison with the WebAssembly JS API

| Aspect                     | **WebAssembly JS API**                                                  | **GraalWasm Polyglot API (since 25.0)**                     |
|----------------------------|-------------------------------------------------------------------------|-------------------------------------------------------------|
| **Compilation**            | `WebAssembly.compile(buffer)` or `new WebAssembly.Module(buffer)`       | `context.eval(source)` → returns a compiled *Module* object |
| **Instantiation**          | `WebAssembly.instantiate(module)` or `new WebAssembly.Instance(module)` | `module.newInstance()`                                      |
| **Optional Import Object** | Passed as second argument to `WebAssembly.instantiate(module, imports)` | Passed as first argument to `module.newInstance(imports)`   |
| **Exports Access**         | `instance.exports.exportName`                                           | `instance.getMember("exports").getMember("exportName")`     |
| **Function Invocation**    | `instance.exports.mul(3, 14)`                                           | `instance.getMember("exports").invokeMember("mul", 3, 14)`  |

### Examples

**Compile, instantiate, and call a function of a wasm module:**

JS:
```js
const arrayBuffer = /*...*/;
const module = await WebAssembly.compile(arrayBuffer); // Compile
const instance = await WebAssembly.instantiate(module); // Instantiate

const exports = instance.exports;
let theAnswer = exports.mul(3, 14);
```

Java (GraalWasm ≥25.0):
```java
Source source = Source.newBuilder("wasm", wasmBinary, "example").build();
Value module = context.eval(source); // Compile
Value instance = module.newInstance(); // Instantiate

Value exports = instance.getMember("exports");
int theAnswer = exports.invokeMember("mul", 3, 14).asInt();

// or, alternatively:
Value mul = exports.getMember("mul");
assert mul.execute(6, 7).asInt() == mul.execute(7, 6).asInt();
```

**Instantiate a wasm module with an import object:**

JS:
```js
const imports = {
    console: {
        log: (value) => console.log("The answer is: " + value),
    },
};

const instance = await WebAssembly.instantiate(module, imports);
instance.exports.callLog(42);
```

Java (GraalWasm ≥25.0):
```java
ProxyExecutable log = args -> {
    System.out.println("The answer is: " + args[0].asInt());
    return null;
};
var console = ProxyObject.fromMap(Map.of("log", log));
var imports = ProxyObject.fromMap(Map.of("console", console));

Value instance = module.newInstance(imports);
instance.getMember("exports").invokeMember("callLog", 42);
```
