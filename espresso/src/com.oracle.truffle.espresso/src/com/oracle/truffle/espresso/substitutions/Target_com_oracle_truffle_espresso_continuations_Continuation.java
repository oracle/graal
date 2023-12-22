package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.ContinuationSupport;

/**
 * VM entry point from the Continuation class, responsible for unwinding and rewinding the stack.
 */
@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_continuations_Continuation {
    // Next steps:
    // - Ensure unwinds fail if there are any non-bytecode methods on the stack.

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    static void resume0(
            @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject self,
            @Inject Meta meta,
        @Inject EspressoContext context
    ) {
        System.out.println("resume0 called with " + self);

        ContinuationSupport.HostFrameRecords stack = ContinuationSupport.guestFrameRecordsToHost(self, meta, context);
    }

    @Substitution
    static void pause0() {
        throw new ContinuationSupport.Unwind();
    }

    @Substitution(hasReceiver = true)
    static void start0(
            @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject self,
            @Inject Meta meta
    ) {
        // The run method is private in Continuation and is the continuation delimiter. Frames from run onwards will
        // be unwound on pause, and rewound on resume.
        try {
            meta.com_oracle_truffle_espresso_continuations_Continuation_run.invokeDirect(self);
        } catch (ContinuationSupport.Unwind e) {
            handleUnwind(meta, self, e);
        }
    }

    @TruffleBoundary
    private static void handleUnwind(Meta meta, @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject continuation, ContinuationSupport.Unwind e) {
        // dumpStackToStdOut(e);
        StaticObject nextPtr = StaticObject.NULL;
        for (int i = 0; i < e.stack.frames.size(); i++)
            nextPtr = ContinuationSupport.createFrameRecord(meta, e.stack.frames.get(i), e.stack.methodVersions.get(i), nextPtr);
        meta.com_oracle_truffle_espresso_continuations_Continuation_stackFrameHead.setObject(continuation, nextPtr);
    }
}
