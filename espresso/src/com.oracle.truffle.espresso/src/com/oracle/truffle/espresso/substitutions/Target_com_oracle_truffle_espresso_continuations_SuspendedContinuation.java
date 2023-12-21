package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.oracle.truffle.espresso.meta.EspressoError.guarantee;
import static java.util.Arrays.stream;

/**
 * VM entry point from the SuspendedContinuation class, responsible for unwinding and rewinding the stack.
 */
@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_continuations_SuspendedContinuation {
    // Next steps:
    // - Figure out how to get enough type information back from the frame static slots to be able to print
    //   type information (needed for serialization). Look at the bytecode verifier output?
    // - Print out some human-meaningful info about the stack at the moment pause is called.

    @Substitution(hasReceiver = true)
    static void resume0(@JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/SuspendedContinuation;") StaticObject suspendedContinuation) {
        System.out.println("resume0 called with " + suspendedContinuation);
    }

    @Substitution(hasReceiver = true)
    static void pause1(@JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/SuspendedContinuation;") StaticObject suspendedContinuation) {
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
            return handleUnwind(meta, ctx, e);
        }
    }

    private static StaticObject handleUnwind(Meta meta, EspressoContext ctx, Unwind e) {
        //dumpStackToStdOut(e);
        return createResumeResult(false, StaticObject.NULL, meta, ctx);
    }

    @SuppressWarnings("unused")
    private static void dumpStackToStdOut(Unwind e) {
        System.out.printf("Stack has %d frames\n", e.frames.size());
        for (int i = 0; i < e.frames.size(); i++) {
            System.out.printf("%d. ", i);
            MaterializedFrame frame = e.frames.get(i);
            System.out.printf("Object refs:\n%s\n   Primitives: %s\n",
                    String.join(
                            "\n",
                            stream(frame.getIndexedLocals())
                                    .map(it -> {
                                        var obj = (StaticObject) it;
                                        if (obj != null) {
                                            return "   • " + obj.toVerboseString().replace("\n", "\n     ");
                                        }
                                        return "   null";
                                    })
                                    .toList()
                    ),

                    String.join(
                            "  ",
                            stream(frame.getIndexedPrimitiveLocals())
                                    .mapToObj(it -> String.format("0x%x", it))
                                    .toList()
                    )
            );
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

    public static class Unwind extends ControlFlowException {
        @Serial
        private static final long serialVersionUID = 157831852637280571L;

        public final List<MaterializedFrame> frames = new ArrayList<>();
    }
}
