# Java bytecode interpreter for SubstrateVM

## How execution is diverged from compiled code to interpreter

Execution of a native-image binary will start out in AOT compiled code as usual.
Execution of specific methods can be diverted into the interpreter via `InterpreterDirectives`.

There are two cases to consider:

1. There is an activation frame on a thread stack: We have to deoptimize that frame so that whenever execution returns to this activation frame it will transfer execution to the interpreter.
2. Whenever a call to this method is done, we need to make sure that its execution will happen in the interpreter.


For 1. the procedure is similar to the `Deoptimizer` implemented for run-time compiled methods: We patch the return address in the upper frame, so that it points to a deopt stub that will do the deoptimization of the target frame. Once the deopt stub executes it collects the locals and arguments on the native frame accordingly, and tail calls into a small stub that will create a new frame and resumes execution in the interpreter with the right `bci` and locals in place.

For 2. we piggyback on the PLT+GOT work done in the context of code compression (see GR-40476).  That means every call has an extra indirection through a PLT stub.  There are other approaches that work on some platforms (e.g., map `.text` section `rwx` on Linux and patch the prologue or call-sites accordingly), that will be reconsidered once interpretation is enabled by default.

### Transition from compiled code to interpreter (`c2i`)

If we want a method to be executed in the interpreter, we can update its slot in the GOT accordingly, since each call-site for that method will go through the PLT+GOT mechanism. But _what_ do we patch there? For each method that we want to run in the interpreter we allocate an `InterpreterMethod` at image build-time and store it in a table. We then create a `interp_enter_trampoline` that injects its index to the `InterpreterMethod` as a hidden argument.

```c
interp_enter_trampoline:
	mov $interp_reg, <index to InterpreterMethod>
	tailcall interp_enter_stub
```

The `interp_enter_stub` then collects all ABI registers into a `InterpreterEnterData` stackvalue:

```c
struct InterpreterEnterData {
	InterpreterMethod method;
	Pointer stack;
	Word abi_arg_gp_regs[];
	Word abi_arg_fp_regs[];
	Word abi_ret_gp_reg;
	Word abi_ret_fp_reg;
}

interp_enter_stub:
	InterpreterEnterData enterData = stack_allocate();
	enterData->stack = $original_sp;
	enterData->method = interpreter_methods_table[$interp_reg]

	for (int i = 0; enterData->abi_arg_gp_regs.length; i++) {
		 enterData->abi_arg_gp_regs[i] = getABIRegGP(i);
	}
	for (int i = 0; enterData->abi_arg_fp_regs.length; i++) {
		 enterData->abi_arg_fp_regs[i] = getABIRegFP(i);
	}

	mov arg_0_reg, enterData
	call interp_enter

	mov return_gp_reg, enterData.abi_ret_gp_reg
	mov return_fp_reg, enterData.abi_ret_fp_reg
	ret
```

And then `interp_enter` allocates the actual `InterpreterFrame`, populates it with arguments and calls the interpreter main loop:

```java
static void interp_enter(InterpreterEnterData enterData) {
	InterpreterMethod method = enterData.method;
	InterpreterFrame frame = allocate();

	CallInfo cinfo = method.getCallInfo();
	for (int i = 0; i < method.signature.length; i++) {
		// cinfo.getArg does the "heavy" ABI work
		frame.setArg(i, cinfo.getArg(i, method.signature[i], enterData));
	}

	new Interpreter(method).execute(frame);

	enterData.setReturnValue(cinfo.getReturnValue(frame));
}
```

Note that:
* Per method there is one `interp_enter_trampoline`, and it is architecture specific.
* There is one `interp_enter_stub` globally, and it is architecture specific.
* There is one `interp_enter` globally, and is written in System Java.
* `InterpreterEnterData` is a `CStruct`.

### Transition from interpreter to compiled code (`i2c`)

Similar approach as for `c2i`: We have `interp_exit` that builds a `InterpreterExitData` struct, which is then passed down to an `interp_exit_stub` that does the ABI related work. Eventually it calls the AOT compiled method indirectly via its GOT offset (i.e. we do _not_ go through the PLT stub) and makes sure the return values are correctly passed up.

For exit we do not require a trampoline per method.  However the `interp_exit_stub` is a bit special, as it does not have a fixed frame layout, something the Graal backend cannot deal with well. Therefore there is special handling around this, for example the stack walker needs to know how much it should unwind.  The variable length of such frames is stored at a known offset.

### Call dispatch

Let's consider multiple scenarios.

#### Scenario A: Static call, no inlining

Consider:
```java
class Calls {
	static void foo() {
		bar();
	}
	static void bar() {
		print("bar");
	}
}
```

Cases:
1. If we want `Calls#bar` to be executed in the interpreter, its GOT slot will be patched to an `interp_enter_trampoline` that injects the `InterpreterMethod` for `Calls#bar`.
2. If `Calls#foo` is executed in the interpreter, and `Calls#bar` should run as compiled code, we will do a GOT dispatch with the `i2c` primitive described above. A prerequisite for that is that we can determine the right GOT slot at the time we create the constant pool entry for that `invokestatic` in `Calls#foo`, which is effectively a `MethodPointer` attached to the `InterpreterMethod`.
3. If both should be executed in the interpreter we can either go through two transitions via `i2c <> c2i`.  There is some optimization potential here by having an `i2i` transition.


#### Scenario B: virtual call, no inlining

Consider:
```java
class VCalls {
	abstract void baz();

	static void dispatch(VCalls o) {
		o.baz();
	}
}

class VCalls_A extends VCalls {
	void baz() {
		print("VCalls_A");
	}
}

class VCalls_B extends VCalls {
	void baz() {
		print("VCalls_B");
	}
}
```

Cases:
1. Assume `VCalls#dispatch` runs as compiled code and  `VCalls_A#baz` should run with the interpreter. Since the virtual dispatch goes through the PLT+GOT mechanism, we can patch the GOT slot accordingly.
2. Assume `VCalls#dispatch` runs in the interpreter and both `VCalls_A#baz` and `VCalls_B#baz` run as compiled code. We do the virtual dispatch by access the entrypoint via `o->hub->vtable[vtableIndex]`, and then pass that through the `i2c` mechanism above. Additionally, at build-time we need to attach the right `vtableIndex` for the constant pool entry associated with that `invokevirtual` instruction.
3. Assume `VCalls#dispatch` runs in the interpreter, `VCalls_A#baz` runs in the interpreter too but `VCalls_B#baz` runs as compiled code. In this case we do the same as in 2. and accept the overhead of a `i2c <> c2i` transition.

#### Scenario C: Static call, with inlining

Consider:
```java
class Calls {
	static void foo() {
		bar();
	}
	static void bar() {
		print("bar");
	}
}
```

But this time `Calls#bar` is inlined. Cases:
1. Assume `Calls#bar` should be executed in the interpreter. In this case we need to make sure that the execution of `Calls#foo` already happens in the interpreter, i.e. patch its GOT slot and deoptimize activation frames of it.
2. Assume `Calls#foo` should be executed in the interpreter. As soon as we reach the `invokestatic` to call `Calls#bar` we cannot do the same as in Scenario A, because there is no AOT compiled version for `Calls#bar` (the only usage was inlined). So we have to make sure to include a `InterpreterMethod` for `Calls#bar` at build-time, and do its execution in the interpreter. Since there is no `MethodPointer` attached to the `InterpreterMethod` of `Calls#bar`, we know that we must execute `Calls#bar` in the interpreter too then.

For 2. we need to track inlining decisions during image building and persist that information in a `.metadata` companion file.

#### Scenario D: Virtual calls, with inlining

If virtual callees "disappear" due to inlining, SVM still reserves a vtable slot for it but populates it with a bailout stub.
We detect such cases upon invocation and fall back to a "side vtable" populated with `InterpreterMethod`s. We construct that table during image build time and include that in the `.metadata` companion file.
