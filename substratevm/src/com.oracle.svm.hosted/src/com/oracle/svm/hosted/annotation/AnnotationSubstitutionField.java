/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.annotation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;

import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AnnotationSubstitutionField extends CustomSubstitutionField {

    private final ResolvedJavaMethod accessorMethod;
    private final Map<JavaConstant, JavaConstant> valueCache;
    private final SnippetReflectionProvider snippetReflection;

    public AnnotationSubstitutionField(AnnotationSubstitutionType declaringClass, ResolvedJavaMethod accessorMethod, SnippetReflectionProvider snippetReflection) {
        super(declaringClass);
        this.accessorMethod = accessorMethod;
        this.snippetReflection = snippetReflection;
        this.valueCache = Collections.synchronizedMap(new HashMap<JavaConstant, JavaConstant>());
    }

    @Override
    public String getName() {
        return accessorMethod.getName();
    }

    @Override
    public JavaType getType() {
        return accessorMethod.getSignature().getReturnType(accessorMethod.getDeclaringClass());
    }

    @Override
    public JavaConstant readValue(JavaConstant receiver) {
        JavaConstant result = valueCache.get(receiver);
        if (result == null) {
            try {
                /*
                 * Invoke the accessor method of the annotation object. Since array attributes
                 * return a different, newly allocated, array at every invocation, we cache the
                 * result value.
                 */
                Proxy proxy = snippetReflection.asObject(Proxy.class, receiver);
                Method reflectionMethod = proxy.getClass().getDeclaredMethod(accessorMethod.getName());
                reflectionMethod.setAccessible(true);
                result = snippetReflection.forBoxed(getJavaKind(), reflectionMethod.invoke(proxy));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException ex) {
                throw VMError.shouldNotReachHere(ex);
            }

            valueCache.put(receiver, result);
        }
        return result;
    }

    @Override
    public boolean allowConstantFolding() {
        return true;
    }

    @Override
    public boolean injectFinalForRuntimeCompilation() {
        /*
         * Value of annotations never change at run time, so we can treat the field as final for
         * runtime compilations.
         */
        return true;
    }

    @Override
    public String toString() {
        return "AnnotationField<" + format("%h.%n") + ">";
    }
}
