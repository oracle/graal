package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.stream;

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

        HostStack stack = guestFrameRecordsToUnwind(self, meta, context);
    }

    private static HostStack guestFrameRecordsToUnwind(
            @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject self,
            Meta meta,
            @Inject EspressoContext context
    ) {
        var stack = new HostStack();

        // Walk the guest linked list, converting to host-side objects.
        StaticObject /* FrameRecord */ cursor = meta.com_oracle_truffle_espresso_continuations_Continuation_stackFrameHead.getObject(self);
        while (StaticObject.notNull(cursor)) {
            /* Object[] */ StaticObject pointersGuest = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_pointers.getObject(cursor);
            /* long[] */ StaticObject primitivesGuest = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_primitives.getObject(cursor);
            /* java.lang.reflect.Method */ StaticObject methodGuest = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_method.getObject(cursor);

            var language = context.getLanguage();
            StaticObject[] pointers = pointersGuest.unwrap(language);
            long[] primitives = primitivesGuest.unwrap(language);
            Method method = (Method) meta.HIDDEN_METHOD_KEY.getHiddenObject(methodGuest);

            //var frame = Truffle.getRuntime().createMaterializedFrame(new Object[], method.


            cursor = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_next.getObject(cursor);
        }

        return null;
    }

    @Substitution
    static void pause0() {
        throw new Unwind();
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
        } catch (Unwind e) {
            handleUnwind(meta, self, e);
        }
    }

    @TruffleBoundary
    private static void handleUnwind(Meta meta, @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject continuation, Unwind e) {
        // dumpStackToStdOut(e);
        StaticObject nextPtr = StaticObject.NULL;
        for (int i = 0; i < e.stack.frames.size(); i++)
            nextPtr = createFrameRecord(meta, e.stack.frames.get(i), e.stack.methodVersions.get(i), nextPtr);
        meta.com_oracle_truffle_espresso_continuations_Continuation_stackFrameHead.setObject(continuation, nextPtr);
    }

    // Convert the MaterializedFrame to a guest-land Continuation.FrameRecord.
    private static @JavaType StaticObject
    createFrameRecord(Meta meta, MaterializedFrame frame, Method.MethodVersion methodVersion, StaticObject nextRecord) {
        Object[] ptrs = frame.getIndexedLocals();
        long[] prims = frame.getIndexedPrimitiveLocals();

        // We discard frame cookies here. As they are only used for the guest StackWalker API and the obsolete
        // SecurityManager implementation, this seems OK.

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
                StaticObject.wrap(prims, meta),
                methodVersion.getMethod().makeMirror(meta)
        );
        return record;
    }

    @SuppressWarnings("unused")
    private static void dumpStackToStdOut(HostStack stack) {
        System.out.printf("Stack has %d frames\n", stack.frames.size());
        for (int i = 0; i < stack.frames.size(); i++) {
            MaterializedFrame frame = stack.frames.get(i);
            Method.MethodVersion methodVersion = stack.methodVersions.get(i);
            System.out.printf("[%d] %s.%s()\n", i, methodVersion.getDeclaringKlass().getExternalName(), methodVersion.getNameAsString());
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

    public static class HostStack {
        public final List<MaterializedFrame> frames = new ArrayList<>();
        public final List<Method.MethodVersion> methodVersions = new ArrayList<>();
    }

    public static class Unwind extends ControlFlowException {
        public HostStack stack = new HostStack();
    }
}
