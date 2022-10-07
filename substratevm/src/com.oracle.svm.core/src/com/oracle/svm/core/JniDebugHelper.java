/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core;

import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.c.InvokeJavaFunctionPointer;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveEpilogue;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.JNIThreadLocalEnvironment;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;

final class JniDebugHelper {
    /**
     * At a breakpoint, prepare for inspection/manipulation by a debugger using JNI.
     */
    @Uninterruptible(reason = "Satisfy NoPrologue precondition, but already in Java.", calleeMustBe = false)
    @CEntryPoint(name = "svm_jnidbg_begin_break", include = IncludeJniDebugHelperMethods.class, publishAs = CEntryPoint.Publish.SymbolOnly)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    public static void beginBreak() {
        JNIObjectHandles.pushLocalFrame(16);
    }

    /**
     * After calling {@link #beginBreak()}, create a JNI object handle for an object (but not after
     * {@link #toJni} unless {@link #toJava} was called too).
     */
    @Uninterruptible(reason = "Satisfy NoPrologue precondition, but already in Java.", calleeMustBe = false)
    @CEntryPoint(name = "svm_jnidbg_ref_to_handle", include = IncludeJniDebugHelperMethods.class, publishAs = CEntryPoint.Publish.SymbolOnly)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    public static JNIObjectHandle refToHandle(Object obj) {
        return JNIObjectHandles.createLocal(obj);
    }

    /**
     * After calling {@link #beginBreak()}, resolve a JNI object handle to a reference (but not
     * after {@link #toJni} unless {@link #toJava} was called too). Returned values must not be
     * reused after executing code other than further {@link #handleToRef} calls.
     */
    @Uninterruptible(reason = "Satisfy NoPrologue precondition, but already in Java.")
    @CEntryPoint(name = "svm_jnidbg_handle_to_ref", include = IncludeJniDebugHelperMethods.class, publishAs = CEntryPoint.Publish.SymbolOnly)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    public static Object handleToRef(JNIObjectHandle handle) {
        /*
         * TODO: for global references, we enter an interruptible slow path during which GC could
         * happen, which would make references that were returned by immediately preceding calls
         * invalid. We need to fix this for calling methods with multiple reference parameters.
         */
        return JNIObjectHandles.getObject(handle);
    }

    /**
     * After {@link #beginBreak()}, enter a context in which JNI functions may be called.
     */
    @Uninterruptible(reason = "Satisfy NoPrologue precondition.", calleeMustBe = false)
    @CEntryPoint(name = "svm_jnidbg_enter_jni_context", include = IncludeJniDebugHelperMethods.class, publishAs = CEntryPoint.Publish.SymbolOnly)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = LeaveEpilogue.class)
    public static JNIEnvironment toJni(Pointer sp, CodePointer ip) {
        JNIEnvironment env = JNIThreadLocalEnvironment.getAddress();
        /*
         * TODO: at method breakpoints, where the frame has not yet been created, stack walks fail
         * fatally (JavaStackWalker.reportUnknownFrameEncountered).
         *
         * TODO: ip is likely at a location without a reference map. If that's the case, GC or
         * anything else fails fatally (CodeInfoTable.reportNoReferenceMap).
         *
         * TODO: stack walks assume a single linear stack. If the auxiliary stack of the debugger
         * where the walk starts is located at a higher address than the thread's actual stack, its
         * anchor will get skipped over (loop in JavaStackWalker.continueWalk). This can lead to
         * missing references on the stack leading to problems following GC, among other things.
         */
        /*
         * Allocate anchor on the C heap so it stays around after returning, and because this code
         * likely executes on some auxiliary stack of the debugger over which we have no control.
         */
        JavaFrameAnchor anchor = UnmanagedMemory.calloc(SizeOf.get(JavaFrameAnchor.class));
        JavaFrameAnchors.pushFrameAnchor(anchor);
        anchor.setLastJavaSP(sp);
        anchor.setLastJavaIP(ip);
        return env;
    }

    /**
     * After {@link #toJni}, walk the stack (for testing).
     */
    @CEntryPoint(name = "svm_jnidbg_walk_in_jni", include = IncludeJniDebugHelperMethods.class, publishAs = CEntryPoint.Publish.SymbolOnly)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class)
    public static void walkInJni(@SuppressWarnings("unused") JNIEnvironment env) {
        new Throwable().printStackTrace();
    }

    /**
     * After {@link #toJni}, trigger a garbage collection (for testing).
     */
    @CEntryPoint(name = "svm_jnidbg_gc_in_jni", include = IncludeJniDebugHelperMethods.class, publishAs = CEntryPoint.Publish.SymbolOnly)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class)
    public static void gcInJni(@SuppressWarnings("unused") JNIEnvironment env) {
        Heap.getHeap().getGC().collectCompletely(GCCause.JavaLangSystemGC);
    }

    /**
     * After {@link #toJni}, leave the context in which JNI functions may be called.
     */
    @Uninterruptible(reason = "Satisfy NoPrologue precondition.", calleeMustBe = false)
    @CEntryPoint(name = "svm_jnidbg_leave_jni_context", include = IncludeJniDebugHelperMethods.class, publishAs = CEntryPoint.Publish.SymbolOnly)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, epilogue = NoEpilogue.class)
    public static void toJava(@SuppressWarnings("unused") JNIEnvironment env) {
        JavaFrameAnchor anchor = JavaFrameAnchors.popFrameAnchor();
        UnmanagedMemory.free(anchor);
    }

    /**
     * After {@link #beginBreak()} and matched pairs of {@link #toJni} and {@link #toJava} calls,
     * finish inspection/manipulation by a debugger so that regular execution can resume.
     */
    @Uninterruptible(reason = "Satisfy NoEpilogue precondition, but already in Java.", calleeMustBe = false)
    @CEntryPoint(name = "svm_jnidbg_end_break", include = IncludeJniDebugHelperMethods.class, publishAs = CEntryPoint.Publish.SymbolOnly)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    public static void endBreak() {
        JNIObjectHandles.popLocalFrame();
    }

    /*
     * This is an example for a native-to-Java call stub (without using JNI, and requiring JNI
     * registration).
     *
     * Native and Java calling convention can differ, e.g. with Windows on AMD64, or on AArch64 with
     * regard to stack argument alignment. Java calls also expect values to be zero-extended, while
     * C does not require that.
     *
     * TODO: We might have to generate native-to-Java stubs for all Java method signatures, although
     * we would be able to share many of them (sub-ints as int, and references as long).
     */
    @CEntryPoint(name = "svm_jnidbg_invokevoid_word_word_int_int_word", include = IncludeJniDebugHelperMethods.class, publishAs = CEntryPoint.Publish.SymbolOnly)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    @Uninterruptible(reason = "Satisfy NoPrologue precondition, but already in Java.", calleeMustBe = false)
    public static void callWordWordIntIntWord(CFunctionPointer fp, WordBase v0, WordBase v1, int v2, int v3, WordBase v4) {
        ((CallWordWordIntIntWordMethod) fp).call(v0, v1, v2, v3, v4);
    }

    public interface CallWordWordIntIntWordMethod extends CFunctionPointer {
        @InvokeJavaFunctionPointer
        void call(WordBase v0, WordBase v1, int v2, int v3, WordBase v4);
    }

    static class JNIEnvEnterPrologue implements CEntryPointOptions.Prologue {
        @Uninterruptible(reason = "prologue")
        public static int enter(JNIEnvironment env) {
            return CEntryPointActions.enter((IsolateThread) env);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static class IncludeJniDebugHelperMethods implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.IncludeDebugHelperMethods.getValue();
        }
    }

    private JniDebugHelper() {
    }
}
