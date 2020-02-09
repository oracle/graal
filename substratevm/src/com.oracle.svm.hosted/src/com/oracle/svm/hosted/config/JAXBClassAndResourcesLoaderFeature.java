/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.config;

//Checkstyle: allow reflection
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticFeature
public class JAXBClassAndResourcesLoaderFeature extends JNIRegistrationUtil implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return access.findClassByName("javax.xml.bind.JAXBContext") != null;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(JAXBClassAndResourcesLoaderFeature::registerJavaxXmlConfigs,
                        method(access, "javax.xml.bind.JAXBContext", "newInstance", Class[].class));
    }

    private static void registerJavaxXmlConfigs(DuringAnalysisAccess access) {
        registerReflectionClasses(access);
        registerProxies(access);
        registerAnnotatedXMLRootClasses(access);
        access.requireAnalysisIteration();
    }

    private static void registerReflectionClasses(DuringAnalysisAccess access) {

        RuntimeReflection.register(clazz(access, "com.sun.xml.internal.bind.v2.ContextFactory"));
        RuntimeReflection.register(method(access, "com.sun.xml.internal.bind.v2.ContextFactory", "createContext", Class[].class, Map.class));

        registerClassAndMethodsForReflection(clazz(access, "javax.xml.bind.annotation.XmlElement"));
        registerClassAndMethodsForReflection(clazz(access, "javax.xml.bind.annotation.XmlType"));
        registerClassAndMethodsForReflection(clazz(access, "javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter"));
    }

    private static void registerProxies(DuringAnalysisAccess access) {
        ImageSingletons.lookup(DynamicProxyRegistry.class).addProxyClass(
                        clazz(access, "javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter"),
                        clazz(access, "com.sun.xml.internal.bind.v2.model.annotation.Locatable"));
    }

    public static void registerAnnotatedXMLRootClasses(DuringAnalysisAccess access) {
        List<AnalysisType> types = ((FeatureImpl.DuringAnalysisAccessImpl) access).getUniverse().getTypes();

        for (AnalysisType type : types) {
            Class<?> javaClass = type.getJavaClass();

            Annotation[] annotations = javaClass.getAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().getName().equals("javax.xml.bind.annotation.XmlRootElement")) {
                    registerClassMethodsAndFieldsForReflection(javaClass);
                    analyzeClassMethodsAndFields(javaClass);
                }
                break;
            }
        }
    }

    private static void analyzeClassMethodsAndFields(Class<?> javaClass) {
        analyzeIfUsingXmlAdapter(javaClass);
    }

    private static void analyzeIfUsingXmlAdapter(Class<?> javaClass) {
        for (Field field : javaClass.getDeclaredFields()) {
            registerXmlAdapterClassIfFound(field.getAnnotations());
        }

        for (Method method : javaClass.getDeclaredMethods()) {
            registerXmlAdapterClassIfFound(method.getAnnotations());
        }
    }

    private static void registerXmlAdapterClassIfFound(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getName().equals("javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter")) {
                Class<?> xmlAdapterClass = getXmlAdapterClassFromAnnotation(annotation);
                registerConstructorAndMethodsForReflection(xmlAdapterClass);
            }
        }
    }

    private static Class<?> getXmlAdapterClassFromAnnotation(Annotation annotation) {
        try {
            Method getValueMethod = annotation.annotationType().getMethod("value");
            return (Class<?>) getValueMethod.invoke(annotation);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere();
        }
    }

    private static void registerClassAndMethodsForReflection(Class<?> javaClass) {
        RuntimeReflection.register(javaClass);
        RuntimeReflection.register(javaClass.getDeclaredMethods());
    }

    private static void registerConstructorAndMethodsForReflection(Class<?> javaClass) {
        registerClassAndMethodsForReflection(javaClass);
        RuntimeReflection.register(ReflectionUtil.lookupConstructor(javaClass));
    }

    private static void registerClassMethodsAndFieldsForReflection(Class<?> javaClass) {
        registerConstructorAndMethodsForReflection(javaClass);
        RuntimeReflection.register(javaClass.getDeclaredFields());
    }
}
