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
import com.oracle.svm.hosted.NativeImageClassLoader;

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
                Method reflectionMethod = proxy.getClass().getDeclaredMethod(accessorMethod.getName());
                reflectionMethod.setAccessible(true);
                annotationFieldValue = reflectionMethod.invoke(proxy);
            } catch (IllegalAccessException | IllegalArgumentException | NoSuchMethodException ex) {
                throw VMError.shouldNotReachHere(ex);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof TypeNotPresentException) {
                    /*
                     * Depending on the class loading order the ghost interface for the missing
                     * class may not have been created yet when the annotation signature was parsed.
                     * Thus a TypeNotPresentException was cached. We catch and repackage it here.
                     */
                    TypeNotPresentException tnpe = (TypeNotPresentException) cause;
                    annotationFieldValue = new TypeNotPresentExceptionProxy(tnpe.typeName(), new NoClassDefFoundError(tnpe.typeName()));
                } else {
                    throw VMError.shouldNotReachHere(ex);
                }
            }

            if (annotationFieldValue instanceof Class) {
                Class<?> classValue = (Class<?>) annotationFieldValue;
                if (NativeImageClassLoader.classIsMissing(classValue)) {
                    /*
                     * The annotation field references a missing type. This situation would normally
                     * produce a NoClassDefFoundError during annotation parsing, which would be
                     * caught and packed as a TypeNotPresentExceptionProxy, then cached in
                     * AnnotationInvocationHandler.memberValues. The original
                     * TypeNotPresentException would then be generated and thrown when the accessor
                     * method is invoked via AnnotationInvocationHandler.invoke(). However, the
                     * NativeImageClassLoader.loadClass() replaces missing classes with ghost
                     * interfaces, a marker for the missing types. We check for the presence of a
                     * ghost interface here and and create a TypeNotPresentExceptionProxy which we
                     * then use to generate the TypeNotPresentException at runtime.
                     */
                    annotationFieldValue = new TypeNotPresentExceptionProxy(classValue.getName(), new NoClassDefFoundError(classValue.getName()));
                }
            } else if (annotationFieldValue instanceof Class[]) {
                for (Class<?> classValue : (Class[]) annotationFieldValue) {
                    if (NativeImageClassLoader.classIsMissing(classValue)) {
                        /*
                         * If at least one type is missing in a Class[] return a
                         * TypeNotPresentExceptionProxy. In JDK8 this situation wrongfully results
                         * in an ArrayStoreException, however it was fixed in JDK11+ (and
                         * back-ported) to result in a TypeNotPresentException of the first missing
                         * class. See: https://bugs.openjdk.java.net/browse/JDK-7183985
                         */
                        annotationFieldValue = new TypeNotPresentExceptionProxy(classValue.getName(), new NoClassDefFoundError(classValue.getName()));
                        break;
                    }
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
