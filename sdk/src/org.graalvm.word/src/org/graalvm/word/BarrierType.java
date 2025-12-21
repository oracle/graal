/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.word;

/**
 * The types of write and read barriers attached to memory operations.
 */
public enum BarrierType {

    /**
     * Primitive access which does not require a barrier.
     */
    NONE,

    /**
     * Array object write.
     */
    ARRAY,

    /**
     * Field object write.
     */
    FIELD,

    /**
     * Read barrier.
     */
    READ,

    /**
     * Unknown (aka field or array) object access.
     */
    UNKNOWN,

    /**
     * A special value for writes to {@link org.graalvm.word.LocationIdentity#INIT_LOCATION} that
     * might still require a full store barrier.
     */
    POST_INIT_WRITE,

    /**
     * Clear {@link java.lang.ref.Reference}.referent. In the HotSpot world this corresponds to a
     * write decorated with {@code AS_NO_KEEPALIVE}. Depending on the particular garbage collector
     * this might do something different than {@link #FIELD}.
     */
    AS_NO_KEEPALIVE_WRITE,

    /**
     * Read of {@link java.lang.ref.Reference}.referent. In the HotSpot world this corresponds to an
     * access decorated with {@code ON_WEAK_OOP_REF}. Depending on the particular garbage collector
     * this might do something different than {@link #READ}.
     */
    REFERENCE_GET(false),

    /**
     * Read of {@link java.lang.ref.Reference}{@code .referent} in the context of
     * {@link java.lang.ref.WeakReference}{@code .refersTo0}. In the HotSpot world this corresponds
     * to an access decorated with {@code AS_NO_KEEPALIVE | ON_WEAK_OOP_REF}. Depending on the
     * particular garbage collector this might do something different than {@link #READ}.
     */

    WEAK_REFERS_TO(false),

    /**
     * Read of {@link java.lang.ref.Reference}{@code .referent} in the context of
     * {@link java.lang.ref.PhantomReference}{@code .refersTo0}. In the HotSpot world this
     * corresponds to an access decorated with {@code AS_NO_KEEPALIVE | ON_PHANTOM_OOP_REF}.
     * Depending on the particular garbage collector this might do something different than
     * {@link #READ}.
     */
    PHANTOM_REFERS_TO(false);

    private final boolean canReadEliminate;

    BarrierType(boolean canReadEliminate) {
        this.canReadEliminate = canReadEliminate;
    }

    BarrierType() {
        this.canReadEliminate = true;
    }

    /**
     * Returns true if accesses using the {@link BarrierType} are permitted to be folded by the
     * optimizer. Accesses by {@link java.lang.ref.Reference#get},
     * {@link java.lang.ref.WeakReference}{@code .refersTo0}, and
     * {@link java.lang.ref.PhantomReference}{@code .refersTo0} shouldn't be optimized as those
     * particular reads have special GC semantics.
     */
    public boolean canReadEliminate() {
        return canReadEliminate;
    }
}
