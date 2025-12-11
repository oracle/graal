/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.bytecode.generator;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class FrameTagConstantsElement extends AbstractElement {

    private final Map<TypeMirror, VariableElement> mapping;

    FrameTagConstantsElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "FrameTags");

        // List of FrameSlotKinds we need to declare constants for.
        Map<String, TypeMirror> frameTypes = new HashMap<>();
        for (TypeMirror boxingEliminatedType : parent.model.boxingEliminatedTypes) {
            frameTypes.put(ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxingEliminatedType)), boxingEliminatedType);
        }
        frameTypes.put("Object", declaredType(Object.class));
        frameTypes.put("Illegal", null);

        // Construct the constants, iterating over the enum fields to find the tag values.
        Map<TypeMirror, VariableElement> result = new HashMap<>();
        TypeElement frameSlotKindType = ElementUtils.castTypeElement(types.FrameSlotKind);
        int index = 0;
        for (VariableElement var : ElementFilter.fieldsIn(CompilerFactory.getCompiler(frameSlotKindType).getAllMembersInDeclarationOrder(parent.context.getEnvironment(), frameSlotKindType))) {
            if (var.getKind() != ElementKind.ENUM_CONSTANT) {
                continue;
            }
            String frameSlotKind = var.getSimpleName().toString();
            if (frameTypes.containsKey(frameSlotKind)) {
                CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(byte.class), frameSlotKind.toUpperCase());
                fld.createInitBuilder().string(index + " /* FrameSlotKind." + frameSlotKind + ".tag */");
                this.add(fld);
                result.put(frameTypes.remove(frameSlotKind), fld);
            }
            index++;
        }
        if (!frameTypes.isEmpty()) {
            throw new AssertionError(String.format("Could not find a FrameSlotKind for some types: %s", frameTypes.keySet()));
        }
        mapping = result;
    }

    VariableElement get(TypeMirror type) {
        return mapping.get(type);
    }

    VariableElement getObject() {
        return mapping.get(declaredType(Object.class));
    }

    VariableElement getIllegal() {
        return mapping.get(null);
    }
}
