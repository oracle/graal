package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
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
    static void resume0(
            @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject self,
            @Inject Meta meta
    ) {
        System.out.println("resume0 called with " + self);
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
        for (int i = 0; i < e.frames.size(); i++)
            nextPtr = createFrameRecord(meta, e.frames.get(i), e.methodVersions.get(i), nextPtr);
        meta.com_oracle_truffle_espresso_continuations_Continuation_stackFrameHead.setObject(continuation, nextPtr);
    }

    // Convert the MaterializedFrame to a guest-land Continuation.FrameRecord.
    private static @JavaType StaticObject
    createFrameRecord(Meta meta, MaterializedFrame frame, Method.MethodVersion methodVersion, StaticObject nextRecord) {
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
                StaticObject.wrap(prims, meta),
                methodVersion.getMethod().makeMirror(meta)
        );
        return record;
    }

    @SuppressWarnings("unused")
    private static void dumpStackToStdOut(Unwind e) {
        System.out.printf("Stack has %d frames\n", e.frames.size());
        for (int i = 0; i < e.frames.size(); i++) {
            MaterializedFrame frame = e.frames.get(i);
            Method.MethodVersion methodVersion = e.methodVersions.get(i);
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

    public static class Unwind extends ControlFlowException {
        public final List<MaterializedFrame> frames = new ArrayList<>();
        public final List<Method.MethodVersion> methodVersions = new ArrayList<>();
    }
}
