/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.memory;

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
     * Returns true if accssses using the {@link BarrierType} are permitted to be folded by the
     * optimizer. Accesses by {@link java.lang.ref.Reference#get},
     * {@link java.lang.ref.WeakReference}{@code .refersTo0}, and
     * {@link java.lang.ref.PhantomReference}{@code .refersTo0} shouldn't be optimized as those
     * particular reads have special GC semantics.
     */
    public boolean canReadEliminate() {
        return canReadEliminate;
    }
}
