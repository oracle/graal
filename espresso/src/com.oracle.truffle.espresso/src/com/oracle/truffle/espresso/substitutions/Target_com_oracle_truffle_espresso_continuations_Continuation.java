package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
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
    // - Make it work when assertions are enabled.
    // - Test with a lambda on the stack (indy), fix it.
    // - Make a demo of using Kryo to serialize and restore a continuation.
    // - Work out how to put unit tests in the enterprise repository.
    // - Be able to abort the unwind if we hit a frame that can't be suspended e.g. that holds monitors.
    // - Ensure unwinds fail if there are any non-bytecode methods on the stack.

    @Substitution
    static void pause0() {
        // This internal exception will be caught in BytecodeNode's interpreter loop. Frame records will be added to
        // the exception object in a linked list until it's caught below.
        throw new ContinuationSupport.Unwind();
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    static void resume0(
            @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject self,
            @Inject Meta meta,
            @Inject EspressoContext context
    ) {
        ContinuationSupport.HostFrameRecord stack = ContinuationSupport.HostFrameRecord.copyFromGuest(self, meta, context);

        // This will break if the continuations API is redefined - TODO: find a way to block that.
        var runMethod = meta.com_oracle_truffle_espresso_continuations_Continuation_run.getMethodVersion();

        // The entry node will unpack the head frame record into the stack and then pass the remaining records into the
        // bytecode interpreter, which will then pass them down the stack until everything is fully unwound.
        // TODO: Is creating a new node the right way to do it? Probably we should be using an explicit node with cached arguments and stuff?
        try {
            runMethod.getCallTarget().call(self, stack);
        } catch (ContinuationSupport.Unwind unwind) {
            CompilerDirectives.transferToInterpreter();
            meta.com_oracle_truffle_espresso_continuations_Continuation_stackFrameHead.setObject(self, unwind.toGuest(meta));
        }
    }

    @Substitution(hasReceiver = true)
    static void start0(
            @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject self,
            @Inject Meta meta
    ) {
        try {
            // The run method is private in Continuation and is the continuation delimiter. Frames from run onwards will
            // be unwound on pause, and rewound on resume.
            meta.com_oracle_truffle_espresso_continuations_Continuation_run.invokeDirect(self);
        } catch (ContinuationSupport.Unwind unwind) {
            // Guest called pause(). By the time we get here the frame info has been gathered up into host-side objects
            // so we just need to copy the data into the guest world.
            CompilerDirectives.transferToInterpreter();
            meta.com_oracle_truffle_espresso_continuations_Continuation_stackFrameHead.setObject(self, unwind.toGuest(meta));
        }
    }
}
