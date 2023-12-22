package com.oracle.truffle.espresso.vm;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.stream;

/**
 * Support code used for implementation
 * the {@link com.oracle.truffle.espresso.substitutions.Target_com_oracle_truffle_espresso_continuations_Continuation continuation intrinsics}.
 */
public class ContinuationSupport {
    public static HostFrameRecords guestFrameRecordsToHost(
            @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject self,
            Meta meta,
            @Inject EspressoContext context
    ) {
        var stack = new HostFrameRecords();

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



            cursor = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_next.getObject(cursor);
        }

        return null;
    }

    // Convert the MaterializedFrame to a guest-land Continuation.FrameRecord.
    public static @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation$FrameRecord;") StaticObject
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


    public static class HostFrameRecords {
        public final List<MaterializedFrame> frames = new ArrayList<>();
        public final List<Method.MethodVersion> methodVersions = new ArrayList<>();

        @SuppressWarnings("unused")
        public void dumpStackToStdOut() {
            System.out.printf("Stack has %d frames\n", frames.size());
            for (int i = 0; i < frames.size(); i++) {
                MaterializedFrame frame = frames.get(i);
                Method.MethodVersion methodVersion = methodVersions.get(i);
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
    }

    public static class Unwind extends ControlFlowException {
        public HostFrameRecords stack = new HostFrameRecords();
    }
}
