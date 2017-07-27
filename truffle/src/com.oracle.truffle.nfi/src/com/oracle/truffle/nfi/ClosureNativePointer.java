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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Container object for the two pointers (data and code) that make up a closure on the native side.
 * The native closure is reference counted, with the reference count being stored in this object.
 *
 * By convention, the first reference is owned by the {@link LibFFIClosure} object and therefore
 * controlled by the Java GC. There should be no other references to this object from the Java side.
 * When the reference count drops to zero, the native side of this object is freed, so the Java side
 * contains garbage and must not be used anymore.
 */
final class ClosureNativePointer extends NativeAllocation.Destructor {

    private final NFIContext context;

    private final long nativeClosure;
    private final long codePointer;

    private final AtomicInteger refCount;

    ClosureNativePointer(NFIContext context, long nativeClosure, long codePointer) {
        this.context = context;
        this.nativeClosure = nativeClosure;
        this.codePointer = codePointer;

        // the first reference is owned by the LibFFIClosure object
        this.refCount = new AtomicInteger(1);
    }

    void addRef() {
        int refs = refCount.getAndIncrement();
        assert refs > 0 : "addRef on dead closure";
    }

    @Override
    protected void destroy() {
        int refs = refCount.decrementAndGet();
        assert refs >= 0 : "destroy on already dead closure";
        if (refs == 0) {
            context.removeClosureNativePointer(codePointer);
            freeClosure(nativeClosure);
        }
    }

    long getCodePointer() {
        assert refCount.get() > 0 : "accessing dead closure";
        return codePointer;
    }

    private static native void freeClosure(long closure);
}
