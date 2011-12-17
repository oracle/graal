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

import java.io.*;

import com.sun.cri.ri.*;

/**
 * Represents a code position, that is, a chain of inlined methods with bytecode
 * locations, that is communicated from the compiler to the runtime system. A code position
 * can be used by the runtime system to reconstruct a source-level stack trace
 * for exceptions and to create {@linkplain CiFrame frames} for deoptimization.
 */
public class CiCodePos implements Serializable {
    /**
     * The position where this position has been called, {@code null} if none.
     */
    public final CiCodePos caller;

    /**
     * The runtime interface method for this position.
     */
    public final RiResolvedMethod method;

    /**
     * The location within the method, as a bytecode index. The constant
     * {@code -1} may be used to indicate the location is unknown, for example
     * within code synthesized by the compiler.
     */
    public final int bci;

    /**
     * Constructs a new object representing a given parent/caller, a given method, and a given BCI.
     *
     * @param caller the parent position
     * @param method the method
     * @param bci a BCI within the method
     */
    public CiCodePos(CiCodePos caller, RiResolvedMethod method, int bci) {
        assert method != null;
        this.caller = caller;
        this.method = method;
        this.bci = bci;
    }

    /**
     * Converts this code position to a string representation.
     * @return a string representation of this code position
     */
    @Override
    public String toString() {
        return CiUtil.append(new StringBuilder(100), this).toString();
    }

    /**
     * Deep equality test.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof CiCodePos) {
            CiCodePos other = (CiCodePos) obj;
            if (other.method.equals(method) && other.bci == bci) {
                if (caller == null) {
                    return other.caller == null;
                }
                return caller.equals(other.caller);
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return bci;
    }
}
