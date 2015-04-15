/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.model;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.model.MethodSpec.TypeDef;

public class ParameterSpec {

    private final String name;
    private final Collection<TypeMirror> allowedTypes;
    private final boolean anyType;

    /** Type is bound to local final variable. */
    private boolean local;
    private boolean signature;
    private boolean allowSubclasses = true;

    /** Optional bound execution of node. */
    private NodeExecutionData execution;
    private TypeDef typeDefinition;

    public ParameterSpec(String name, Collection<TypeMirror> allowedTypes) {
        this.name = name;
        this.allowedTypes = allowedTypes;
        boolean anyTypeTemp = false;
        for (TypeMirror type : allowedTypes) {
            if (ElementUtils.isObject(type)) {
                anyTypeTemp = true;
                break;
            }
        }
        this.anyType = anyTypeTemp;
    }

    public ParameterSpec(ParameterSpec original, TypeMirror newType) {
        this(original.name, newType);
        this.local = original.local;
        this.signature = original.signature;
        this.execution = original.execution;
        this.typeDefinition = original.typeDefinition;
        this.allowSubclasses = original.allowSubclasses;
    }

    public ParameterSpec(String name, TypeMirror type) {
        this(name, Arrays.asList(type));
    }

    public void setAllowSubclasses(boolean allowSubclasses) {
        this.allowSubclasses = allowSubclasses;
    }

    public NodeExecutionData getExecution() {
        return execution;
    }

    public void setExecution(NodeExecutionData executionData) {
        this.execution = executionData;
        this.signature = execution != null;
    }

    public void setSignature(boolean signature) {
        this.signature = signature;
    }

    void setTypeDefinition(TypeDef typeDefinition) {
        this.typeDefinition = typeDefinition;
    }

    TypeDef getTypeDefinition() {
        return typeDefinition;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public boolean isSignature() {
        return signature;
    }

    public boolean isLocal() {
        return local;
    }

    public String getName() {
        return name;
    }

    public Collection<TypeMirror> getAllowedTypes() {
        return allowedTypes;
    }

    public boolean matches(VariableElement variable) {
        if (anyType) {
            return true;
        } else {
            for (TypeMirror type : allowedTypes) {
                if (ElementUtils.typeEquals(variable.asType(), type)) {
                    return true;
                }
            }
            if (allowSubclasses) {
                for (TypeMirror type : allowedTypes) {
                    if (ElementUtils.isSubtypeBoxed(ProcessorContext.getInstance(), variable.asType(), type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return toSignatureString(false);
    }

    public String toSignatureString(boolean typeOnly) {
        StringBuilder builder = new StringBuilder();
        if (typeDefinition != null) {
            builder.append("<" + typeDefinition.getName() + ">");
        } else if (getAllowedTypes().size() >= 1) {
            builder.append(ElementUtils.getSimpleName(getAllowedTypes().iterator().next()));
        } else {
            builder.append("void");
        }
        if (!typeOnly) {
            builder.append(" ");
            builder.append(getName());
        }
        return builder.toString();
    }

}
