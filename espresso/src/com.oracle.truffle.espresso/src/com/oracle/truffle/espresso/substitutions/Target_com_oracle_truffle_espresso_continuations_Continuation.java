package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.stream;

/**
 * VM entry point from the Continuation class, responsible for unwinding and rewinding the stack.
 */
@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_continuations_Continuation {
    // Next steps:
    // - Figure out how to get enough type information back from the frame static slots to be able to print
    //   type information (needed for serialization). Look at the bytecode verifier output?
    // - Print out some human-meaningful info about the stack at the moment pause is called.

    @Substitution(hasReceiver = true)
    static void resume0(@JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject continuation) {
        System.out.println("resume0 called with " + continuation);
    }

    @Substitution
    static void pause0() {
        throw new Unwind();
    }

    @Substitution(hasReceiver = true)
    @CompilerDirectives.TruffleBoundary
    static void start0(
            @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject continuation,
            @Inject Meta meta
    ) {
        try {
            meta.com_oracle_truffle_espresso_continuations_Continuation_run.invokeDirect(continuation);
        } catch (Unwind e) {
            handleUnwind(meta, continuation, e);
        }
    }

    private static void handleUnwind(Meta meta, @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject continuation, Unwind e) {
        // dumpStackToStdOut(e);
        StaticObject nextPtr = StaticObject.NULL;
        for (int i = e.frames.size() - 1; i >= 0; i--)
            nextPtr = createFrameRecord(meta, e.frames.get(i), nextPtr);
        meta.com_oracle_truffle_espresso_continuations_Continuation_stackFrameHead.setObject(continuation, nextPtr);
    }

    private static StaticObject createFrameRecord(Meta meta, MaterializedFrame frame, StaticObject nextRecord) {
        // Convert the MaterializedFrame to a guest-land Continuation.FrameRecord.
        Object[] ptrs = frame.getIndexedLocals();
        long[] prims = frame.getIndexedPrimitiveLocals();

        // ptrs already contains StaticObjects, but we need to fill out the nulls and do the casts ourselves, otherwise
        // we get ClassCastExceptions.
        StaticObject[] convertedPtrs = new StaticObject[ptrs.length];
        for (int i = 0; i < convertedPtrs.length; i++)
            convertedPtrs[i] = ptrs[i] != null ? (StaticObject) ptrs[i] : StaticObject.NULL;

        var record = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord.allocateInstance();
        meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_init_.invokeDirect(
                record,
                nextRecord,
                // Host arrays are not guest arrays, so convert.
                StaticObject.wrap(convertedPtrs, meta),
                StaticObject.wrap(prims, meta)
        );
        return record;
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

//    private static StaticObject createResumeResult(boolean completed, StaticObject entryPointResultOrObjectForResume, Meta meta, EspressoContext ctx) {
//        // Create a guest Continuation.Result object via its constructor.
//        var reasonName = completed ? Symbol.Name.COMPLETED : Symbol.Name.PAUSED;
//
//        // Read StopReason.COMPLETED or StopReason.PAUSED
//        // TODO: This should probably be pulled out into some other place where we can cache the results of the lookups.
//        StaticObject reason = meta.com_oracle_truffle_espresso_continuations_Continuation_StopReason.lookupField(
//                reasonName,
//                Symbol.Type.com_oracle_truffle_espresso_continuations_Continuation_StopReason
//        ).getObject(meta.com_oracle_truffle_espresso_continuations_Continuation_StopReason.tryInitializeAndGetStatics());
//
//        // Now create a Result object via its c'tor to wrap the status and yielded object.
//        StaticObject r = meta.com_oracle_truffle_espresso_continuations_Continuation_Result.allocateInstance(ctx);
//        meta.com_oracle_truffle_espresso_continuations_Continuation_Result_init_.invokeDirect(r, reason, entryPointResultOrObjectForResume);
//        return r;
//    }

    public static class Unwind extends ControlFlowException {
        public final List<MaterializedFrame> frames = new ArrayList<>();
    }
}
