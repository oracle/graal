/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.dsl.processor.node;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.template.*;

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
        List<String> childNames = Utils.getAnnotationValueList(String.class, mirror, "value");
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

        MethodSpec spec = new MethodSpec(new InheritsParameterSpec("child", baseType));
        addDefaultFieldMethodSpec(spec);
        ParameterSpec childSpec = new ParameterSpec("castedChild", baseType);
        childSpec.setSignature(true);
        spec.addRequired(childSpec);
        return spec;
    }

    @Override
    public CreateCastData create(TemplateMethod method, boolean invalid) {
        AnnotationMirror mirror = method.getMarkerAnnotation();
        List<String> childNames = Utils.getAnnotationValueList(String.class, mirror, "value");
        CreateCastData cast = new CreateCastData(method, childNames);
        AnnotationValue value = Utils.getAnnotationValue(mirror, "value");
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
            } else if (!Utils.typeEquals(type, child.getNodeType())) {
                cast.addError(value, "All child nodes for a cast must have the same node type.");
                continue;
            }
        }
        return cast;
    }

    private static class InheritsParameterSpec extends ParameterSpec {

        public InheritsParameterSpec(String name, TypeMirror... allowedTypes) {
            super(name, Arrays.asList(allowedTypes));
        }

        @Override
        public boolean matches(TypeMirror actualType) {
            boolean found = false;
            for (TypeMirror specType : getAllowedTypes()) {
                if (Utils.isAssignable(actualType, specType)) {
                    found = true;
                    break;
                }
            }
            return found;
        }
    }
}
