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

Define extra properties for the treatment of each parameter of a bytecode handler with a `BytecodeInterpreterHandlerConfig` annotation. If an interpreter is split across multiple methods, ensure that every switch method in the interpreter is annotated with the same `BytecodeInterpreterHandlerConfig`:

```java
@BytecodeInterpreterSwitch()
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
        int opcode = bytecode[pc];
        switch (opcode) {
            case ADD:
                pc = this.addHandler(pc, state, bytecode, frame);
                break;
            // ... other cases
            // (optional) dispatch to helper method with remaining cases:
            default:
                pc = dispatchLoop_1(pc, state, bytecode, frame, opcode);
                break;
        }
    }
}

@EarlyInline
@BytecodeInterpreterSwitch()
@BytecodeInterpreterHandlerConfig(maximumOperationCode = LAST_OPCODE, arguments = {
    @Argument, // Denotes `this' pointer
    @Argument(returnValue = true),
    @Argument(expand = VIRTUAL),
    @Argument,
    @Argument(expand = MATERIALIZED, fields = {@Field(name = "stack")})
})
private int dispatchLoop_1(int pc, State state, short[] bytecode, Frame frame, int opcode) {
    switch (opcode) {
        case SUB:
            pc = this.subHandler(pc, state, bytecode, frame);
            break;
        // ... additional split opcodes
        default:
            throw new IllegalArgumentException("Unknown opcode: " + opcode);
    }
    return pc;
}
```

Note that all `BytecodeInterpreterHandler` annotated methods in the same source file must have the same signature, and the signature must have the same number of arguments (include any implicit receiver) as the length of the {@link BytecodeInterpreterHandlerConfig#arguments} array.
When the interpreter is split across multiple `@BytecodeInterpreterSwitch` methods, these helper methods can still inline back into the interpreter root, but the `@BytecodeInterpreterHandler` methods themselves remain outlined.

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

On Native Image, use `-H:Dump=:2 -H:MethodFilter="__stub_*" -H:+TrackNodeSourcePosition` to dump the host compilation graphs for all handlers with source positions.

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

This calling convention maximizes the use of allocatable registers for parameters, avoiding stack accesses that may otherwise occur under a standard calling convention. However, bytecode handler parameters still cannot consume the full register set: tail call threading needs one register to hold the next handler target, so the practical parameter budget is at most `MAX_REGISTERS - 1`. The effective budget can be lower still when the architecture-specific base pointer register (for example `rbp` on AMD64) must remain unavailable, such as when stack-pointer-modifying code requires frame-pointer preservation.

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
- All `BytecodeInterpreterHandler` methods participating in the same interpreter must share the same method descriptor and modifiers
- A method annotated with `BytecodeInterpreterFetchOpcode` must be declared in the same enclosing class, have the same signature as `BytecodeInterpreterHandler` methods, and be free of side effects
- Exception handling in the `BytecodeInterpreterSwitch` method should account for exceptions thrown from handler stubs being unwound to any threading entry point.

### Template

The Template feature, used in conjunction with tail call threading, enables the creation of multiple variants of a bytecode handler. To utilize this feature, identify an int field of a `VIRTUAL` argument as the _template variable_ using `@Field(templateVariable = N)`, where `N >= 2` is the number of variants for that field. The following example uses a simple int-only top-of-stack cache with three states:

```java
@BytecodeInterpreterHandlerConfig(maximumOperationCode = RET, arguments = {
    @Argument, // receiver
    @Argument(returnValue = true),
    @Argument(expand = VIRTUAL, fields = {
        @Field(name = "tos0", scratch = true),
        @Field(name = "tos1", scratch = true),
        @Field(name = "level", templateVariable = 3)
    }),
    @Argument,
    @Argument(expand = MATERIALIZED)
})
public int execute(short[] bytecode) {
    ...
}

public final class IntTosStack {
    final int[] stack;
    int sp;
    int tos0;
    int tos1;
    int result;
    private int level;
}
```

Only fields with a property need to be listed in `fields`: `tos0` and `tos1` are scratch state, while `level` is the template variable. The other `IntTosStack` fields, such as `sp` and `result`, still participate in `VIRTUAL` expansion. The program model is that the virtual-expanded instance fields are the interpreter state carried from one handler stub to the next. Non-template fields can live in physical registers. The template field is different: it selects the handler variant and is initialized as a constant on entry to that variant. Scratch fields are carried between threaded handler stubs, but they are initialized to their default value when entering from the Java caller and are not written back to the original Java object or pending exception state when threading exits.

The three states are:

- `level == 0`: no cached operand; operands are in the backing stack array
- `level == 1`: `tos0` holds the top int operand
- `level == 2`: `tos0` and `tos1` hold the top two int operands, with `tos1` at the top

When branching to the next bytecode handler in the tail call threading, you can assign a new constant value to the _template variable_ to specify which variant of the next bytecode handler to execute. If no new value is assigned, the template variable retains its current constant value.

Multiple template variables are also supported. The total number of handler variants is the product of all configured variant counts. Template variables are encoded in expanded-field order using mixed-radix indexing; for example, `@Field(name = "lvl", templateVariable = 3)` followed by `@Field(name = "mode", templateVariable = 2)` creates six variants with flat index `lvl + mode * 3`. Each template variable value must be in the range `[0, N)`, where `N` is that field's `templateVariable` value.

At a threaded dispatch, each template variable value must be a constant or a phi whose inputs recursively resolve to constants. If multiple template variables are non-constant phis at the same dispatch, those phis must be produced by the same merge. Handlers that update different template variables through independent branch merges are rejected. Normalize the template values before dispatch, or update the variables through the same control-flow merge.

Template variables do not occupy stub ABI argument slots. The selected stub variant initializes them as constants on entry, and their value on exit selects the variant of the next threaded handler.

By default, every `@BytecodeInterpreterHandler` is considered compatible with non-zero template states. A handler can opt out with `@BytecodeInterpreterHandler(templateCompatible = false)`. The compiler still creates the handler variants, but non-zero template handler tables leave that handler's opcode entries pointing at the generated fallback stub, so control returns to the Java switch loop before the handler executes.

The following state machine illustrates this top-of-stack cache:

```
                  +-----+
                  | pop |
    +---------------+   |
    |    level 0    |<--+    // memory stack only
    +---------------+
      |           ^
      | push      | pop
      v           |
    +---------------+
    |    level 1    |        // tos0 is valid
    +---------------+
      |           ^
      | push      | pop
      v           |
    +---------------+
+-->|    level 2    |        // tos0 and tos1 are valid
|   +---------------+
|     | push
+-----+
```

In practice, it is common to encapsulate these state transitions within individual helper methods and force their inlining. For instance, the _template variable_ `level` can be made private and all updates can go through `push`, `pop`, and result helpers:

```java
public final class IntTosStack {
    final int[] stack;
    int sp;

    int tos0;
    int tos1;
    int result;

    private int level;

    public void push(int value) {
        switch (level) {
            case 0:
                tos0 = value;
                level = 1;
                break;
            case 1:
                tos1 = value;
                level = 2;
                break;
            case 2:
                stack[sp++] = tos0;
                tos0 = tos1;
                tos1 = value;
                level = 2;
                break;
            default:
                throw new AssertionError(level);
        }
    }

    public int pop() {
        switch (level) {
            case 0:
                return stack[--sp];
            case 1:
                level = 0;
                return tos0;
            case 2:
                level = 1;
                return tos1;
            default:
                throw new AssertionError(level);
        }
    }

    public int peek() {
        switch (level) {
            case 0:
                return stack[sp - 1];
            case 1:
                return tos0;
            case 2:
                return tos1;
            default:
                throw new AssertionError(level);
        }
    }
}
```

The bytecode handlers can then be written against the logical operand stack. For example:

```java
@BytecodeInterpreterHandler(ICONST_0)
public long iconst0Handler(long pc, IntTosStack stack, short[] bytecode, Frame frame) {
    stack.push(0);
    return pc + 1;
}

@BytecodeInterpreterHandler(IADD)
public long iaddHandler(long pc, IntTosStack stack, short[] bytecode, Frame frame) {
    int b = stack.pop();
    int a = stack.pop();
    stack.push(a + b);
    return pc + 1;
}

@BytecodeInterpreterHandler(value = IRETURN, threading = false)
public long ireturnHandler(long pc, IntTosStack stack, short[] bytecode, Frame frame) {
    stack.result = stack.peek();
    return pc;
}
```

When the compiler generates the variants, the _template variable_ is initialized with a constant value. As the bytecode handler executes, each call to the push and pop operations updates `level` to a next constant, enabling the compiler to eliminate dead code through constant folding and propagation. For example, the `IADD` handler in `level == 2` can fold the two pops and one push into direct operations on the expanded fields, with no backing stack access on that path.

The bytecode handler is expected to exit with a constant `level`, or with a supported phi of constants, which is used to determine which variant of the next bytecode handler should execute. Unsupported non-constant template shapes are rejected instead of falling back to dynamic template selection.

When control returns to the Java switch loop, main dispatch also selects the handler variant from the current template-variable values. This means a normal interpreter exit does not need to write scratch state back to the virtual object only to re-enter variant 0. Instead, model exits that consume scratch or template state as ordinary bytecode handlers. In the previous example, `IRETURN` is a non-threaded handler that observes the active TOS state and stores the logical result before returning to Java code.

Tail call threading can still exit before a handler reaches its normal return. For example, an incompatible bytecode can force dispatch through a generated default stub, or a handler can throw. These exits follow the same state model as ordinary Java execution: there is no hidden cleanup callback. State that must be visible to Java code after a normal bytecode exit should be produced by an explicit handler, as shown by `IRETURN`. Scratch fields are not written back to the original Java object or pending exception state.
