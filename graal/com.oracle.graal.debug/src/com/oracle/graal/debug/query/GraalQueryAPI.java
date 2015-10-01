/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.debug.query;

/**
 * NOTE that these queries return fixed constants in the interpreter mode. The Graal option
 * RemoveNeverExecutedCode is switched off to prevent de-optimization.
 */
public final class GraalQueryAPI {

    // Static query intrinsics

    /**
     * @return true if the enclosing method has been compiled by the dynamic compiler.
     */
    public static boolean isMethodCompiled() {
        return false;
    }

    /**
     * @return true if the enclosing method is inlined.
     */
    public static boolean isMethodInlined() {
        return false;
    }

    /**
     * @return the name of the root method for the current compilation task. If the enclosing method
     *         is inlined, this query returns the name of the method into which it is inlined.
     */
    public static String getRootName() {
        return "unknown";
    }

    // Dynamic query intrinsics

    public static final int ERROR = -1;

    /**
     * @return the kind of heap allocation for a directly preceding allocation site. The possible
     *         return values are {ERROR(-1), TLAB(0), HEAP(1)}. While ERROR denotes either the
     *         utility is not supported, e.g. in interpreter, or if the allocation site was
     *         eliminated, the other two represent a TLAB allocation (fast path) or a direct heap
     *         allocation (slow path).
     */
    public static int getAllocationType() {
        return ERROR;
    }

    /**
     * @return the runtime lock type for a directly preceding lock site. The possible return values
     *         are {ERROR(-1), bias:existing(0), bias:acquired(1), bias:transfer(2),
     *         stub:revoke_or_stub:epoch-expired(3), stub:failed-cas(4), recursive(5), cas(6)}.
     */
    public static int getLockType() {
        return ERROR;
    }

}
