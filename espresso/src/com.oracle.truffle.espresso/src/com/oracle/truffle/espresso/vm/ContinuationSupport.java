/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.vm;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;

import java.io.Serial;

/**
 * Support code used for implementation the
 * {@link com.oracle.truffle.espresso.substitutions.Target_com_oracle_truffle_espresso_continuations_Continuation
 * continuation intrinsics}.
 */
public class ContinuationSupport {
    /**
     * A host-side mirror of a guest-side {@code Continuation.FrameRecord} object. The data in a
     * frame record is trusted but may be accidentally nonsensical in case of user error, for
     * instance resuming a continuation generated with a different version of the program or VM to
     * what we are running now.
     */
    public static final class HostFrameRecord {
        public final StaticObject[] pointers;
        public final long[] primitives;
        public final byte[] slotTags;
        public final int sp;
        public final int statementIndex;
        public final Method.MethodVersion methodVersion;
        public HostFrameRecord next;

        public HostFrameRecord(StaticObject[] pointers, long[] primitives, byte[] slotTags,
                               int sp,
                               int statementIndex,
                               Method.MethodVersion methodVersion,
                               HostFrameRecord next) {
            this.pointers = pointers;
            this.primitives = primitives;
            this.slotTags = slotTags;
            this.sp = sp;
            this.statementIndex = statementIndex;
            this.methodVersion = methodVersion;
            this.next = next;
        }

        /**
         * Copies this single record into a newly allocated guest-side object that the guest can
         * then serialize, deserialize and resume. Does <i>not</i> set the {@code next} pointer.
         */
        public @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation$FrameRecord;") StaticObject copyToGuest(Meta meta) {
            // We discard frame cookies here. As they are only used for the guest StackWalker API
            // and the obsolete SecurityManager implementation, this seems OK.

            // ptrs already contains StaticObjects, but we need to fill out the nulls and do the
            // casts ourselves, otherwise we get ClassCastExceptions.
            StaticObject[] convertedPtrs = new StaticObject[pointers.length];
            for (int i = 0; i < convertedPtrs.length; i++) {
                convertedPtrs[i] = pointers[i] != null ? pointers[i] : StaticObject.NULL;
            }

            var guestRecord = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord.allocateInstance();
            meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_init_.invokeDirect(
                            guestRecord,
                            // Host arrays are not guest arrays, so convert.
                            StaticObject.wrap(convertedPtrs, meta),
                            StaticObject.wrap(primitives, meta),
                            methodVersion.getMethod().makeMirror(meta),
                            sp,
                            statementIndex,
                            slotTags != null ? StaticObject.wrap(slotTags, meta) : StaticObject.NULL);
            return guestRecord;
        }

        /**
         * Copies the <i>entire</i> linked list of frame records from a guest {@code Continuation}
         * object (which contains the head frame record pointer) to a linked list of host frame
         * records.
         */
        public static HostFrameRecord copyFromGuest(
                        @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject self,
                        Meta meta,
                        @Inject EspressoContext context) {
            HostFrameRecord hostCursor = null;
            HostFrameRecord hostHead = null;
            StaticObject /* FrameRecord */ cursor = meta.com_oracle_truffle_espresso_continuations_Continuation_stackFrameHead.getObject(self);
            while (StaticObject.notNull(cursor)) {
                /* Object[] */
                StaticObject pointersGuest = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_pointers.getObject(cursor);
                /* long[] */
                StaticObject primitivesGuest = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_primitives.getObject(cursor);
                /* Object (actually byte[]), or null */
                StaticObject reservedGuest = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_reserved1.getObject(cursor);
                /* java.lang.reflect.Method */
                StaticObject methodGuest = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_method.getObject(cursor);
                int sp = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_sp.getInt(cursor);
                int statementIndex = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_statementIndex.getInt(cursor);

                var language = context.getLanguage();

                StaticObject[] pointers = pointersGuest.unwrap(language);
                long[] primitives = primitivesGuest.unwrap(language);
                byte[] slotTags = reservedGuest != StaticObject.NULL ? reservedGuest.unwrap(language) : null;
                Method method = Method.getHostReflectiveMethodRoot(methodGuest, meta);

                var next = new HostFrameRecord(pointers, primitives, slotTags, sp, statementIndex, method.getMethodVersion(), null);
                if (hostCursor != null) {
                    hostCursor.next = next;
                }
                if (hostHead == null) {
                    hostHead = next;
                }
                hostCursor = next;
                cursor = meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_next.getObject(cursor);
            }

            return hostHead;
        }
    }

    /**
     * The exception thrown host-side to unwind the stack when a continuation suspends. Frame info
     * is gathered up into a linked list.
     */
    public static class Unwind extends ControlFlowException {
        @Serial
        private static final long serialVersionUID = 2520648816466452283L;

        public transient HostFrameRecord head = null;

        @CompilerDirectives.TruffleBoundary
        public StaticObject toGuest(Meta meta) {
            // Convert the linked list from host to guest.
            ContinuationSupport.HostFrameRecord cursor = head;
            StaticObject guestHead = null;
            StaticObject guestCursor = null;
            while (cursor != null) {
                var next = cursor.copyToGuest(meta);
                if (guestHead == null) {
                    guestHead = next;
                }
                if (guestCursor != null) {
                    meta.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_next.setObject(guestCursor, next);
                }
                guestCursor = next;
                cursor = cursor.next;
            }
            return guestHead;
        }
    }
}
