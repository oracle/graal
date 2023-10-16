/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.ref;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

/**
 * Marker interface for Espresso non-strong reference implementations.
 *
 * <p>
 * All classes implementing {@link EspressoReference} must extend {@link Reference
 * Reference&lt;StaticObject&gt;} or one of it's subclasses; note that the generic
 * &lt;StaticObject&gt; parameter is relevant e.g. a class extending {@code Reference&lt;T&gt;} is
 * not valid.
 * </p>
 */
public interface EspressoReference {

    /**
     * Returns the associated guest reference.
     */
    @JavaType(Reference.class)
    StaticObject getGuestReference();

    /**
     * {@link Reference#get() Reference&lt;StaticObject&gt;#get()}.
     */
    StaticObject get();

    /**
     * {@link Reference#clear() Reference&lt;StaticObject&gt;#clear()}.
     */
    void clear();

    /**
     * Creates a host reference with {@link WeakReference} semantics.
     */
    static EspressoReference createWeak(EspressoContext context, @JavaType(WeakReference.class) StaticObject guestReference,
                    @JavaType(Object.class) StaticObject referent) {
        return new EspressoWeakReference(guestReference, referent, context.getReferenceQueue());
    }

    /**
     * Creates a host reference with {@link SoftReference} semantics.
     */
    static EspressoReference createSoft(EspressoContext context, @JavaType(SoftReference.class) StaticObject guestReference,
                    @JavaType(Object.class) StaticObject referent) {
        return new EspressoSoftReference(guestReference, referent, context.getReferenceQueue());
    }

    /**
     * Creates a host reference with {@link PhantomReference} semantics.
     */
    static EspressoReference createPhantom(EspressoContext context, @JavaType(PhantomReference.class) StaticObject guestReference,
                    @JavaType(Object.class) StaticObject referent) {
        return new EspressoPhantomReference(guestReference, referent, context.getReferenceQueue());
    }

    /**
     * Creates a host reference with {@code java.lang.ref.FinalReference} or {@link WeakReference}
     * semantics.
     * 
     * If the host {@code java.lang.reg.FinalReference} is not accessible, or its use is explicitly
     * disabled via {@code --java.UseHostFinalReference=false}, a {@link WeakReference} is returned
     * instead.
     * 
     * With {@link WeakReference}, {@link Object#finalize()} will never be called on collected
     * objects.
     */
    @SuppressWarnings("javadoc")
    static EspressoReference createFinal(EspressoContext context, @JavaType(internalName = "Ljava/lang/ref/FinalReference;") StaticObject guestReference,
                    @JavaType(Object.class) StaticObject referent) {
        if (!context.getEspressoEnv().UseHostFinalReference) {
            return EspressoReference.createWeak(context, guestReference, referent);
        }
        return FinalizationSupport.createEspressoFinalReference(context, guestReference, referent);
    }
}
