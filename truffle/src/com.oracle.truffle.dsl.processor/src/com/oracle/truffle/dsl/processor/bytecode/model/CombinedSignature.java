/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.bytecode.model;

import java.util.List;

import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.ConstantOperandsModel;

/**
 * Represents a signature parsed with name and constant operand information for easier code
 * generation.
 */
public record CombinedSignature(Signature signature, List<String> operandNames, List<CombinedOperand> operands) {

    public CombinedSignature(Signature signature, List<String> operandNames, ConstantOperandsModel constantOperands) {
        this(signature, operandNames, createOperands(signature, operandNames, constantOperands));
    }

    private static List<CombinedOperand> createOperands(Signature signature, List<String> operandNames, ConstantOperandsModel constantOperands) {
        if (signature.operandTypes.size() != operandNames.size()) {
            throw new IllegalArgumentException("Incompatible signature and operandNames passed.");
        }

        if (signature.constantOperandsBeforeCount != constantOperands.before().size()) {
            throw new IllegalArgumentException("Incompatible constant before operands.");
        }

        if (signature.constantOperandsAfterCount != constantOperands.after().size()) {
            throw new IllegalArgumentException("Incompatible constant after operands.");
        }

        CombinedOperand[] arguments = new CombinedOperand[signature.operandTypes.size()];
        int dynamicCounter = 0;
        for (int i = 0; i < signature.operandTypes.size(); i++) {
            ConstantOperandModel constant;
            int dynamicIndex;
            if (i < signature.constantOperandsBeforeCount) {
                constant = constantOperands.before().get(i);
                dynamicIndex = -1;
            } else if (i >= signature.constantOperandsBeforeCount + signature.dynamicOperandCount) {
                constant = constantOperands.after().get(i - (signature.constantOperandsBeforeCount + signature.dynamicOperandCount));
                dynamicIndex = -1;
            } else {
                constant = null;
                dynamicIndex = dynamicCounter++;
            }
            arguments[i] = new CombinedOperand(i,
                            dynamicIndex,
                            operandNames.get(i),
                            signature.operandTypes.get(i),
                            constant);
        }
        return List.of(arguments);
    }

}
