/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vm.continuation;

import static com.oracle.truffle.espresso.analysis.frame.EspressoFrameDescriptor.guarantee;
import static com.oracle.truffle.espresso.meta.EspressoError.cat;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.analysis.frame.EspressoFrameDescriptor;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.meta.ErrorType;
import com.oracle.truffle.espresso.substitutions.JavaType;

/**
 * A host-side mirror of a guest-side {@code Continuation.FrameRecord} object. The data in a frame
 * record is trusted but may be accidentally nonsensical in case of user error, for instance
 * resuming a continuation generated with a different version of the program or VM to what we are
 * running now.
 */
public final class HostFrameRecord {
    // we would store statement index here, but it may not be precise if the method gets
    // instrumented afterwards.
    public final EspressoFrameDescriptor frameDescriptor;
    public final StaticObject[] objects;
    public final long[] primitives;
    public final int top;
    public final Method.MethodVersion methodVersion;
    public HostFrameRecord next;

    private boolean tainted = false;

    public int bci() {
        assert primitives.length > 0;
        return EspressoFrame.decodeBCI(EspressoFrameDescriptor.narrow(primitives[0]));
    }

    private void setBci(int bci) {
        primitives[0] = EspressoFrameDescriptor.zeroExtend(EspressoFrame.encodeBCI(bci));
    }

    public void taint() {
        tainted = true;
    }

    public void untaint() {
        tainted = false;
    }

    public boolean isTainted() {
        return tainted;
    }

    public static HostFrameRecord recordFrame(VirtualFrame frame, Method.MethodVersion m, int bci, int top, HostFrameRecord next) {
        // With these being constant, the lookup loop in 'm.getFrameDescriptor(bci)' can fold away.
        CompilerAsserts.partialEvaluationConstant(m);
        CompilerAsserts.partialEvaluationConstant(bci);

        EspressoFrameDescriptor fd = m.getFrameDescriptor(bci);
        // With 'fd' being constant, the import loop can be exploded.
        CompilerAsserts.partialEvaluationConstant(fd);

        StaticObject[] objects = new StaticObject[fd.size()];
        long[] primitives = new long[fd.size()];
        fd.importFromFrame(frame, objects, primitives);

        HostFrameRecord hfr = new HostFrameRecord(fd, objects, primitives, bci, top, m, next);
        // Result is trusted, but it never hurts to assert that.
        assert hfr.bci() == bci;
        assert top == fd.top() : "Mismatched tops: " + top + ", " + fd.top();
        assert hfr.verify(m.getMethod().getMeta(), true);
        return hfr;
    }

    private HostFrameRecord(EspressoFrameDescriptor frameDescriptor, StaticObject[] objects, long[] primitives, int bci, int top, Method.MethodVersion methodVersion, HostFrameRecord next) {
        this.frameDescriptor = frameDescriptor;
        this.objects = objects;
        this.primitives = primitives;
        this.top = top;
        this.methodVersion = methodVersion;
        this.next = next;
        setBci(bci);
    }

    @TruffleBoundary
    public boolean verify(Meta meta, boolean single) {
        HostFrameRecord current = this;
        while (current != null) {
            // Ensures initialization has run.
            // This ensures a well-defined point for running static initializers in case the records
            // are not obtained from a call to suspend (eg: deserialization, or manual record
            // creation)
            methodVersion.getDeclaringKlass().safeInitialize();
            // Ensures recorded frame is compatible with what the method expects.
            frameDescriptor.validateImport(objects, primitives, methodVersion.getDeclaringKlass(), meta);
            // Ensures we restore the stack at invokes
            BytecodeStream bs = new BytecodeStream(methodVersion.getOriginalCode());
            guarantee(Bytecodes.isInvoke(bs.opcode(bci())) && bs.opcode(bci()) != Bytecodes.INVOKEDYNAMIC, cat("Frame record would re-wind at a non-invoke bytecode."), meta);
            if (next != null) {
                // Ensures the next method is a valid invoke
                ConstantPool pool = methodVersion.getPool();
                int methodIndex = bs.readCPI(bci());
                Symbol<Name> name = pool.methodName(methodIndex);
                Symbol<Signature> signature = pool.methodSignature(methodIndex);
                // Compatible method reference
                guarantee(next.methodVersion.getName() == name && next.methodVersion.getRawSignature() == signature, "Wrong method on the recorded frames", meta);
                // Loading constraints are respected
                Symbol<Type> returnType = SignatureSymbols.returnType(next.methodVersion.getMethod().getParsedSignature());
                EspressoContext context = meta.getContext();
                context.getRegistries().checkLoadingConstraint(returnType,
                                methodVersion.getDeclaringKlass().getDefiningClassLoader(),
                                next.methodVersion.getDeclaringKlass().getDefiningClassLoader(), m -> {
                                    throw context.throwError(ErrorType.LinkageError, m);
                                });
            } else {
                // Last method on the stack must be the call to suspend.
                guarantee(methodVersion.getMethod() == meta.continuum.org_graalvm_continuations_ContinuationImpl_suspend, "Last method on the record is not 'Continuation.suspend'", meta);
            }
            if (single) {
                return true;
            }
            current = current.next;
        }
        return true;
    }

    /**
     * Converts the entire linked list of host records to guest records.
     */
    public StaticObject copyToGuest(Meta meta) {
        // Convert the linked list from host to guest.
        HostFrameRecord cursor = this;
        StaticObject guestHead = null;
        StaticObject guestCursor = null;
        while (cursor != null) {
            StaticObject guestNext = cursor.copyToGuestSingle(meta);
            if (guestHead == null) {
                guestHead = guestNext;
            }
            if (guestCursor != null) {
                meta.continuum.org_graalvm_continuations_ContinuationImpl_FrameRecord_next.setObject(guestCursor, guestNext);
            }
            guestCursor = guestNext;
            cursor = cursor.next;
        }
        return guestHead;
    }

    /**
     * Copies this single record into a newly allocated guest-side object that the guest can then
     * serialize, deserialize and resume. Does <i>not</i> set the {@code next} pointer.
     */
    public @JavaType(internalName = "Lorg/graalvm/continuations/Continuation$FrameRecord;") StaticObject copyToGuestSingle(Meta meta) {
        // Manually build the guest record.
        var guestRecord = meta.getAllocator().createNew(meta.continuum.org_graalvm_continuations_ContinuationImpl_FrameRecord);
        meta.continuum.org_graalvm_continuations_ContinuationImpl_FrameRecord_pointers.setObject(guestRecord, StaticObject.wrap(objects, meta));
        meta.continuum.org_graalvm_continuations_ContinuationImpl_FrameRecord_primitives.setObject(guestRecord, StaticObject.wrap(primitives, meta));
        meta.continuum.org_graalvm_continuations_ContinuationImpl_FrameRecord_method.setObject(guestRecord, methodVersion.getMethod().makeMirror(meta));
        meta.continuum.org_graalvm_continuations_ContinuationImpl_FrameRecord_bci.setInt(guestRecord, bci());
        return guestRecord;
    }

    /**
     * Copies the <i>entire</i> linked list of frame records from a guest {@code Continuation}
     * object (which contains the head frame record pointer) to a linked list of host frame records.
     */
    @TruffleBoundary
    public static HostFrameRecord copyFromGuest(
                    @JavaType(internalName = "Lorg/graalvm/continuations/Continuation;") StaticObject self,
                    Meta meta,
                    EspressoContext context) {
        HostFrameRecord hostCursor = null;
        HostFrameRecord hostHead = null;
        StaticObject /* FrameRecord */ cursor = meta.continuum.org_graalvm_continuations_ContinuationImpl_stackFrameHead.getObject(self);
        while (StaticObject.notNull(cursor)) {
            /* Object[] */
            StaticObject pointersGuest = meta.continuum.org_graalvm_continuations_ContinuationImpl_FrameRecord_pointers.getObject(cursor);
            /* long[] */
            StaticObject primitivesGuest = meta.continuum.org_graalvm_continuations_ContinuationImpl_FrameRecord_primitives.getObject(cursor);
            /* java.lang.reflect.Method */
            StaticObject methodGuest = meta.continuum.org_graalvm_continuations_ContinuationImpl_FrameRecord_method.getObject(cursor);
            int bci = meta.continuum.org_graalvm_continuations_ContinuationImpl_FrameRecord_bci.getInt(cursor);

            EspressoLanguage language = context.getLanguage();

            guarantee(!StaticObject.isNull(pointersGuest), "null array in the frame records.", meta);
            guarantee(!StaticObject.isNull(primitivesGuest), "null array in the frame records.", meta);
            guarantee(!StaticObject.isNull(methodGuest), "null method in the frame records.", meta);

            StaticObject[] pointers = pointersGuest.unwrap(language);
            long[] primitives = primitivesGuest.unwrap(language);
            Method method = Method.getHostReflectiveMethodRoot(methodGuest, meta);
            EspressoFrameDescriptor fd = method.getMethodVersion().getFrameDescriptor(bci);

            HostFrameRecord next = new HostFrameRecord(fd,
                            pointers.clone(), primitives.clone(), // Defensive copies.
                            bci, fd.top(), method.getMethodVersion(), null);
            if (hostCursor != null) {
                hostCursor.next = next;
            }
            if (hostHead == null) {
                hostHead = next;
            }

            hostCursor = next;
            cursor = meta.continuum.org_graalvm_continuations_ContinuationImpl_FrameRecord_next.getObject(cursor);
        }

        // Make sure the stack is valid. Will force class initialization of the entire record stack.
        if (hostHead != null) {
            hostHead.verify(meta, false);
        }
        return hostHead;
    }
}
