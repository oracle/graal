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

import com.oracle.svm.core.hub.crema.CremaResolvedJavaField;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaMethod;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaRecordComponent;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaType;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.ArrayList;

public final class CremaResolvedObjectType extends InterpreterResolvedObjectType implements CremaResolvedJavaType {

    public CremaResolvedObjectType(Symbol<Type> type, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass, InterpreterResolvedObjectType[] interfaces,
                    InterpreterConstantPool constantPool, Class<?> javaClass, boolean isWordType) {
        super(type, modifiers, componentType, superclass, interfaces, constantPool, javaClass, isWordType);
    }

    @Override
    public CremaResolvedJavaField[] getDeclaredFields() {
        // (GR-69098)
        throw VMError.unimplemented("getDeclaredFields");
    }

    @Override
    public CremaResolvedJavaMethod[] getDeclaredCremaMethods() {
        // filter out constructors
        ArrayList<CremaResolvedJavaMethod> result = new ArrayList<>();
        for (InterpreterResolvedJavaMethod declaredMethod : getDeclaredMethods()) {
            if (!declaredMethod.isConstructor()) {
                result.add((CremaResolvedJavaMethod) declaredMethod);
            }
        }
        return result.toArray(new CremaResolvedJavaMethod[0]);
    }

    @Override
    public CremaResolvedJavaMethod[] getDeclaredConstructors() {
        ArrayList<CremaResolvedJavaMethod> result = new ArrayList<>();
        for (InterpreterResolvedJavaMethod declaredMethod : getDeclaredMethods()) {
            if (declaredMethod.isConstructor()) {
                result.add((CremaResolvedJavaMethod) declaredMethod);
            }
        }
        return result.toArray(new CremaResolvedJavaMethod[0]);
    }

    @Override
    public CremaResolvedJavaRecordComponent[] getRecordComponents() {
        // (GR-69095)
        throw VMError.unimplemented("getRecordComponents");
    }

    @Override
    public byte[] getRawAnnotations() {
        // (GR-69096)
        throw VMError.unimplemented("getRawAnnotations");
    }

    @Override
    public byte[] getRawTypeAnnotations() {
        // (GR-69096)
        throw VMError.unimplemented("getRawTypeAnnotations");
    }

    @Override
    public CremaEnclosingMethodInfo getEnclosingMethod() {
        // (GR-69095)
        throw VMError.unimplemented("getEnclosingMethod");
    }

    @Override
    public JavaType[] getDeclaredClasses() {
        // (GR-69095)
        throw VMError.unimplemented("getDeclaredClasses");
    }

    @Override
    public JavaType[] getPermittedSubClasses() {
        // (GR-69095)
        throw VMError.unimplemented("getPermittedSubClasses");
    }

    @Override
    public ResolvedJavaType[] getNestMembers() {
        // (GR-69095)
        throw VMError.unimplemented("getNestMembers");
    }

    @Override
    public ResolvedJavaType getNestHost() {
        // (GR-69095)
        throw VMError.unimplemented("getNestHost");
    }
}
