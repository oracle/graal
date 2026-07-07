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

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

final class InstructionName {

    private final InstructionKind kind;
    private final String baseInstructionName;
    private final String variantName;
    private final List<String> quickeningNames;

    /*
     * The instruction descriptor name used in dumps and lookup maps; for example,
     * c.StoreReturnValue_offset1$StoreReturnValueBoxingEliminated$unboxed.
     */
    public final String instructionName;
    /*
     * The full quickening path used in specialization hooks and execute method names; for example,
     * StoreReturnValueBoxingEliminated$unboxed.
     */
    public final String quickeningName;
    /*
     * The Java identifier fragment used in generated method and class names; for example,
     * StoreReturnValue_offset1$StoreReturnValueBoxingEliminated$unboxed_.
     */
    public final String internalName;
    /*
     * The Java constant name used for the instruction opcode; for example,
     * STORE_RETURN_VALUE$$OFFSET1$STORE_RETURN_VALUE_BOXING_ELIMINATED$UNBOXED_.
     */
    public final String constantName;

    private InstructionName(InstructionKind kind, String baseInstructionName, String variantName, List<String> quickeningNames) {
        this.kind = kind;
        this.baseInstructionName = baseInstructionName;
        this.variantName = variantName;
        this.quickeningNames = quickeningNames;
        this.instructionName = createInstructionName();
        this.quickeningName = createQuickeningName();
        this.internalName = createInternalName(instructionName);
        this.constantName = createConstantName();
    }

    static InstructionName create(InstructionKind kind, String baseInstructionName) {
        return new InstructionName(kind, baseInstructionName, null, List.of());
    }

    InstructionName withVariant(String newVariantName) {
        if (variantName != null) {
            throw new AssertionError("Instruction already has a variant name: " + instructionName);
        }
        return new InstructionName(kind, baseInstructionName, newVariantName, quickeningNames);
    }

    InstructionName withQuickening(String newQuickeningName) {
        List<String> newQuickeningNames = new ArrayList<>(quickeningNames.size() + 1);
        newQuickeningNames.addAll(quickeningNames);
        newQuickeningNames.add(newQuickeningName);
        return new InstructionName(kind, baseInstructionName, variantName, List.copyOf(newQuickeningNames));
    }

    private String createInstructionName() {
        String result = baseInstructionName;
        if (variantName != null) {
            result = result + "_" + variantName;
        }
        if (!quickeningNames.isEmpty()) {
            result = result + "$" + String.join("$", quickeningNames);
        }
        return result;
    }

    private String createQuickeningName() {
        List<String> normalizedNames = new ArrayList<>(quickeningNames.size());
        for (String currentQuickeningName : quickeningNames) {
            normalizedNames.add(currentQuickeningName.replace('#', '_'));
        }
        return String.join("$", normalizedNames);
    }

    private String createInternalName(String renderedInstructionName) {
        String operationName = switch (kind) {
            case CUSTOM -> {
                if (!renderedInstructionName.startsWith("c.")) {
                    throw new AssertionError("Unexpected custom operation name: " + renderedInstructionName);
                }
                yield renderedInstructionName.substring(2) + "_";
            }
            case CUSTOM_SHORT_CIRCUIT -> {
                if (!renderedInstructionName.startsWith("sc.")) {
                    throw new AssertionError("Unexpected short-circuit custom operation name: " + renderedInstructionName);
                }
                yield renderedInstructionName.substring(3) + "_";
            }
            default -> renderedInstructionName;
        };
        StringBuilder b = new StringBuilder(operationName);
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            switch (c) {
                case '.':
                    if (i + 1 < b.length()) {
                        b.setCharAt(i + 1, Character.toUpperCase(b.charAt(i + 1)));
                    }
                    b.deleteCharAt(i);
                    break;
                case '#':
                    b.setCharAt(i, '$');
                    break;
            }
        }
        return b.toString();
    }

    private String createConstantName() {
        if (variantName == null) {
            return ElementUtils.createConstantName(internalName);
        }

        String baseInternalName = createInternalName(baseInstructionName);
        String suffix = "";
        switch (kind) {
            case CUSTOM:
            case CUSTOM_SHORT_CIRCUIT:
                if (!baseInternalName.endsWith("_")) {
                    throw new AssertionError("Unexpected custom operation internal name: " + baseInternalName);
                }
                baseInternalName = baseInternalName.substring(0, baseInternalName.length() - 1);
                suffix = "_";
                break;
            default:
                break;
        }

        StringBuilder b = new StringBuilder(ElementUtils.createConstantName(baseInternalName));
        b.append("$$").append(ElementUtils.createConstantName(variantName));
        if (!quickeningName.isEmpty()) {
            b.append("$").append(ElementUtils.createConstantName(quickeningName));
        }
        b.append(suffix);
        return b.toString();
    }

}
