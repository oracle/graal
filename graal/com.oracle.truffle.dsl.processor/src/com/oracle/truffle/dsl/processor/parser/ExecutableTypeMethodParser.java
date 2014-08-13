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
package com.oracle.truffle.dsl.processor.parser;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.model.*;

public class ExecutableTypeMethodParser extends NodeMethodParser<ExecutableTypeData> {

    public ExecutableTypeMethodParser(ProcessorContext context, NodeData node) {
        super(context, node);
        setEmitErrors(false);
        setParseNullOnError(false);
        setUseVarArgs(true);
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        MethodSpec spec = createDefaultMethodSpec(method, mirror, false, null);
        List<ParameterSpec> requiredSpecs = new ArrayList<>(spec.getRequired());
        spec.getRequired().clear();

        List<TypeMirror> allowedTypes = getNode().getTypeSystem().getPrimitiveTypeMirrors();
        Set<String> allowedIdentifiers = getNode().getTypeSystem().getTypeIdentifiers();
        for (ParameterSpec originalSpec : requiredSpecs) {
            spec.addRequired(new ParameterSpec(originalSpec, allowedTypes, allowedIdentifiers));
        }
        spec.setIgnoreAdditionalSpecifications(true);
        spec.setIgnoreAdditionalParameters(true);
        spec.setVariableRequiredParameters(true);
        // varargs
        ParameterSpec otherParameters = new ParameterSpec("other", allowedTypes, allowedIdentifiers);
        otherParameters.setSignature(true);
        spec.addRequired(otherParameters);
        return spec;
    }

    @Override
    protected List<TypeMirror> nodeTypeMirrors(NodeData nodeData) {
        return getNode().getTypeSystem().getPrimitiveTypeMirrors();
    }

    @Override
    protected Set<String> nodeTypeIdentifiers(NodeData nodeData) {
        return getNode().getTypeSystem().getTypeIdentifiers();
    }

    @Override
    public final boolean isParsable(ExecutableElement method) {
        if (method.getModifiers().contains(Modifier.STATIC)) {
            return false;
        } else if (method.getModifiers().contains(Modifier.NATIVE)) {
            return false;
        }
        return method.getSimpleName().toString().startsWith("execute");
    }

    @Override
    public ExecutableTypeData create(TemplateMethod method, boolean invalid) {
        TypeData resolvedType = method.getReturnType().getTypeSystemType();
        return new ExecutableTypeData(method, method.getMethod(), getNode().getTypeSystem(), resolvedType);
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return null;
    }

}
