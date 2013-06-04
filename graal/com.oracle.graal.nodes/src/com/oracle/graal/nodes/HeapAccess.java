/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

/**
 * A HeapAccess is a node that access the heap and therefore may be subjected to certain rules of
 * the underlying runtime.
 */
public interface HeapAccess {

    /*
     * The types of write barriers attached to stores.
     */
    public enum WriteBarrierType {
        /*
         * Primitive stores which do not necessitate write barriers.
         */
        NONE,
        /*
         * Array object stores which necessitate precise write barriers.
         */
        PRECISE,
        /*
         * Field object stores which necessitate imprecise write barriers.
         */
        IMPRECISE
    }

    /**
     * Gets the write barrier type for that particular access.
     */
    WriteBarrierType getWriteBarrierType();

    /**
     * Returns whether or not the heap access is a compressed pointer candidate.
     */
    boolean compress();
}