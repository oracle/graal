---
layout: docs
toc_group: truffle
link_title: Deoptimization Cycle Patterns
permalink: /graalvm-as-a-platform/language-implementation-framework/DeoptCyclePatterns/
---

# Deoptimization Cycle Patterns

As of GraalVM for JDK 25, the Truffle framework includes an **automatic deoptimization cycle detection feature**, which is a powerful tool for identifying existing deoptimization cycles.
The goal of this document is to help prevent deoptimization cycles by describing common patterns that can lead to such cycles, which should be avoided.

### Always Deoptimizing Node

One of the simplest patterns that causes a deoptimization cycle is as follows:

```java
class AlwaysDeoptNode extends RootNode {
    @Override
    public Object execute(VirtualFrame frame) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return null;
    }
}
```

When the above code is compiled, it deoptimizes and invalidates on the first execution.
The same occurs with each subsequent compilation and execution.
This is an extreme example, and it is hard to imagine how the code could be useful for anything other than testing purposes, but it is a clear cause of unbounded repeated deoptimization—that is, a deoptimization cycle.

### Invalid Argument Profiling Node

The following is an example of non-stabilizing argument profiling:

```java
class InvalidArgumentProfilingNode extends RootNode {
    @CompilerDirectives.CompilationFinal int cachedValue;

    @Override
    public Object execute(VirtualFrame frame) {
        int arg = (int) frame.getArguments()[0];
        if (this.cachedValue != arg) {
            // Bug: repeated non-stabilizing deoptimization
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.cachedValue = arg;
        }
        return this.cachedValue;
    }
}
```

The number of deoptimizations and invalidations in the above code is unbounded because the profiled argument can keep changing with no limit on the number of these changes.
Consequently, there is no bound on the number of deoptimizations.

A possible solution to this problem would be to use `IntValueProfile`, which caches the value only once and switches to a generic state when the value changes.

### Stabilize Late Node

The following code does not cause a true deoptimization cycle.
It eventually stabilizes, but stabilization occurs so late that the deoptimization cycle detection tool might still report a cycle:

```java
class StabilizeLateNode extends RootNode {

    static final int GENERIC = Integer.MIN_VALUE;

    @CompilationFinal int cachedValue;
    int seenCount = 0;

    @Override
    public Object execute(VirtualFrame frame) {
        int arg = (int) frame.getArguments()[0];
        // we assume Integer.MIN_VALUE is never used in value
        assert arg != Integer.MIN_VALUE;

        int result;
        if (cachedValue == GENERIC) {
            result = arg;
        } else {
            if (cachedValue != arg) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (seenCount < 20) {
                    this.cachedValue = arg;
                } else {
                    cachedValue = GENERIC;
                }
                seenCount++;
                result = arg;
            } else {
                result = cachedValue;
            }
        }
        return result;
    }
}
```

After 20 consecutive executions where the argument `arg` is different from `cachedValue`, the variable `seenCount` reaches 20. At this point, deoptimizations stop.
Until then, the code may be compiled and deoptimized once for each `seenCount` value below 20—up to twenty times.
Since deoptimization cycle detection is enabled by default after 15 compilations, it could be triggered by this late-stabilizing code.

The solution to this problem is to ensure that the optimizations stabilize more quickly.

### Compilation Final Field of a Non-Constant Object

Truffle compilation final fields are a powerful optimization tool, often used to control speculations.
For example, they are used in conditions checking whether a certain specialized piece of code is valid for the current input.
When it is not, the compiled code is deoptimized and invalidated. This allows the code to re-specialize and recompile with the updated assumption.
However, this usage must follow one key rule:

* The number of possible deoptimizations (and invalidations) caused by the condition involving a compilation final field should be small. As seen in the previous examples, failing to follow this rule can lead to issues.

Extra caution must be taken when the object containing the compilation field is not a partial evaluation (PE) constant because, in that case, the field is not compilation-final, and reading it translates to a normal field read.
This makes it more difficult to reason about the number of possible deoptimizations caused by a condition involving the field.

The following examples demonstrate what can happen if this caution is not exercised.

#### Example 1: Non-Constant Language Context

The following code shows repeated deoptimization caused by the initialization of each new language context:

```java
class LanguageContext {
    private final Env env;

    @CompilationFinal boolean initialized;

    LanguageContext(Env env) {
        this.env = env;
    }

    void ensureInitialized() {
        if (!initialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initialize();
        }
    }

    private void initialize() {
        // perform initialization
        initialized = true;
    }

    static final ContextReference<LanguageContext> REF = ContextReference.create(MyLanguage.class);
}

class LanguageContextDrivenInitialization extends RootNode {
    @Override
    public Object execute(VirtualFrame frame) {
        LanguageContext languageContext = LanguageContext.REF.get(this);
        languageContext.ensureInitialized();
        // ...
        return null;
    }
}
```

If `TruffleLanguage.Registration.contextPolicy` of `MyLanguage` is `SHARED`, a new language context is created for each polyglot context used to run the language code.
Therefore, `languageContext` is not a PE constant and so the number of deoptimizations (and invalidations) driven by the `initialized` boolean is unbounded.

A possible solution for this problem would be to transfer to the interpreter only when `languageContext` is a PE constant, and to annotate the initialize method with `TruffleBoundary`:

```java
class LanguageContext {
    private final Env env;

    @CompilationFinal boolean initialized;

    LanguageContext(Env env) {
        this.env = env;
    }

    void ensureInitialized() {
        if (!initialized) {
            if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            initialize();
        }
    }

    @TruffleBoundary
    void initialize() {
        // perform initialization
        initialized = true;
    }

    static final ContextReference<LanguageContext> REF = ContextReference.create(MyLanguage.class);
}

class LanguageContextDrivenInitialization extends RootNode {
    @Override
    public Object execute(VirtualFrame frame) {
        LanguageContext languageContext = LanguageContext.REF.get(this);
        languageContext.ensureInitialized();
        // ...
        return null;
    }
}
```

Annotating the method with `TruffleBoundary` has the downside of being slow when `languageContext` is not a PE constant, as it would always produce a runtime call after compilation.
Another solution could be to perform the initialization eagerly, which would avoid the problem entirely.

#### Example 2: Dispatch Node

The following code shows repeated deoptimization caused by the initialization of call targets in a dispatch node:

```java
class MyFunction {
    private final RootNode root;
}

class DispatchNode extends RootNode {

    @Child IndirectCallNode callNode = Truffle.getRuntime().createIndirectCallNode();

    @Override
    public Object execute(VirtualFrame frame) {
        MyFunction function = (MyFunction) frame.getArguments()[0];
        CallTarget target = function.root.getCallTarget();
        return callNode.call(target);
    }

}

// Simplified version of the RootNode class from the Truffle framework. It is not complete, only relevant parts are listed.
class RootNode {
    @CompilationFinal private volatile RootCallTarget callTarget;

    public final RootCallTarget getCallTarget() {
        if (callTarget == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initializeTarget();
        }
        return callTarget;
    }
}
```

The dispatch node has a single call site, and each uninitialized call target must be initialized before the call.
Initialization is performed in the `getCallTarget` method if the compilation final field `callTarget` on the callee's `RootNode` object is `null`.
The callee’s root node object is not a PE constant.
In fact, the number of root nodes the compiled code can see is theoretically unbounded.
Initializing a call target causes a deoptimization, so the entire pattern leads to a deoptimization cycle.

A possible solution to this problem would be to initialize the call target at parse time or when the function is looked up for the first time.
`MyFunction` objects would then store the call targets directly:

```java
class MyFunction {
    private final CallTarget target;
}

class DispatchNode extends RootNode {

    @Child IndirectCallNode callNode = IndirectCallNode.create();

    @Override
    public Object execute(VirtualFrame frame) {
        MyFunction funcion = (MyFunction) frame.getArguments()[0];
        CallTarget target = function.target;
        return callNode.call(target);
    }

}
```

### Skipped Exceptions

The following code causes a deoptimization by throwing a skipped exception:

```java
class SkippedExceptionNode extends RootNode {

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            throw new IndexOutOfBoundsException();
        } catch (RuntimeException e) {
            //
        }
        return null;
    }
}
```

Skipped exceptions are exceptions that always cause a deoptimization.
The complete list is as follows:
* `UnexpectedResultException`
* `SlowPathException`
* `ScopedMemoryAccess$ScopedAccessError`
* `ArithmeticException`
* `IllegalArgumentException`
* `IllegalStateException`
* `VirtualMachineError`
* `IndexOutOfBoundsException`
* `ClassCastException`
* `BufferUnderflowException`
* `BufferOverflowException`
* `ReadOnlyBufferException`
* `AssertionError`

As you can see from the example, catching the exception does not help.
Whenever a skipped exception is thrown in Truffle-compiled code, the code is deoptimized and invalidated, and repeated execution of such code leads to a deoptimization cycle.

The solution to this problem is to ensure that skipped exceptions are never thrown from compiled code. You can achieve this by either:
* Throwing the exception behind `TruffleBoundary`, or
* Using explicit guards to guarantee that the exception cannot be thrown in compiled code.
