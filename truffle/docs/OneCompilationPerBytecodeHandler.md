---
layout: docs
toc_group: truffle
link_title: One Compilation per Bytecode Handler
permalink: /graalvm-as-a-platform/language-implementation-framework/one-compilation-per-bytecode-handler/
---
# One Compilation per Bytecode Handler

Interpreter dispatch methods typically contain large switch statements within while-true loops, resulting in a large compilation unit. This pattern often causes register pressure, leading to excessive spilling inside loops and substantial performance degradation.

Putting the handler logic for each bytecode into separate methods (i.e. outlining) can reduce register pressure, but performance remains limited. This is because bytecode handlers frequently access interpreter data structure fields, and these accesses can be hoisted out of the dispatch loop and reused across handlers when not outlined.

GraalVM 25.1 introduces the support for **One Compilation per Bytecode Handler**, addressing these issues through:

- **Parameter expansion**: Converting object fields into standalone parameters while maintaining the aliasing relationships
- **Specialized calling conventions**: Utilizing additional registers for argument passing and returning updated values in the same registers
- **Tail call threading**: Replacing returns with jumps to the next handler

## Example

### 1. Define Your Data Structures

Define the core data structures that the interpreter will use:

- `State` maintains the stack pointer for our operand stack across different handlers.
- `Frame` contains the operand stack and local variables.

```java
public class State {
    int sp;
    
    public State(int sp) {
        this.sp = sp;
    }
}

public class Frame {
    final int[] stack;
    final int[] locals;
    
    public Frame(int[] stack, int[] locals) {
        this.stack = stack;
        this.locals = locals;
    }
}
```

### 2. Implement a Bytecode Handler

Next, implement a handler for the `ADD` bytecode operation which:

1. Pops two integers from the operand stack (stored in `frame.stack`)
2. Adds them together
3. Pushes the result back onto the stack
4. Returns the next program counter position

```java
@BytecodeInterpreterHandler(ADD)
public int addHandler(int pc, State state, short[] bytecode, Frame frame) {
    int b = frame.stack[state.sp--];
    int a = frame.stack[state.sp--];
    frame.stack[++state.sp] = a + b;
    return pc + 1;
}
```

### 3. Configure the Main Dispatch Loop

Define extra properties for the treatment of each parameter of a bytecode handler with a `BytecodeInterpreterHandlerConfig` annotation:

```java
@BytecodeInterpreterSwitch
@BytecodeInterpreterHandlerConfig(maximumOperationCode = LAST_OPCODE, arguments = {
    @Argument, // Denotes `this' pointer
    @Argument(returnValue = true),
    @Argument(expand = VIRTUAL),
    @Argument,
    @Argument(expand = MATERIALIZED, fields = {@Field(name = "stack")})
})
public void dispatchLoop(short[] bytecode) {
    int pc = 0;
    Frame frame = ...;
    State state = new State(0);
    while (true) {
        switch (bytecode[pc]) {
            case ADD:
                pc = this.addHandler(pc, state, bytecode, frame);
                break;
            // ... other cases
        }
    }
}
```

Note that all `BytecodeInterpreterHandler` annotated methods in the same source file must have the same signature, and the signature must have the same number of arguments (include any implicit receiver) as the length of the {@link BytecodeInterpreterHandlerConfig#arguments} array.

The `@Argument` annotations define how each parameter (including receiver for non-static method) is handled:

- **Position 0** (`@Argument`): The receiver `this` is passed normally
- **Position 1** (`@Argument(returnValue = true)`): The `pc` parameter also receives the return value
- **Position 2** (`@Argument(expand = VIRTUAL)`): The `State state` object gets expanded - its `sp` field becomes a separate parameter, and the original object is reconstructed in the stub
- **Position 3** (`@Argument`): The `bytecode` array is passed normally
- **Position 4** (`@Argument(expand = MATERIALIZED, fields = {@Field(name = "stack")})`): The `Frame frame` object is passed normally, but its `stack` field is also passed as a separate parameter

### 4. Understand the Generated Code

The system automatically generates optimized stub code that returns updated arguments in their original locations. Here's what gets created for our `addHandler`:

```java
public static <ReceiverType, int, int, short[], Frame, int[]>
__stub_addHandler(ReceiverType thiz, int pc, int sp, short[] bytecode, Frame frame, int[] stack) {
    State newState = new State(sp); // To be virtualized
    frame.stack    = stack;         // Read aliasing, no write emitted
    newpc          = thiz.addHandler(pc, newState, bytecode, frame); // To be inlined
    return <thiz, newpc, newState.sp, bytecode, frame, frame.stack>;
}
```

### 5. How the Call Site Changes

The original switch case:

```java
case ADD:
    pc = this.addHandler(pc, state, bytecode, frame);  // Original call
    break;
```

is compiled as:

```java
case ADD:
    // Call the optimized stub instead
    pc = __stub_addHandler(this, pc, state.sp, bytecode, frame, frame.stack);
    
    // Extract updated stack pointer from return registers
    state.sp = readRegister(2);  // Retrieved from register per calling convention
    break;
```

The compiler makes these key changes to allow further performance improvement:

1. The dispatch method now calls `__stub_addHandler` instead of `addHandler`
2. Passes `state.sp` and `frame.stack` as separate values; does not pass `state`
3. Upon return, the register storing `sp` in the caller has the updated value.

This transformation makes the code run faster because:
- **Fewer memory accesses** - Critical values stay in CPU registers instead of memory
- **Better optimization** - The compiler can better optimize the separate, smaller compilation units

The compiler handles all this complexity automatically - meaning you can write Java code normally, but it runs much faster.

## Design and Implementation

Traditional bytecode interpreters need fast code, but large switch statements cause performance issues. The solution is to break the large interpreter dispatch loop into smaller pieces that still work together efficiently.

The best approach would be to compile everything at once with unlimited CPU resources, but real computers have limits. So we need to divide the single large compilation unit into smaller, manageable units that can still be optimized as if they were one piece. This means optimizations like escape analysis and moving frequent memory reads out of loops should work across these smaller units.

The best size for these compilation units is individual bytecode handlers. Each handler typically has one entry point and one normal exit, plus possibly an exception exit. By preventing these bytecode handlers from being inlined, we get the split we want.

To move frequent field reads from the handler out of the main loop, we can list the fields as extra parameters for the bytecode handlers. However, this approach can lose important information about how parameters relate to each other. For example, the compiler might forget that parameter `a` is a field of parameter `b`, which makes some optimizations harder.

Also, compared to compiling everything together, this split trades off flexibility. While it allows multiple inputs (the arguments passed to each handler), it only allows one output (the return value).

### Partial Parameter Expansion

To fix the problem of lost relationships between parameters, we use automatic parameter expansion that keeps track of how parameters are connected. For each bytecode handler, the system creates a special stub with extra parameters for frequently used fields. Compiler optimizations understand the relationship between these new parameters and field accesses on the original objects.

In the earlier example, `@Argument(expand = MATERIALIZED, fields = {@Field(name = "stack")})` expands the `Frame frame` parameter into `Frame frame, int[] stack`. The stub knows that `stack` and `frame.stack` refer to the same memory, so it can replace slow memory reads with fast register reads. This moves the memory read from inside the handler to before the call, where it can be moved out of the main loop.

### Full Parameter Expansion

To get multiple return values, we could put them into a single data structure or use wrapper objects to emulate pass-by-reference semantics in C++. However, both approaches add overhead because of memory allocation, writes, and reads between the different compilation units. Escape analysis could eliminate these costs, but it only works within a single compilation unit and requires inlining the stubs.

We use automatic parameter expansion again, this time expanding the wrapper object into all its individual fields without keeping the original parameter. The generated stub creates a copy of the original wrapper object using these field values. So updates to the original wrapper object in the bytecode handler now only affect the copy.

Before returning, the stub reads the updated fields from the copied wrapper objects back to where the caller passes them. The caller can then access these updated values and store them back into the original wrapper object.

In the earlier example, `@Argument(expand = VIRTUAL)` expands the `State state` parameter into just `int sp`. The stub creates a new `State` object and passes it to the original handler. The updated `sp` field is then retrieved and put where the caller can find it. The caller then updates the original `State` object with the new `sp` value.

Escape analysis can work in both the caller and the handler, potentially keeping both wrapper objects in registers instead of memory. With scalar replacement, the field can stay in the same register across different compilation units, effectively extending escape analysis beyond a single compilation unit.

> **Important:** `ExpansionKind.VIRTUAL` arguments don't remain reference-consistent across stub invocations.

### Calling Convention

The automatic parameter expansion technique relies on a specialized calling convention that supports multiple return values by updating parameters in their original locations. For instance, if parameters are passed in the register sequence `rdi, rsi, rdx, rcx, r8, r9`, the updated values are returned in the same register sequence `rdi, rsi, rdx, rcx, r8, r9`.

This calling convention maximizes the use of allocatable registers for parameters, avoiding stack accesses that may otherwise occur under a standard calling convention.

### Tail Call Threading

The `HostCompilerDirectives.markThreadedSwitch(int)` API enables the compiler to apply threaded switch optimization on the switch statement in the interpreter dispatch method, significantly improving branch prediction. This optimization remains beneficial in the current scenario, as control can still jump directly to the next switch case after returning from the stub.

Since the updated parameters are already stored in the desired argument locations, implementing tail call threading is straightforward. The compiler can simply replace the final return instruction with a direct jump to the next handler stub, provided that all handler stubs participating in threading share the same method descriptor. This optimization eliminates not only the call overhead in subsequent stub calls but also the potential need to restore updated fields in the caller, allowing for direct register-based operation.

To get the address of the next handler stub, the compiler needs to:
1. Inject logic for fetching the next opcode
2. Maintain the entries of the bytecode handler stubs in a global method pointer array

The first requirement is fulfilled by annotating a method within the same enclosing class with `@BytecodeInterpreterFetchOpcode`. This method is constrained to have the same method signature (excluding return type) as the bytecode handlers. The compiler injects a call to this method into the generated stub, prior to the return, to fetch the next opcode. The fetched opcode is then used to index into the global method pointer array.

Threading can be stopped early by using `@BytecodeInterpreterHandler(threading=false)`. The compiler will then skip the opcode fetching logic and keep the original return instruction, passing control back to the caller after the handler stub completes. Alternatively, leaving out the `@BytecodeInterpreterHandler` annotation will also stop threading. The compiler fills the corresponding entry in the global method pointer array with a default handler that returns immediately. In this case, control goes back to the caller before the opcode handling logic runs, requiring a re-dispatch on the current opcode. In either case, execution continues in the caller where threading began. Threading may continue in future iterations if a threading-enabled bytecode handler stub is entered.

The flexibility for bytecode handlers to participate in threading facilitates incremental development and accommodates opcode-specific logic that is challenging to unify. For example, the RET opcode often requires a different return type, making it difficult to share the same method descriptor as other handlers.

The following pseudocode shows the bytecode handler stub with threading:

```java
public static <ReceiverType, int, int, short[], Frame, int[]>
__stub_addHandler(ReceiverType thiz, int pc, int sp, short[] bytecode, Frame frame, int[] stack) {
    State newState = new State(sp); // To be virtualized
    frame.stack    = stack;         // Read aliasing, no write emitted
    newpc          = thiz.addHandler(pc, newState, bytecode, frame);     // To be inlined
    nextOpcode     = thiz.fetchOpcode(newpc, newState, bytecode, frame); // To be inlined
    cont           = globalHandlerTable[nextOpcode];
    moveToArgumentLocations(thiz, newpc, newState.sp, bytecode, frame, frame.stack);
    jmp cont;
}
```

This tail call threading implementation imposes the following **restrictions**:

- The `BytecodeInterpreterSwitch` method must not contain additional logic after bytecode handler invocations, as such logic will only be executed after the threading terminates
- All `BytecodeInterpreterHandler` methods called by the same `BytecodeInterpreterSwitch` method must share the same method descriptor and modifiers
- A method annotated with `BytecodeInterpreterFetchOpcode` must be declared in the same enclosing class, have the same signature as `BytecodeInterpreterHandler` methods, and be free of side effects
- Exception handling in the BytecodeInterpreterSwitch method should be made aware that exceptions thrown from handler stubs can be unwound to any threading entry point