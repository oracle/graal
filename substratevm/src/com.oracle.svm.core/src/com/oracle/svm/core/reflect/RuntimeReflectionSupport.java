/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.espresso.classfile.ConstantPool;
import com.oracle.svm.espresso.classfile.ConstantPool.Tag;
import com.oracle.svm.espresso.classfile.attributes.MethodParametersAttribute;
import com.oracle.svm.guest.staging.reflect.target.ReflectionObjectFactory;

/** Core-side support for reflection metadata whose representation is specific to runtime-loaded classes. */
public final class RuntimeReflectionSupport {
    private RuntimeReflectionSupport() {
    }

    public static Parameter[] getRuntimeReflectParameters(Executable executable, Object parameterMetadata) {
        MethodParametersAttribute methodParameters = (MethodParametersAttribute) parameterMetadata;
        DynamicHub declaringClass = DynamicHub.fromClass(executable.getDeclaringClass());
        ConstantPool constantPool = CremaSupport.singleton().getConstantPool(declaringClass);
        int entryCount = methodParameters.entryCount();
        Parameter[] parameters = new Parameter[entryCount];
        int constantPoolLength = constantPool.length();
        for (int i = 0; i < entryCount; i++) {
            MethodParametersAttribute.Entry entry = methodParameters.entryAt(i);
            int nameIndex = entry.getNameIndex();
            if (nameIndex >= constantPoolLength) {
                throw new IllegalArgumentException("Constant pool index out of bounds");
            }
            String name = null;
            if (nameIndex != 0) {
                if (constantPool.tagAt(nameIndex) != Tag.UTF8) {
                    throw new IllegalArgumentException("Wrong type at constant pool index");
                }
                name = constantPool.utf8At(nameIndex, "parameter name").toString();
            }
            parameters[i] = ReflectionObjectFactory.newParameter(executable, i, name, entry.getAccessFlags());
        }
        return parameters;
    }
}
