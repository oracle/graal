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
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

public class AnnotationSubstitutionField extends CustomSubstitutionField {

    private final ResolvedJavaMethod accessorMethod;
    private final Map<JavaConstant, JavaConstant> valueCache;
    private final SnippetReflectionProvider snippetReflection;
    private final MetaAccessProvider metaAccess;

    public AnnotationSubstitutionField(AnnotationSubstitutionType declaringClass, ResolvedJavaMethod accessorMethod,
                    SnippetReflectionProvider snippetReflection,
                    MetaAccessProvider metaAccess) {
        super(declaringClass);
        this.accessorMethod = accessorMethod;
        this.snippetReflection = snippetReflection;
        this.valueCache = Collections.synchronizedMap(new HashMap<>());
        this.metaAccess = metaAccess;
    }

    @Override
    public String getName() {
        return accessorMethod.getName();
    }

    @Override
    public JavaType getType() {
        /*
         * The type of an annotation element can be one of: primitive, String, Class, an enum type,
         * an annotation type, or an array type whose component type is one of the preceding types,
         * according to https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.6.1.
         */
        JavaType actualType = accessorMethod.getSignature().getReturnType(accessorMethod.getDeclaringClass());
        if (AnnotationSupport.isClassType(actualType, metaAccess)) {
            /*
             * Annotation elements that have a Class type can reference classes that are missing at
             * runtime. We declare the corresponding fields with the Object type to be able to store
             * a TypeNotPresentExceptionProxy which we then use to generate the
             * TypeNotPresentException at runtime (see bellow).
             */
            return metaAccess.lookupJavaType(Object.class);
        }
        return actualType;
    }

    @Override
    public JavaConstant readValue(JavaConstant receiver) {
        JavaConstant result = valueCache.get(receiver);
        if (result == null) {
            Object annotationFieldValue;
            /*
             * Invoke the accessor method of the annotation object. Since array attributes return a
             * different, newly allocated, array at every invocation, we cache the result value.
             */
            try {
                /*
                 * The code bellow assumes that the annotations have already been parsed and the
                 * result cached in the AnnotationInvocationHandler.memberValues field. The parsing
                 * is triggered, at the least, during object graph checking in
                 * Inflation.checkType(), or earlier when the type annotations are accessed for the
                 * first time, e.g., ImageClassLoader.includedInPlatform() due to the call to
                 * Class.getAnnotation(Platforms.class).
                 */
                Proxy proxy = snippetReflection.asObject(Proxy.class, receiver);

                /*
                 * Reflect on the proxy interface, i.e., the annotation class, instead of the
                 * generated proxy class to avoid module access issues with dynamically generated
                 * modules. The dynamically generated module that the generated proxies belong to,
                 * i.e., `jdk.proxy1`, cannot be open to all-unnamed-modules like we do with other
                 * modules.
                 */
                Class<?> annotationInterface = AnnotationSupport.findAnnotationInterfaceTypeForMarkedAnnotationType(proxy.getClass());
                annotationFieldValue = ReflectionUtil.lookupMethod(annotationInterface, accessorMethod.getName()).invoke(proxy);
            } catch (IllegalAccessException | IllegalArgumentException ex) {
                throw VMError.shouldNotReachHere(ex);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof TypeNotPresentException) {
                    /*
                     * When an annotation has a Class<?> parameter but is referencing a missing
                     * class a TypeNotPresentException is thrown. The TypeNotPresentException is
                     * usually created when the annotation is first parsed, i.e., one some other
                     * parameter is queried, and cached as an TypeNotPresentExceptionProxy. We catch
                     * and repackage it here, then rely on the runtime mechanism to unpack and
                     * rethrow it.
                     */
                    TypeNotPresentException tnpe = (TypeNotPresentException) cause;
                    annotationFieldValue = new TypeNotPresentExceptionProxy(tnpe.typeName(), new NoClassDefFoundError(tnpe.typeName()));
                } else {
                    throw VMError.shouldNotReachHere(ex);
                }
            }

            result = snippetReflection.forBoxed(getJavaKind(), annotationFieldValue);

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
