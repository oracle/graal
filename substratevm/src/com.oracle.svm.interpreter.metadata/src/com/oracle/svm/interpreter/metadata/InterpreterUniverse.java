/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.OptionalInt;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public interface InterpreterUniverse {
    Collection<? extends ResolvedJavaType> getTypes();

    Collection<? extends ResolvedJavaField> getFields();

    Collection<? extends ResolvedJavaMethod> getMethods();

    ResolvedJavaType lookupType(Class<?> clazz);

    Class<?> lookupClass(ResolvedJavaType type);

    Collection<? extends ResolvedJavaField> getAllDeclaredFields(ResolvedJavaType type);

    Collection<? extends ResolvedJavaMethod> getAllDeclaredMethods(ResolvedJavaType type);

    OptionalInt getTypeIndexFor(ResolvedJavaType type);

    OptionalInt getFieldIndexFor(ResolvedJavaField field);

    OptionalInt getMethodIndexFor(ResolvedJavaMethod method);

    ResolvedJavaType getTypeAtIndex(int index);

    ResolvedJavaField getFieldAtIndex(int index);

    ResolvedJavaMethod getMethodAtIndex(int index);

    ResolvedJavaMethod getMethodForESTOffset(int methodIndex);

    ResolvedJavaMethod getMethodFromMethodId(int methodId);
}
