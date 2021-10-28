/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.memory;

/**
 *
 * A special form of {@linkplain MemoryAccess} requiring barrier information for garbage collection.
 */
public interface OnHeapMemoryAccess extends MemoryAccess {

    /**
     * The types of (write/read) barriers attached to stores.
     */
    enum BarrierType {
        /**
         * Primitive access which do not necessitate barriers.
         */
        NONE,
        /**
         * Array object access.
         */
        ARRAY,
        /**
         * Field object access.
         */
        FIELD,
        /**
         * Unknown (aka field or array) object access.
         */
        UNKNOWN,
        /**
         * Weak field access (e.g. Hotspot's Reference.referent field).
         */
        WEAK_FIELD,
        /**
         * Phantom field access (e.g. Hotspot's Reference.referent field of a PhantomReference
         * instance).
         */
        PHANTOM_FIELD
    }

    /**
     * Gets the write barrier type for that particular access.
     */
    BarrierType getBarrierType();

}
