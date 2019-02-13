/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.model;

import java.util.Arrays;
import java.util.Collection;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
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

    public boolean isCached() {
        return false;
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
            if (!allowSubclasses) {
                if (allowedTypes.size() > 0 && !ElementUtils.typeEquals(allowedTypes.iterator().next(), variable.asType())) {
                    return false;
                }
            }
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
