/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

abstract class ReflectionObjectEvent extends CausalityEvent {
    public final AnnotatedElement element;

    ReflectionObjectEvent(AnnotatedElement element) {
        this.element = element;
    }

    protected abstract String getSuffix();

    @Override
    public String toString() {
        return reflectionObjectToString(element) + getSuffix();
    }

    @Override
    public String toString(AnalysisMetaAccess metaAccess) {
        return reflectionObjectToGraalLikeString(metaAccess, element) + getSuffix();
    }

    private static String reflectionObjectToString(AnnotatedElement reflectionObject) {
        if (reflectionObject instanceof Class<?> clazz) {
            return clazz.getTypeName();
        } else if (reflectionObject instanceof Constructor<?> c) {
            return c.getDeclaringClass().getTypeName() + ".<init>(" + Arrays.stream(c.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", ")) + ')';
        } else if (reflectionObject instanceof Method m) {
            return m.getDeclaringClass().getTypeName() + '.' + m.getName() + '(' + Arrays.stream(m.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", ")) + ')';
        } else {
            Field f = ((Field) reflectionObject);
            return f.getDeclaringClass().getTypeName() + '.' + f.getName();
        }
    }

    private static String reflectionObjectToGraalLikeString(AnalysisMetaAccess metaAccess, AnnotatedElement reflectionObject) {
        if (reflectionObject instanceof Class<?> c) {
            return metaAccess.lookupJavaType(c).toJavaName();
        } else if (reflectionObject instanceof Executable e) {
            return metaAccess.lookupJavaMethod(e).format("%H.%n(%P):%R");
        } else {
            return metaAccess.lookupJavaField((Field) reflectionObject).format("%H.%n");
        }
    }
}
