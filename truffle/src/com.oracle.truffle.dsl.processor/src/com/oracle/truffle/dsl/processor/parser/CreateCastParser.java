/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.parser;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.CreateCastData;
import com.oracle.truffle.dsl.processor.model.MethodSpec;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.ParameterSpec;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;

public class CreateCastParser extends NodeMethodParser<CreateCastData> {

    public CreateCastParser(ProcessorContext context, NodeData operation) {
        super(context, operation);
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.CreateCast;
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        List<String> childNames = ElementUtils.getAnnotationValueList(String.class, mirror, "value");
        NodeChildData foundChild = null;
        for (String childName : childNames) {
            foundChild = getNode().findChild(childName);
            if (foundChild != null) {
                break;
            }
        }
        TypeMirror baseType = types.Node;
        if (foundChild != null) {
            baseType = foundChild.getOriginalType();
        }

        MethodSpec spec = new MethodSpec(new ParameterSpec("child", baseType));
        addDefaultFieldMethodSpec(spec);
        ParameterSpec childSpec = new ParameterSpec("castedChild", baseType);
        childSpec.setSignature(true);
        spec.addRequired(childSpec);
        return spec;
    }

    @Override
    public CreateCastData create(TemplateMethod method, boolean invalid) {
        AnnotationMirror mirror = method.getMarkerAnnotation();
        List<String> childNames = ElementUtils.getAnnotationValueList(String.class, mirror, "value");
        CreateCastData cast = new CreateCastData(method, childNames);
        AnnotationValue value = ElementUtils.getAnnotationValue(mirror, "value");
        TypeMirror type = null;
        if (childNames == null || childNames.isEmpty()) {
            cast.addError(value, "No value specified but required.");
            return cast;
        }

        for (String childName : childNames) {
            NodeChildData child = getNode().findChild(childName);
            if (child == null) {
                // error
                cast.addError(value, "Specified child '%s' not found.", childName);
                continue;
            }
            if (type == null) {
                type = child.getNodeType();
            } else if (!ElementUtils.typeEquals(type, child.getNodeType())) {
                cast.addError(value, "All child nodes for a cast must have the same node type.");
                continue;
            }
        }
        return cast;
    }

}
