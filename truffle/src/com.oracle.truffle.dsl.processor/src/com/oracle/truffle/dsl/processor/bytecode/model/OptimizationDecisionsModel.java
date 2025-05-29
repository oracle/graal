/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

public class OptimizationDecisionsModel {

    /**
     * A quicken model that is designed to be persistable and comparable, so internally uses
     * strings.
     */
    public record QuickenDecision(String operation, Set<String> specializations, List<String> types) {

        public QuickenDecision(OperationModel operationModel, Collection<SpecializationData> specializations) {
            this(operationModel, specializations, null);
        }

        public QuickenDecision(OperationModel operationModel, Collection<SpecializationData> specializations, List<TypeMirror> types) {
            this(operationModel.name, translateSpecializations(specializations), translateTypes(types));
        }

        private static List<String> translateTypes(List<TypeMirror> types) {
            if (types == null) {
                // auto infer types on resolve
                return null;
            }
            return types.stream().map((e) -> ElementUtils.getQualifiedName(e)).toList();
        }

        private static Set<String> translateSpecializations(Collection<SpecializationData> specializations) {
            List<String> allIds = specializations.stream().filter((s) -> s.getMethod() != null).map((s) -> s.getMethodName()).toList();
            Set<String> allIdsSet = new HashSet<>(allIds);
            return allIdsSet;
        }

        public ResolvedQuickenDecision resolve(BytecodeDSLModel model) {
            OperationModel operationModel = model.getOperationByName(operation);
            List<SpecializationData> specializationModels = operationModel.instruction.nodeData.findSpecializationsByName(this.specializations);
            ProcessorContext c = ProcessorContext.getInstance();
            List<TypeMirror> parameterTypes;
            if (this.types == null) {
                parameterTypes = null;
                for (SpecializationData specializationData : specializationModels) {
                    List<TypeMirror> specializationTypes = operationModel.getSpecializationSignature(specializationData).signature().getDynamicOperandTypes();
                    if (parameterTypes == null) {
                        parameterTypes = new ArrayList<>(specializationTypes);
                    } else {
                        for (int i = 0; i < specializationTypes.size(); i++) {
                            TypeMirror type1 = specializationTypes.get(i);
                            TypeMirror type2 = parameterTypes.get(i);
                            if (!ElementUtils.typeEquals(type1, type2)) {
                                parameterTypes.set(i, c.getType(Object.class));
                            }
                        }
                    }
                }
            } else {
                parameterTypes = this.types.stream().map((typeName) -> ElementUtils.fromQualifiedName(typeName)).toList();
            }
            return new ResolvedQuickenDecision(operationModel, specializationModels, parameterTypes);
        }
    }

    public record ResolvedQuickenDecision(OperationModel operation, List<SpecializationData> specializations, List<TypeMirror> types) {
    }

}
