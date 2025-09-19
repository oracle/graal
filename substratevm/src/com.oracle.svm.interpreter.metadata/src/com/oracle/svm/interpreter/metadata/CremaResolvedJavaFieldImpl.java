/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaField;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.espresso.classfile.ParserField;

import jdk.vm.ci.meta.UnresolvedJavaType;

public class CremaResolvedJavaFieldImpl extends InterpreterResolvedJavaField implements CremaResolvedJavaField {
    public static final CremaResolvedJavaFieldImpl[] EMPTY_ARRAY = new CremaResolvedJavaFieldImpl[0];

    CremaResolvedJavaFieldImpl(InterpreterResolvedObjectType declaringClass, ParserField f, int offset) {
        super(f.getName(), f.getType(), f.getFlags(),
                        /*- resolvedType */ null,
                        declaringClass,
                        offset,
                        /*- constantValue */ null,
                        /*- isWordStorage */ false);
    }

    public static CremaResolvedJavaFieldImpl createAtRuntime(InterpreterResolvedObjectType declaringClass, ParserField f, int offset) {
        return new CremaResolvedJavaFieldImpl(declaringClass, f, offset);
    }

    public InterpreterResolvedJavaType getResolvedType() {
        if (resolvedType == null) {
            Class<?> cls = CremaSupport.singleton().resolveOrThrow(UnresolvedJavaType.create(getSymbolicType().toString()), getDeclaringClass());
            resolvedType = (InterpreterResolvedJavaType) DynamicHub.fromClass(cls).getInterpreterType();
        }
        return resolvedType;
    }

    @Override
    public boolean isTrustedFinal() {
        return isFinal() && (isStatic() || Record.class.isAssignableFrom(getDeclaringClass().getJavaClass()) /*- GR-69549: || getDeclaringClass().isHidden() */);
    }

    @Override
    public byte[] getRawAnnotations() {
        /* (GR-69096) resolvedJavaField.getRawAnnotations() */
        return new byte[0];
    }

    @Override
    public byte[] getRawTypeAnnotations() {
        /* (GR-69096) resolvedJavaMethod.getRawTypeAnnotations() */
        return new byte[0];
    }

    @Override
    public String getGenericSignature() {
        /* (GR-69096) resolvedJavaMethod.getGenericSignature() */
        return getSymbolicType().toString();
    }
}
