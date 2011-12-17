/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ci;

/**
 * Represents the result of compiling a method. The result can include a target method with machine code and metadata,
 * and/or statistics. If the compiler bailed out due to malformed bytecode, an internal error, or other cause, it will
 * supply the bailout object.
 */
public class CiResult {
    private final CiTargetMethod targetMethod;
    private final CiBailout bailout;
    private final CiStatistics stats;

    /**
     * Creates a new compilation result.
     * @param targetMethod the method that was produced, if any
     * @param bailout the bailout condition that occurred
     * @param stats statistics about the compilation
     */
    public CiResult(CiTargetMethod targetMethod, CiBailout bailout, CiStatistics stats) {
        this.targetMethod = targetMethod;
        this.bailout = bailout;
        this.stats = stats;
    }

    /**
     * Gets the target method that was produced by this compilation. If no target method was
     * produced, but a bailout occured, then the bailout exception will be thrown at this point.
     * @return the target method produced
     * @throws {@link CiBailout} if a bailout occurred
     */
    public CiTargetMethod targetMethod() {
        if (bailout != null) {
            throw bailout;
        }
        return targetMethod;
    }

    /**
     * Returns the statistics about the compilation that were produced, if any.
     * @return the statistics
     */
    public CiStatistics statistics() {
        return stats;
    }

    /**
     * Returns the bailout condition that occurred for this compilation, if any.
     * @return the bailout
     */
    public CiBailout bailout() {
        return bailout;
    }
}
