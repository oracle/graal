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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

public record Signature(TypeMirror returnType,
                List<Operand> operands,
                List<Operand> dynamicOperands,
                List<Operand> constantOperands,
                boolean isVariadic, int variadicOffset) {

    public Signature(TypeMirror returnType, List<Operand> operands, boolean isVariadic, int variadicOffset) {
        this(returnType, operands,
                        // we materialize these lists for performance reasons
                        operands.stream().filter(Operand::isDynamic).toList(),
                        operands.stream().filter(Operand::isConstant).toList(),
                        isVariadic, variadicOffset);
    }

    public Signature(TypeMirror returnType, List<Operand> operands) {
        this(returnType, operands, false, -1);
    }

    public static List<Operand> createDefaultOperands(List<TypeMirror> types) {
        Operand[] operands = new Operand[types.size()];
        for (int i = 0; i < types.size(); i++) {
            operands[i] = new Operand(types.get(i), "arg" + i, i, i, null);
        }
        return List.of(operands);
    }

    private Signature(Signature copy, List<TypeMirror> operandTypes, List<String> operandNames, TypeMirror returnType) {
        this(returnType, copy.updateOperands(operandTypes, operandNames), copy.isVariadic, copy.variadicOffset);
    }

    private List<Operand> updateOperands(List<TypeMirror> types, List<String> names) {
        if (types == null && types == null) {
            // no update needed
            return this.operands;
        }

        if (types != null && types.size() != this.operands.size()) {
            throw new IllegalArgumentException("Provided types list size does not match operands size.");
        }
        if (names != null && names.size() != this.operands.size()) {
            throw new IllegalArgumentException("Provided names list size does not match operands size.");
        }

        List<Operand> newOperands = new ArrayList<>(this.operands.size());
        for (int i = 0; i < this.operands.size(); i++) {
            Operand current = this.operands.get(i);
            if (types != null) {
                current = current.withType(types.get(i));
            }
            if (names != null) {
                current = current.withName(names.get(i));
            }
            newOperands.add(current);
        }
        return newOperands;

    }

    public Signature withOperandTypes(List<TypeMirror> types) {
        if (operandTypes().size() != this.operands.size()) {
            throw new IllegalArgumentException("Invalid argument names");
        }
        Objects.requireNonNull(types);
        return new Signature(this, types, null, this.returnType());
    }

    public Signature withDynamicOperandTypes(Class<?>... dynamicTypes) {
        ProcessorContext context = ProcessorContext.getInstance();
        return withDynamicOperandTypes(List.of(dynamicTypes).stream().map((c) -> context.getType(c)).toList());
    }

    public Signature withDynamicOperandType(int dynamicIndex, TypeMirror type) {
        List<TypeMirror> types = new ArrayList<>(dynamicOperandTypes());
        types.set(dynamicIndex, type);
        return withDynamicOperandTypes(types);
    }

    public Signature withDynamicOperandTypes(List<TypeMirror> dynamicTypes) {
        List<TypeMirror> operandTypes = new ArrayList<>();
        for (Operand operand : operands()) {
            if (operand.isDynamic()) {
                operandTypes.add(dynamicTypes.get(operand.dynamicIndex()));
            } else {
                operandTypes.add(operand.type());
            }
        }
        return new Signature(this, operandTypes, null, this.returnType());
    }

    public Signature withReturnType(TypeMirror type) {
        Objects.requireNonNull(type);
        return new Signature(this, null, null, type);
    }

    public Signature withOperandNames(List<String> names) {
        Objects.requireNonNull(names);
        return new Signature(this, null, names, this.returnType());
    }

    public List<TypeMirror> dynamicOperandTypes() {
        return dynamicOperands().stream().map(Operand::type).toList();
    }

    public TypeMirror getDynamicOperandType(int dynamicOperandIndex) {
        if (dynamicOperandIndex < 0 && dynamicOperandIndex >= dynamicOperandCount()) {
            throw new IllegalArgumentException("Invalid operand index " + dynamicOperandIndex);
        }
        Operand operand = dynamicOperands().get(dynamicOperandIndex);
        if (isVariadicOperand(operand) && !ElementUtils.typeEquals(operand.type(), ProcessorContext.getInstance().getType(Object[].class))) {
            throw new AssertionError("Invalid varargs type.");
        }
        return operand.type();
    }

    public boolean isVariadicOperand(Operand operand) {
        return isVariadic() && operand.dynamicIndex() == dynamicOperandCount() - 1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ElementUtils.getSimpleName(returnType())).append(" ");
        sb.append("(");
        String sep = "";
        for (Operand operand : operands) {
            sb.append(sep);
            sb.append(ElementUtils.getSimpleName(operand.type()));
            sb.append(" ");
            if (isVariadic() && operand.isDynamic() && operand.dynamicIndex() == dynamicOperandCount() - 1) {
                sb.append("...");
            }
            sb.append(operand.name());
            sep = ", ";
        }
        sb.append(')');
        return sb.toString();
    }

    public int dynamicOperandCount() {
        return (int) operands.stream().filter(Operand::isDynamic).count();
    }

    public boolean isVoid() {
        return ElementUtils.isVoid(returnType);
    }

    public List<TypeMirror> operandTypes() {
        return operands().stream().map(Operand::type).toList();
    }

    public List<String> dynamicOperandNames() {
        return operands().stream().filter(Operand::isDynamic).map(Operand::name).toList();
    }

    public static record Operand(TypeMirror type, String name, int index, int dynamicIndex, ConstantOperandModel constant) {

        public Operand(TypeMirror type, String name, int index, int dynamicIndex, ConstantOperandModel constant) {
            this.type = type;
            this.name = name;
            this.index = index;
            this.dynamicIndex = dynamicIndex;
            this.constant = constant;
        }

        public Operand(int operandIndex, ConstantOperandModel constant, String name) {
            this(constant.type(), name, operandIndex, -1, constant);
        }

        public Operand withType(TypeMirror newType) {
            if (ElementUtils.typeEquals(this.type(), newType)) {
                return this;
            }
            return new Operand(newType, name, index, dynamicIndex, constant);
        }

        public Operand withName(String newName) {
            return new Operand(type, newName, index, dynamicIndex, constant);
        }

        public boolean isConstant() {
            return constant != null;
        }

        public boolean isDynamic() {
            return constant == null;
        }

        @Override
        public int dynamicIndex() {
            if (!isDynamic()) {
                throw new UnsupportedOperationException();
            }
            return dynamicIndex;
        }

        /**
         * Returns a conflict free local name.
         */
        public String localName() {
            return name + "_";
        }

    }
}
