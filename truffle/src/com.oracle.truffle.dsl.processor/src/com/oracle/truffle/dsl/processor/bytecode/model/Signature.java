/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.List;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

public final class Signature {
    public final ProcessorContext context = ProcessorContext.getInstance();

    public final TypeMirror returnType;
    // [constantOperandsBefore*, dynamicOperands*, constantOperandsAfter*]
    public final List<TypeMirror> operandTypes;
    public final boolean isVariadic;
    public final boolean isVoid;

    public final List<String> constantOperandsBefore;
    public final List<String> constantOperandsAfter;
    public final int dynamicOperandCount;
    public final int localSetterCount;
    public final int localSetterRangeCount;

    public Signature(TypeMirror returnType, List<TypeMirror> types) {
        this(returnType, types, false, 0, 0, List.of(), List.of());
    }

    public Signature(TypeMirror returnType, List<TypeMirror> types, boolean isVariadic,
                    int localSetterCount, int localSetterRangeCount, List<String> constantOperandsBefore, List<String> constantOperandsAfter) {
        this.returnType = returnType;
        this.operandTypes = Collections.unmodifiableList(types);
        this.isVariadic = isVariadic;
        this.isVoid = ElementUtils.isVoid(returnType);

        this.constantOperandsBefore = Collections.unmodifiableList(constantOperandsBefore);
        this.constantOperandsAfter = Collections.unmodifiableList(constantOperandsAfter);
        this.dynamicOperandCount = types.size() - constantOperandsBefore.size() - constantOperandsAfter.size();
        this.localSetterCount = localSetterCount;
        this.localSetterRangeCount = localSetterRangeCount;
    }

    public TypeMirror getGenericType(int i) {
        assert i > 0 && i < dynamicOperandCount;
        if (isVariadicParameter(i)) {
            return context.getType(Object[].class);
        }
        return context.getType(Object.class);
    }

    public TypeMirror getGenericReturnType() {
        if (isVoid) {
            return context.getType(void.class);
        } else {
            return context.getType(Object.class);
        }
    }

    public TypeMirror getSpecializedType(int i) {
        assert i > 0 && i < dynamicOperandCount;
        if (isVariadicParameter(i)) {
            return context.getType(Object[].class);
        }
        return operandTypes.get(constantOperandsBefore.size() + i);
    }

    public boolean isVariadicParameter(int i) {
        return isVariadic && i == dynamicOperandCount - 1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(ElementUtils.getSimpleName(returnType)).append(" ");
        sb.append("(");

        for (int i = 0; i < getConstantOperandsBeforeCount(); i++) {
            sb.append(ElementUtils.getSimpleName(operandTypes.get(i)));
            sb.append(", ");
        }

        int offset = getConstantOperandsBeforeCount();
        for (int i = 0; i < dynamicOperandCount; i++) {
            sb.append(ElementUtils.getSimpleName(operandTypes.get(offset + i)));
            if (isVariadic && i == dynamicOperandCount - 1) {
                sb.append("...");
            }
            sb.append(", ");
        }

        offset += dynamicOperandCount;
        for (int i = 0; i < getConstantOperandsAfterCount(); i++) {
            sb.append(ElementUtils.getSimpleName(operandTypes.get(offset + i)));
            sb.append(", ");
        }

        for (int i = 0; i < localSetterCount; i++) {
            sb.append("local, ");
        }

        for (int i = 0; i < localSetterRangeCount; i++) {
            sb.append("localRange, ");
        }

        if (sb.charAt(sb.length() - 1) == ' ') {
            sb.delete(sb.length() - 2, sb.length());
        }

        sb.append(')');

        return sb.toString();
    }

    public int getConstantOperandsBeforeCount() {
        return constantOperandsBefore.size();
    }

    public int getConstantOperandsAfterCount() {
        return constantOperandsAfter.size();
    }
}
