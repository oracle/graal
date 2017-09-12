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
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.CallTarget;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Container object for the two pointers (data and code) that make up a closure on the native side.
 * The native closure is reference counted, with the reference count being stored in this object.
 *
 * Every {@link LibFFIClosure} object has a reference to a {@link ClosureNativePointer} object.
 * Native code may have additional references that are manually counted using the
 * {@code newClosureRef} and {@code addClosureRef} functions.
 *
 * This object is kept alive in a map in the {@link NFIContext} as long as the reference count is
 * greater than zero. The reference from {@link NFIContext} to {@link ClosureNativePointer} is not
 * counted in the reference count. When the reference count drops to zero, it is removed from the
 * map in the context, and the GC can free this object, and subsequently destroy the closure on the
 * native side.
 *
 * When the {@link NFIContext} is disposed, all native references to all closures owned by this
 * context are released. If there are also no Java references left, then the native closure is
 * destroyed.
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
