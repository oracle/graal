/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.ppc;

import com.sun.max.asm.*;
import com.sun.max.util.*;

/**
 * The argument to a Branch Conditional instruction specifying the conditions
 * under which the branch is taken.
 */
public final class BOOperand extends AbstractSymbolicArgument {

    private BOOperand(String value, String predictionBitsMask) {
        this(value, null, null, predictionBitsMask);
    }

    private BOOperand(String value) {
        this(value, null, null, null);
    }

    private BOOperand(String value, BOOperand taken, BOOperand notTaken, String predictionBitsMask) {
        super(Integer.parseInt(value, 2));
        assert value.length() == 5;
        assert predictionBitsMask == null || predictionBitsMask.length() == value.length();

        this.taken = taken;
        this.notTaken = notTaken;

        if (predictionBitsMask == null) {
            valueWithoutPredictionBits = Integer.MAX_VALUE;
        } else {
            int valWithoutPredictionBits = 0;
            int bit = 1 << (value.length() - 1);
            for (int i = 0; i != predictionBitsMask.length(); ++i) {
                if (predictionBitsMask.charAt(i) == '0') {
                    if (value.charAt(i) == '1') {
                        valWithoutPredictionBits |= bit;
                    }
                } else {
                    valWithoutPredictionBits >>= 1;
                }
                bit >>= 1;
            }
            this.valueWithoutPredictionBits = valWithoutPredictionBits;
        }
    }

    /**
     * The GNU assembler syntax does not have symbols for the valid values
     * and only accepts numeric arguments.
     */
    @Override
    public String externalValue() {
        return Integer.toString(value());
    }

    private final BOOperand taken;
    private final BOOperand notTaken;
    private final int valueWithoutPredictionBits;

    /**
     * @return the version of this branch condition operand that has the relevant bits set to indicate to the hardware that the branch is very likely not to be taken
     *
     * @throws IllegalArgumentException if prediction bits cannot be set for this operand
     */
    public BOOperand taken() {
        if (taken == null) {
            throw new IllegalArgumentException("branch condition " + this + " does not support branch prediction");
        }
        return taken;
    }

    /**
     * @return the version of this branch condition operand that has the relevant bits set to indicate to the hardware that the branch is very likely not to be taken
     *
     * @throws IllegalArgumentException if prediction bits cannot be set for this operand
     */
    public BOOperand notTaken() {
        if (notTaken == null) {
            throw new IllegalArgumentException("branch condition " + this + " does not support branch prediction");
        }
        return notTaken;
    }

    /**
     * @return the mask to apply to this branch condition operand to extract the branch prediction bits
     *
     * @throws IllegalArgumentException if this branch condition operand does not support branch prediction
     */
    public int valueWithoutPredictionBits() {
        if (valueWithoutPredictionBits == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("branch condition " + this + " does not support branch prediction");
        }

        return valueWithoutPredictionBits;
    }

    // Checkstyle: stop constant name check

    /**
     * Decrement the Counter Register, then branch if the decremented value is not 0 and
     * the bit in the Condition Register selected by the BI field is not set.
     */
    public static final BOOperand CTRNonZero_CRFalse = new BOOperand("00000");

    /**
     * Decrement the Counter Register, then branch if the decremented value is 0 and
     * the bit in the Condition Register selected by the BI field is not set.
     */
    public static final BOOperand CTRZero_CRFalse = new BOOperand("00010");

    /**
     * Branch if the bit in the Condition Register selected by the BI field is not set
     * and indicate that the branch is very likely to be taken.
     */
    public static final BOOperand CRFalse_PredictTaken = new BOOperand("00111", "00011");

    /**
     * Branch if the bit in the Condition Register selected by the BI field is not set
     * and indicate that the branch is very likely not to be taken.
     */
    public static final BOOperand CRFalse_PredictNotTaken = new BOOperand("00110", "00011");

    /**
     * Branch if the bit in the Condition Register selected by the BI field is not set.
     */
    public static final BOOperand CRFalse = new BOOperand("00100", CRFalse_PredictTaken, CRFalse_PredictNotTaken, "00011");

    /**
     * Decrement the Counter Register, then branch if the decremented value is not 0 and
     * the bit in the Condition Register selected by the BI field is set.
     */
    public static final BOOperand CTRNonZero_CRTrue = new BOOperand("01000");

    /**
     * Decrement the Counter Register, then branch if the decremented value is 0 and
     * the bit in the Condition Register selected by the BI field is set.
     */
    public static final BOOperand CTRZero_CRTrue = new BOOperand("01010");

    /**
     * Branch if the bit in the Condition Register selected by the BI field is set
     * and indicate that the branch is very likely to be taken.
     */
    public static final BOOperand CRTrue_PredictTaken = new BOOperand("01111", "00011");

    /**
     * Branch if the bit in the Condition Register selected by the BI field is set
     * and indicate that the branch is very likely not to be taken.
     */
    public static final BOOperand CRTrue_PredictNotTaken = new BOOperand("01110", "00011");

    /**
     * Branch if the bit in the Condition Register selected by the BI field is set.
     */
    public static final BOOperand CRTrue = new BOOperand("01100", CRTrue_PredictTaken, CRTrue_PredictNotTaken, "00011");

    /**
     * Decrement the Counter Register, then branch if the decremented value is 0
     * and indicate that the branch is very likely to be taken.
     */
    public static final BOOperand CTRNonZero_PredictTaken = new BOOperand("11001", "01001");

    /**
     * Decrement the Counter Register, then branch if the decremented value is 0
     * and indicate that the branch is very likely not to be taken.
     */
    public static final BOOperand CTRNonZero_PredictNotTaken = new BOOperand("11000", "01001");

    /**
     * Decrement the Counter Register, then branch if the decremented value is 0.
     */
    public static final BOOperand CTRNonZero = new BOOperand("10000", CTRNonZero_PredictTaken, CTRNonZero_PredictNotTaken, "01001");

    /**
     * Decrement the Counter Register, then branch if the decremented value is 0
     * and indicate that the branch is very likely to be taken.
     */
    public static final BOOperand CTRZero_PredictTaken = new BOOperand("11011", "01001");

    /**
     * Decrement the Counter Register, then branch if the decremented value is 0
     * and indicate that the branch is very likely not to be taken.
     */
    public static final BOOperand CTRZero_PredictNotTaken = new BOOperand("11010", "01001");

    /**
     * Decrement the Counter Register, then branch if the decremented value is 0.
     */
    public static final BOOperand CTRZero = new BOOperand("10010", CTRZero_PredictTaken, CTRZero_PredictNotTaken, "01001");

    /**
     * Branch always.
     */
    public static final BOOperand Always = new BOOperand("10100");

    // Checkstyle: resume constant name check

    public static final Symbolizer<BOOperand> SYMBOLIZER = Symbolizer.Static.initialize(BOOperand.class);
}
