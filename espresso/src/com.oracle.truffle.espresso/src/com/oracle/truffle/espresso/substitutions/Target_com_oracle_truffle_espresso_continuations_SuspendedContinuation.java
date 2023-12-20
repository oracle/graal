package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

import java.io.Serial;

import static com.oracle.truffle.espresso.meta.EspressoError.guarantee;

/**
 * VM entry point from the SuspendedContinuation class, responsible for unwinding and rewinding the stack.
 */
@EspressoSubstitutions
final class Target_com_oracle_truffle_espresso_continuations_SuspendedContinuation {
    // Next steps:
    // - Study how BytecodeNode manages frames.
    // - Figure out how to construct an artifical stack frame and invoke it from within resume0.
    // - Figure out how to construct *two* artificial stack frames and invoke them.
    // - Demonstrate catching an exception in resume0() thrown by pause1() and returning.
    // - Catch/rethrow the unwind exception to build up an array of stack frames.


    @Substitution(hasReceiver = true)
    static void resume0(@JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/SuspendedContinuation;") StaticObject suspendedContinuation) {
        System.out.println("resume0 called with " + suspendedContinuation);
    }

    @Substitution(hasReceiver = true)
    static void pause1(@JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/SuspendedContinuation;") StaticObject suspendedContinuation) {
        System.out.println("pause1 called with " + suspendedContinuation);
        throw new Unwind();
    }

    @Substitution(hasReceiver = true)
    @CompilerDirectives.TruffleBoundary
    static @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/SuspendedContinuation$Result;") StaticObject start0(
            @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/SuspendedContinuation;") StaticObject suspendedContinuation,
            @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/SuspendedContinuation$EntryPoint;") StaticObject entryPoint,
            @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/SuspendedContinuation$PauseCapability;") StaticObject pauseCap,
            @JavaType(Object.class) StaticObject objectToResumeWith,
            @Inject Meta meta,
            @Inject EspressoContext ctx
    ) {
        Method run = entryPoint.getKlass().lookupDeclaredMethod(Symbol.Name.run, Symbol.Signature.Object_PauseCapability_Object);
        guarantee(run != null, "Continuation entry point should have been type checked as implementing the right interface by here.");
        try {
            var entryPointResultObject = (StaticObject) run.invokeDirect(entryPoint, pauseCap, objectToResumeWith);
            return createResumeResult(true, entryPointResultObject, meta, ctx);
        } catch (Unwind e) {
            return createResumeResult(false, null, meta, ctx);
        }
    }

    private static StaticObject createResumeResult(boolean completed, StaticObject entryPointResultOrObjectForResume, Meta meta, EspressoContext ctx) {
        // Create a guest SuspendedContinuation.Result object via its constructor.
        var reasonName = completed ? Symbol.Name.COMPLETED : Symbol.Name.PAUSED;

        // Read StopReason.COMPLETED or StopReason.PAUSED
        // TODO: This should probably be pulled out into some other place where we can cache the results of the lookups.
        StaticObject reason = meta.com_oracle_truffle_espresso_continuations_SuspendedContinuation_StopReason.lookupField(
                reasonName,
                Symbol.Type.com_oracle_truffle_espresso_continuations_SuspendedContinuation_StopReason
        ).getObject(meta.com_oracle_truffle_espresso_continuations_SuspendedContinuation_StopReason.tryInitializeAndGetStatics());

        // Now create a Result object via its c'tor to wrap the status and yielded object.
        StaticObject r = meta.com_oracle_truffle_espresso_continuations_SuspendedContinuation_Result.allocateInstance(ctx);
        meta.com_oracle_truffle_espresso_continuations_SuspendedContinuation_Result_init_.invokeDirect(r, reason, entryPointResultOrObjectForResume);
        return r;
    }

    static class Unwind extends ControlFlowException {
        @Serial
        private static final long serialVersionUID = 157831852637280571L;
    }
}
