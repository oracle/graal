/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

public record ShortCircuitInstructionModel(Operator operator,
                InstructionModel booleanConverterInstruction) {

    /**
     * The processor cannot directly depend on the module containing
     * ShortCircuitOperation.Operation, so the definition is mirrored here.
     */
    public enum Operator {
        AND_RETURN_VALUE(true, false),
        AND_RETURN_CONVERTED(true, true),
        OR_RETURN_VALUE(false, false),
        OR_RETURN_CONVERTED(false, true);

        public final boolean continueWhen;
        public final boolean returnConvertedBoolean;

        Operator(boolean continueWhen, boolean returnConvertedBoolean) {
            this.continueWhen = continueWhen;
            this.returnConvertedBoolean = returnConvertedBoolean;
        }
    }

    public boolean continueWhen() {
        return operator.continueWhen;
    }

    public boolean returnConvertedBoolean() {
        return operator.returnConvertedBoolean;
    }

    public boolean convertsOperands() {
        return booleanConverterInstruction != null;
    }

    /**
     * If the operation doesn't convert its value, or it returns a converted boolean, it produces a
     * boolean result.
     */
    public boolean producesBoolean() {
        return !convertsOperands() || returnConvertedBoolean();
    }

    /**
     * If the operation doesn't produce a boolean, it must DUP the operand so it can pass it to the
     * converter and also produce it as a result.
     */
    public boolean duplicatesOperandOnStack() {
        return !producesBoolean();
    }

}
