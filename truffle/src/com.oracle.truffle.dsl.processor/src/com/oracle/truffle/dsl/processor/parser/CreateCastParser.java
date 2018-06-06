/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.dsl.processor.parser;

import java.lang.annotation.Annotation;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.api.dsl.CreateCast;
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
    public Class<? extends Annotation> getAnnotationType() {
        return CreateCast.class;
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
        TypeMirror baseType = getContext().getTruffleTypes().getNode();
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
