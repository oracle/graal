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
 * Represents the Java bytecode frame state(s) at a given position
 * including {@link CiValue locations} where to find the local variables,
 * operand stack values and locked objects of the bytecode frame(s).
 */
public class CiFrame extends CiCodePos implements Serializable {
    /**
     * An array of values representing how to reconstruct the state of the Java frame.
     * This is array is partitioned as follows:
     * <p>
     * <table border="1" cellpadding="5" frame="void", rules="all">
     * <tr><th>Start index (inclusive)</th><th>End index (exclusive)</th><th>Description</th></tr>
     * <tr><td>0</td>                   <td>numLocals</td>           <td>Local variables</td></tr>
     * <tr><td>numLocals</td>           <td>numLocals + numStack</td><td>Operand stack</td></tr>
     * <tr><td>numLocals + numStack</td><td>values.length</td>       <td>Locked objects</td></tr>
     * </table>
     * <p>
     * Note that the number of locals and the number of stack slots may be smaller than the
     * maximum number of locals and stack slots as specified in the compiled method.
     */
    public final CiValue[] values;

    /**
     * The number of locals in the values array.
     */
    public final int numLocals;

    /**
     * The number of stack slots in the values array.
     */
    public final int numStack;

    /**
     * The number of locks in the values array.
     */
    public final int numLocks;

    public final boolean rethrowException;

    /**
     * Creates a new frame object.
     *
     * @param caller the caller frame (which may be {@code null})
     * @param method the method
     * @param bci a BCI within the method
     * @param rethrowException specifies if the VM should re-throw the pending exception when deopt'ing using this frame
     * @param values the frame state {@link #values}
     * @param numLocals the number of local variables
     * @param numStack the depth of the stack
     * @param numLocks the number of locked objects
     */
    public CiFrame(CiFrame caller, RiResolvedMethod method, int bci, boolean rethrowException, CiValue[] values, int numLocals, int numStack, int numLocks) {
        super(caller, method, bci);
        assert values != null;
        this.rethrowException = rethrowException;
        this.values = values;
        this.numLocks = numLocks;
        this.numLocals = numLocals;
        this.numStack = numStack;
        assert !rethrowException || numStack == 1 : "must have exception on top of the stack";
    }

    /**
     * Gets the value representing the specified local variable.
     * @param i the local variable index
     * @return the value that can be used to reconstruct the local's current value
     */
    public CiValue getLocalValue(int i) {
        return values[i];
    }

    /**
     * Gets the value representing the specified stack slot.
     * @param i the stack index
     * @return the value that can be used to reconstruct the stack slot's current value
     */
    public CiValue getStackValue(int i) {
        return values[i + numLocals];
    }

    /**
     * Gets the value representing the specified lock.
     * @param i the lock index
     * @return the value that can be used to reconstruct the lock's current value
     */
    public CiValue getLockValue(int i) {
        return values[i + numLocals + numStack];
    }

    /**
     * Gets the caller of this frame.
     *
     * @return {@code null} if this frame has no caller
     */
    public CiFrame caller() {
        return (CiFrame) caller;
    }

    /**
     * Deep equality test.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof CiFrame) {
            CiFrame other = (CiFrame) obj;
            return equals(other, false, false);
        }
        return false;
    }

    /**
     * Deep equality test.
     *
     * @param ignoreKinds if {@code true}, compares values but {@link CiValue#equalsIgnoringKind(CiValue) ignore} their kinds
     * @param ignoreNegativeBCIs if {@code true}, negative BCIs are treated as equal
     */
    public boolean equals(CiFrame other, boolean ignoreKinds, boolean ignoreNegativeBCIs) {
        if ((other.bci == bci || (ignoreNegativeBCIs && other.bci < 0 && bci < 0)) &&
            numLocals == other.numLocals &&
            numStack == other.numStack &&
            numLocks == other.numLocks &&
            values.length == other.values.length) {

            if (ignoreKinds) {
                for (int i = 0; i < values.length; i++) {
                    if (!values[i].equalsIgnoringKind(other.values[i])) {
                        return false;
                    }
                }
            } else {
                for (int i = 0; i < values.length; i++) {
                    if (!values[i].equals(other.values[i])) {
                        return false;
                    }
                }
            }
            if (caller == null) {
                return other.caller == null;
            }
            if (other.caller == null) {
                return false;
            }
            return caller().equals(other.caller(), ignoreKinds, ignoreNegativeBCIs);
        }
        return false;
    }

    @Override
    public String toString() {
        return CiUtil.append(new StringBuilder(100), this).toString();
    }

    /**
     * Gets a copy of this frame but with an empty stack.
     */
    public CiFrame withEmptyStack() {
        if (numStack == 0) {
            return this;
        }
        CiValue[] values = new CiValue[numLocals + numLocks];
        System.arraycopy(this.values, 0, values, 0, numLocals);
        System.arraycopy(this.values, numLocals + numStack, values, numLocals, numLocks);
        return new CiFrame(caller(), method, bci, rethrowException, values, numLocals, 0, numLocks);
    }
}
