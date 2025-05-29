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
package com.oracle.truffle.dsl.processor.bytecode.parser;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isAssignable;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isObject;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEqualsAny;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.SuppressFBWarnings;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.model.ConstantOperandModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.ConstantOperandsModel;
import com.oracle.truffle.dsl.processor.bytecode.model.Signature;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.model.MessageContainer;

public class SpecializationSignatureParser {

    /**
     * Represents a signature parsed from a given specialization of a custom operation. In addition
     * to the regular signature information, this record includes the operand names declared by the
     * specialization.
     */
    public record SpecializationSignature(Signature signature, List<String> operandNames) {
    }

    final ProcessorContext context;
    final TruffleTypes types;

    public SpecializationSignatureParser(ProcessorContext context) {
        this.context = context;
        this.types = context.getTypes();
    }

    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "Calls to params poll() as expected. FindBugs false positive.")
    public SpecializationSignature parse(ExecutableElement specialization, MessageContainer errorTarget, ConstantOperandsModel constantOperands) {
        boolean isValid = true;
        boolean isFallback = ElementUtils.findAnnotationMirror(specialization, types.Fallback) != null;

        Queue<? extends VariableElement> params = new ArrayDeque<>(specialization.getParameters());

        // First: an optional VirtualFrame parameter.
        if (!params.isEmpty() && isAssignable(peekType(params), types.Frame)) {
            if (!isAssignable(params.poll().asType(), types.VirtualFrame)) {
                errorTarget.addError(specialization, "Frame parameter must have type VirtualFrame.");
                isValid = false;
            }
        }
        skipDSLParameters(params);

        // Second: all operands (constant and dynamic).
        List<VariableElement> operands = new ArrayList<>();
        boolean hasVariadic = false;
        int variadicOffset = 0;
        while (!params.isEmpty()) {
            operands.add(params.poll());
            skipDSLParameters(params);
        }

        List<String> operandNames = new ArrayList<>(operands.size());
        int numConstantOperands = constantOperands.before().size() + constantOperands.after().size();
        if (operands.size() < numConstantOperands) {
            errorTarget.addError(specialization, "Specialization should declare at least %d operand%s (one for each %s).",
                            numConstantOperands,
                            numConstantOperands == 1 ? "" : "s",
                            getSimpleName(types.ConstantOperand));
            isValid = false;
        } else {
            // Operand layout: [consts_before..., dynamic_params..., consts_after...]
            int numDynamicOperands = operands.size() - numConstantOperands;

            // Process constant operands (before).
            for (int i = 0; i < constantOperands.before().size(); i++) {
                VariableElement operand = operands.get(i);
                ConstantOperandModel constantOperand = constantOperands.before().get(i);
                isValid = checkConstantOperandParam(operand, constantOperand, errorTarget) && isValid;
                operandNames.add(constantOperand.getNameOrDefault(operand.getSimpleName().toString()));
            }

            // Process dynamic operands.
            int dynamicOffset = constantOperands.before().size();
            for (int i = 0; i < numDynamicOperands; i++) {
                VariableElement dynamicOperand = operands.get(dynamicOffset + i);
                if (hasVariadic) {
                    // The variadic operand should be the last dynamic operand.
                    if (isVariadic(dynamicOperand)) {
                        errorTarget.addError(dynamicOperand, "Multiple variadic operands not allowed to an operation. Split up the operation if such behaviour is required.");
                    } else {
                        errorTarget.addError(dynamicOperand, "Non-variadic operands must precede variadic operands.");
                    }
                    isValid = false;
                } else if (isVariadic(dynamicOperand)) {
                    hasVariadic = true;
                    variadicOffset = ElementUtils.getAnnotationValue(Integer.class, ElementUtils.findAnnotationMirror(dynamicOperand, types.Variadic), "startOffset");

                    if (!ElementUtils.typeEquals(dynamicOperand.asType(), new ArrayCodeTypeMirror(context.getDeclaredType(Object.class)))) {
                        errorTarget.addError(dynamicOperand, "Variadic operand must have type Object[].");
                        isValid = false;
                    }
                    if (variadicOffset < 0) {
                        errorTarget.addError(dynamicOperand, "Variadic startOffset must be positive.");
                        isValid = false;
                    }
                }

                if (isFallback) {
                    /**
                     * In the regular DSL, fallback specializations can take non-Object arguments if
                     * they agree with the type signature of the abstract execute method. Since we
                     * synthesize our own execute method that only takes Object arguments, fallback
                     * specializations with non-Object parameters are unsupported.
                     */
                    if (!isObject(dynamicOperand.asType()) && !isVariadic(dynamicOperand)) {
                        if (errorTarget != null) {
                            errorTarget.addError(dynamicOperand, "Operands to @%s specializations of Operation nodes must have type %s.",
                                            getSimpleName(types.Fallback),
                                            getSimpleName(context.getDeclaredType(Object.class)));
                        }
                        isValid = false;
                    }
                }

                operandNames.add(dynamicOperand.getSimpleName().toString());
            }

            // Process constant operands (after).
            int constantAfterOffset = dynamicOffset + numDynamicOperands;
            for (int i = 0; i < constantOperands.after().size(); i++) {
                VariableElement operand = operands.get(constantAfterOffset + i);
                ConstantOperandModel constantOperand = constantOperands.after().get(i);
                isValid = checkConstantOperandParam(operand, constantOperand, errorTarget) && isValid;
                operandNames.add(constantOperand.getNameOrDefault(operand.getSimpleName().toString()));
            }
        }

        if (!isValid) {
            return null;
        }

        List<TypeMirror> operandTypes = operands.stream().map(v -> v.asType()).toList();
        TypeMirror returnType = specialization.getReturnType();
        if (ElementUtils.canThrowTypeExact(specialization.getThrownTypes(), CustomOperationParser.types().UnexpectedResultException)) {
            returnType = context.getDeclaredType(Object.class);
        }
        Signature signature = new Signature(returnType, operandTypes, hasVariadic, variadicOffset, constantOperands.before().size(), constantOperands.after().size());

        return new SpecializationSignature(signature, operandNames);
    }

    private boolean isVariadic(VariableElement param) {
        return ElementUtils.findAnnotationMirror(param, types.Variadic) != null;
    }

    private static TypeMirror peekType(Queue<? extends VariableElement> queue) {
        return queue.peek().asType();
    }

    /**
     * DSL parameters aren't relevant to signature calculations. This helper should be called
     * between each parameter.
     */
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "Calls to params poll() as expected. FindBugs false positive.")
    private static void skipDSLParameters(Queue<? extends VariableElement> queue) {
        while (!queue.isEmpty() && isDSLParameter(queue.peek())) {
            queue.poll();
        }
    }

    private static boolean isDSLParameter(VariableElement param) {
        for (AnnotationMirror mir : param.getAnnotationMirrors()) {
            if (typeEqualsAny(mir.getAnnotationType(), CustomOperationParser.types().Cached, CustomOperationParser.types().CachedLibrary, CustomOperationParser.types().Bind)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkConstantOperandParam(VariableElement constantOperandParam, ConstantOperandModel constantOperand, MessageContainer errorTarget) {
        if (isVariadic(constantOperandParam)) {
            errorTarget.addError(constantOperandParam, "Constant operand parameter cannot be variadic.");
            return false;
        }
        TypeMirror parameterType = constantOperandParam.asType();
        TypeMirror constantType = constantOperand.type();
        if (!ElementUtils.typeEquals(parameterType, constantType)) {
            errorTarget.addError(constantOperandParam, "Constant operand parameter must have type %s.", getSimpleName(constantType));
            return false;
        }
        return true;
    }

    /**
     * Computes a {@link Signature} from the node's set of specializations. Returns {@code null} if
     * there are no specializations or the specializations do not share a common signature.
     * <p>
     * Also accumulates individual signatures into the {@code signatures} parameter, so they can be
     * inspected individually.
     */
    public static Signature createPolymorphicSignature(List<SpecializationSignature> signatures, List<ExecutableElement> specializations, MessageContainer customOperation) {
        assert !signatures.isEmpty();
        assert signatures.size() == specializations.size();
        Signature polymorphicSignature = signatures.get(0).signature();
        for (int i = 1; i < signatures.size(); i++) {
            polymorphicSignature = mergeSignatures(signatures.get(i).signature(), polymorphicSignature, specializations.get(i), customOperation);
            if (polymorphicSignature == null) {
                break;
            }
        }
        return polymorphicSignature;
    }

    private static Signature mergeSignatures(Signature a, Signature b, Element el, MessageContainer errorTarget) {
        if (a.isVariadic != b.isVariadic) {
            if (errorTarget != null) {
                errorTarget.addError(el, "Error calculating operation signature: either all or none of the specializations must be variadic (i.e., have a @%s annotated parameter)",
                                getSimpleName(CustomOperationParser.types().Variadic));
            }
            return null;
        }
        if (a.isVoid != b.isVoid) {
            if (errorTarget != null) {
                errorTarget.addError(el, "Error calculating operation signature: either all or none of the specializations must be declared void.");
            }
            return null;
        }
        assert a.constantOperandsBeforeCount == b.constantOperandsBeforeCount;
        assert a.constantOperandsAfterCount == b.constantOperandsAfterCount;
        if (a.dynamicOperandCount != b.dynamicOperandCount) {
            if (errorTarget != null) {
                errorTarget.addError(el, "Error calculating operation signature: all specializations must have the same number of operands.");
            }
            return null;
        }

        TypeMirror newReturnType = mergeIfPrimitiveType(a.context, a.returnType, b.returnType);
        List<TypeMirror> mergedTypes = new ArrayList<>(a.operandTypes.size());
        for (int i = 0; i < a.operandTypes.size(); i++) {
            mergedTypes.add(mergeIfPrimitiveType(a.context, a.operandTypes.get(i), b.operandTypes.get(i)));
        }
        return new Signature(newReturnType, mergedTypes, a.isVariadic, a.variadicOffset, a.constantOperandsBeforeCount, a.constantOperandsAfterCount);
    }

    private static TypeMirror mergeIfPrimitiveType(ProcessorContext context, TypeMirror a, TypeMirror b) {
        if (ElementUtils.typeEquals(ElementUtils.boxType(context, a), ElementUtils.boxType(context, b))) {
            return a;
        } else {
            return context.getType(Object.class);
        }
    }
}
