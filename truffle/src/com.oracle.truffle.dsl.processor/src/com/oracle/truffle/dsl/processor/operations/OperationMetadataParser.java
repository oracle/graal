/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.operations;

import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;

public class OperationMetadataParser extends AbstractParser<OperationMetadataData> {

    private final OperationsData parentData;

    public OperationMetadataParser(OperationsData parentData) {
        this.parentData = parentData;
    }

    @Override
    protected OperationMetadataData parse(Element element, List<AnnotationMirror> mirror) {
        AnnotationMirror mir = ElementUtils.findAnnotationMirror(element.getAnnotationMirrors(), getAnnotationType());
        OperationMetadataData data = new OperationMetadataData(parentData, context, element, mir);

        if (!(element instanceof VariableElement)) {
            data.addError(element, "@Metadata must be attached to a field");
            return data;
        }

        VariableElement varElement = (VariableElement) element;

        if (!varElement.getModifiers().containsAll(Set.of(Modifier.STATIC, Modifier.FINAL))) {
            data.addError(element, "@Metadata must be attached to a static final field");
        }

        if (varElement.getModifiers().contains(Modifier.PRIVATE)) {
            data.addError(element, "@Metadata field must not be private");
        }

        TypeMirror fieldType = varElement.asType();

        TypeMirror metadataType = null;

        if (fieldType.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) fieldType;
            if (declaredType.asElement().equals(types.MetadataKey.asElement())) {
                metadataType = declaredType.getTypeArguments().get(0);
            }
        }

        if (metadataType == null) {
            data.addError(element, "@Metadata field must be of type MetadataKey<>");
        }

        data.setType(metadataType);

        String name = element.getSimpleName().toString();
        if (ElementUtils.getAnnotationValue(mir, "value") != null) {
            name = (String) ElementUtils.getAnnotationValue(mir, "value").getValue();
        }

        data.setName(name);

        return data;
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.GenerateOperations_Metadata;
    }

}
