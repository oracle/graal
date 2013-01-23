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
package com.oracle.truffle.codegen.processor.typesystem;

import javax.lang.model.element.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.template.*;

abstract class TypeSystemMethodParser<E extends TemplateMethod> extends TemplateMethodParser<TypeSystemData, E> {

    public TypeSystemMethodParser(ProcessorContext context, TypeSystemData typeSystem) {
        super(context, typeSystem);
    }

    public TypeSystemData getTypeSystem() {
        return template;
    }

    @Override
    public final boolean isParsable(ExecutableElement method) {
        return Utils.findAnnotationMirror(getContext().getEnvironment(), method, getAnnotationType()) != null;
    }

    protected TypeData findTypeByMethodName(ExecutableElement method, AnnotationMirror annotationMirror, String prefix) {
        String methodName = method.getSimpleName().toString();
        if (!methodName.startsWith(prefix)) {
            String annotationName = Utils.getSimpleName(annotationMirror.getAnnotationType());
            getContext().getLog().error(method, "Methods annotated with %s must match the pattern '%s'.", annotationName, String.format("%s${typeName}", prefix));
            return null;
        }
        String typeName = methodName.substring(prefix.length(), methodName.length());
        TypeData type = getTypeSystem().findType(typeName);
        if (type == null) {
            String annotationName = TypeSystem.class.getSimpleName();
            getContext().getLog().error(method, "Type '%s' is not declared in this @%s.", typeName, annotationName);
            return null;
        }

        return type;
    }

}
