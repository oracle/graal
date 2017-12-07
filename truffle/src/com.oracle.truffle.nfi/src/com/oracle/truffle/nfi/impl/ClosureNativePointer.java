/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.CallTarget;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Container object for the two pointers (data and code) that make up a closure on the native side.
 * There can be multiple references to a native closure object, both from the managed heap and from
 * native code. This class manages these references using a {@link #refCount reference count}.
 * <p>
 * If the {@link NFIContext} dies, all native references to the closure will be implicitly released.
 * <p>
 * This diagram shows the references between the {@link ClosureNativePointer} object, the closure on
 * the native side, and its users:
 * <p>
 * <img src="doc-files/native-alloc.svg">
 * <p>
 * The native data associated with a closure is stored in a {@code struct closure_data} on the
 * native heap. The {@link ClosureNativePointer} object has a {@link #codePointer native pointer}
 * into this structure. The lifetime of the managed {@link ClosureNativePointer} and the native
 * {@code struct closure_data} are linked, when the {@link ClosureNativePointer} dies, the {@code
 * struct closure_data} is deallocated. This is done by the {@link NativeDestructor}, which is
 * triggered by a phantom reference on the {@link ClosureNativePointer}.
 * <p>
 * The native {@link struct closure_data} can be referenced from native code directly, or from
 * {@link LibFFIClosure} objects in the managed heap. Both kinds of references are counted in the
 * {@link #refCount}. The references from native code are manually counted using the
 * {@code TruffleEnv::newClosureRef} and {@code TruffleEnv::releaseClosureRef} functions. The
 * references from the {@link LibFFIClosure} objects are counted automatically using a phantom
 * reference and the {@link ReleaseRef} destructor, which will be triggered when the
 * {@link LibFFIClosure} object dies.
 * <p>
 * As long as the {@link #refCount} is greater than zero, there is one additional reference from a
 * map in the {@link NFIContext}. This map is used to lookup the {@link ClosureNativePointer}
 * reference from managed code. This reference also keeps the {@link ClosureNativePointer} object
 * alive if there are only native references, but no other managed references.
 * <p>
 * The native {@code struct closure_data} needs a JNI reference to a {@link CallTarget}. This
 * reference can only be freed after the {@link ClosureNativePointer} object dies. The
 * {@link CallTarget} might have a reference to a cached {@link LibFFIClosure} object, which in turn
 * has a reference to the {@link ClosureNativePointer}. This reference cycle would keep the whole
 * structure alive indefinitely. To break that cycle, the NFI reference from the
 * {@code struct closure_data} to the {@link CallTarget} is weak, but the {@link CallTarget} is kept
 * alive by an additional strong reference from the {@link ClosureNativePointer} object. That way,
 * the GC can collect the whole cycle of {@link ClosureNativePointer}, {@link CallTarget} and
 * {@link LibFFIClosure} at once, and then the {@link NativeDestructor} can free the
 * {@code struct closure_data} later.
 * <p>
 * Another problem that might keep the whole reference cycle alive is the {@link ReleaseRef}
 * destructor that exists for each {@link LibFFIClosure} object. If the {@link LibFFIClosure} object
 * is cached in the AST, this may produce a reference cycle from the {@link ReleaseRef} destructor
 * back to the {@link LibFFIClosure}, preventing the {@link LibFFIClosure} from being collected,
 * which in turn prevents the {@link ReleaseRef} destructor from triggering. Since the
 * {@link LibFFIClosure} object is cached in the AST, it can only die if the {@link NFIContext} is
 * disposed. Because of that, the {@link ReleaseRef} destructor can not be registered in the
 * {@link NativeAllocation#getGlobalQueue global} queue, otherwise the reference from the destructor
 * would keep everything alive. Therefore, the {@link ReleaseRef} destructor is enqueued in a local
 * queue that can die at the same time as the {@link ClosureNativePointer}. Now, if the whole
 * {@link NFIContext} dies, the GC can collect all objects involved in the reference cycle at once,
 * including the {@link ReleaseRef} destructor. In that case, the reference count is not decremented
 * for the {@link LibFFIClosure} objects, but that doesn't matter since the {@link NFIContext} is
 * dead anyway.
 */
final class ClosureNativePointer {

    private final NFIContext context;

    private final long codePointer;

    private final AtomicInteger refCount;

    /**
     * The native side of this object has a JNI reference to a {@link CallTarget}. This
     * {@link CallTarget} may indirectly hold a reference to this object, preventing garbage
     * collection of the whole structure. To break this reference cycle, the native side contains a
     * weak JNI reference, and this object contains a strong managed reference to keep the object
     * alive. That way the GC can collect everything on the Java side before the native side is
     * deallocated.
     */
    final CallTarget callTarget;

    /**
     * The LibFFI closure structure keeps a pointer to the native signature. Keep a Java reference
     * to the signature around to prevent GC as long as the closure is alive.
     */
    final LibFFISignature signature;

    /**
     * The destructor of {@link LibFFIClosure} needs a reference to this object, in order to call
     * {@link #releaseRef} when it dies. Since this object in turn might contain a transitive
     * reference to the {@link LibFFIClosure} object, we must not keep the destructor
     * unconditionally alive, otherwise that will keep the {@link LibFFIClosure} object alive
     * forever, and the reference count will never drop to zero. By keeping the destructor reference
     * here, we allow the {@link ClosureNativePointer} object and the {@link LibFFIClosure} object
     * to die simultaneously (e.g. if a context is disposed). In that case, the destructor will not
     * run, but since the {@link ClosureNativePointer} object is dead anyway, we don't care about
     * the reference count.
     */
    private final NativeAllocation.Queue releaseRefQueue;

    static ClosureNativePointer create(NFIContext context, long nativeClosure, long codePointer, CallTarget callTarget, LibFFISignature signature) {
        ClosureNativePointer ret = new ClosureNativePointer(context, codePointer, callTarget, signature);
        NativeAllocation.getGlobalQueue().registerNativeAllocation(ret, new NativeDestructor(nativeClosure));
        return ret;
    }

    private ClosureNativePointer(NFIContext context, long codePointer, CallTarget callTarget, LibFFISignature signature) {
        this.context = context;
        this.codePointer = codePointer;
        this.callTarget = callTarget;
        this.signature = signature;

        // the code calling this constructor is responsible for calling registerManagedRef
        this.refCount = new AtomicInteger(0);

        this.releaseRefQueue = new NativeAllocation.Queue();
    }

    void registerManagedRef(LibFFIClosure closure) {
        addRef();
        releaseRefQueue.registerNativeAllocation(closure, new ReleaseRef(this));
    }

    void addRef() {
        int refs = refCount.incrementAndGet();
        assert refs > 0 : "closure still dead after addRef";
    }

    void releaseRef() {
        int refs = refCount.decrementAndGet();
        assert refs >= 0 : "releaseRef on already dead closure";
        if (refs == 0) {
            context.removeClosureNativePointer(codePointer);
        }
    }

    long getCodePointer() {
        assert refCount.get() > 0 : "accessing dead closure";
        return codePointer;
    }

    private static final class ReleaseRef extends NativeAllocation.Destructor {

        private final ClosureNativePointer pointer;

        ReleaseRef(ClosureNativePointer pointer) {
            this.pointer = pointer;
        }

        @Override
        protected void destroy() {
            pointer.releaseRef();
        }
    }

    private static class NativeDestructor extends NativeAllocation.Destructor {

        private final long nativeClosure;

        NativeDestructor(long nativeClosure) {
            this.nativeClosure = nativeClosure;
        }

        @Override
        protected void destroy() {
            freeClosure(nativeClosure);
        }
    }

    private static native void freeClosure(long closure);
}
