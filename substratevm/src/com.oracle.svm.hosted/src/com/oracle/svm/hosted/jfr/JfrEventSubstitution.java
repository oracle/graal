/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jfr;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.svm.core.jfr.JfrJavaEvents;
import com.oracle.svm.core.jfr.JfrJdkCompatibility;
import com.oracle.svm.core.util.ObservableImageHeapMapProvider;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.SecuritySupport;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * This class triggers the class redefinition (see {@link JVM#retransformClasses}) for all event
 * classes that are visited during static analysis.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class JfrEventSubstitution extends SubstitutionProcessor {

    private final ResolvedJavaType baseEventType;
    private final ConcurrentHashMap<ResolvedJavaType, Boolean> typeSubstitution;
    private final ConcurrentHashMap<ResolvedJavaMethod, ResolvedJavaMethod> methodSubstitutions;
    private final ConcurrentHashMap<ResolvedJavaField, ResolvedJavaField> fieldSubstitutions;
    private final Map<String, Class<? extends jdk.jfr.Event>> mirrorEventMapping;

    private static final Method registerMirror = JavaVersionUtil.JAVA_SPEC < 22 ? ReflectionUtil.lookupMethod(SecuritySupport.class, "registerMirror", Class.class) : null;

    JfrEventSubstitution(MetaAccessProvider metaAccess) {
        baseEventType = metaAccess.lookupJavaType(jdk.internal.event.Event.class);
        typeSubstitution = new ConcurrentHashMap<>();
        methodSubstitutions = new ConcurrentHashMap<>();
        fieldSubstitutions = new ConcurrentHashMap<>();
        if (JavaVersionUtil.JAVA_SPEC < 22) {
            mirrorEventMapping = createMirrorEventsMapping();
        } else {
            mirrorEventMapping = null;
        }
    }

    @Override
    public ResolvedJavaField lookup(ResolvedJavaField field) {
        ResolvedJavaType type = field.getDeclaringClass();
        if (needsClassRedefinition(type)) {
            typeSubstitution.computeIfAbsent(type, this::initEventClass);
            return fieldSubstitutions.computeIfAbsent(field, JfrEventSubstitution::initEventField);
        }
        return field;
    }

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        ResolvedJavaType type = method.getDeclaringClass();
        if (needsClassRedefinition(type)) {
            typeSubstitution.computeIfAbsent(type, this::initEventClass);
            return methodSubstitutions.computeIfAbsent(method, JfrEventSubstitution::initEventMethod);
        }
        return method;
    }

    @Override
    public ResolvedJavaType lookup(ResolvedJavaType type) {
        if (needsClassRedefinition(type)) {
            typeSubstitution.computeIfAbsent(type, this::initEventClass);
        }
        return type;
    }

    private static ResolvedJavaField initEventField(ResolvedJavaField oldField) throws RuntimeException {
        ResolvedJavaType type = oldField.getDeclaringClass();
        if (oldField.isStatic()) {
            for (ResolvedJavaField field : type.getStaticFields()) {
                if (field.getName().equals(oldField.getName())) {
                    return field;
                }
            }
        } else {
            for (ResolvedJavaField field : type.getInstanceFields(false)) {
                if (field.getName().equals(oldField.getName())) {
                    return field;
                }
            }
        }
        throw VMError.shouldNotReachHere("Could not re-resolve field: " + oldField);
    }

    private static ResolvedJavaMethod initEventMethod(ResolvedJavaMethod oldMethod) throws RuntimeException {
        ResolvedJavaType type = oldMethod.getDeclaringClass();
        String name = oldMethod.getName();
        Signature signature = oldMethod.getSignature();

        if (name.equals("<clinit>")) {
            return type.getClassInitializer();
        } else if (name.equals("<init>")) {
            for (ResolvedJavaMethod m : type.getDeclaredConstructors(false)) {
                if (m.getName().equals(name) && m.getSignature().equals(signature)) {
                    return m;
                }
            }
        }

        ResolvedJavaMethod newMethod = type.findMethod(name, signature);
        if (newMethod != null) {
            return newMethod;
        }

        throw VMError.shouldNotReachHere("Could not re-resolve method: " + oldMethod);
    }

    private Boolean initEventClass(ResolvedJavaType eventType) throws RuntimeException {
        try {
            Class<? extends jdk.internal.event.Event> newEventClass = OriginalClassProvider.getJavaClass(eventType).asSubclass(jdk.internal.event.Event.class);
            eventType.initialize();

            if (JavaVersionUtil.JAVA_SPEC < 22) {
                /*
                 * It is crucial that mirror events are registered before the actual events.
                 * Starting with JDK 22, this is no longer necessary because
                 * MetadataRepository.register(...) handles that directly, see code that uses
                 * MirrorEvents.find(...).
                 */
                Class<? extends jdk.jfr.Event> mirrorEventClass = mirrorEventMapping.get(newEventClass.getName());
                if (mirrorEventClass != null) {
                    registerMirror.invoke(null, mirrorEventClass);
                }
            }

            SecuritySupport.registerEvent(newEventClass);

            JfrJavaEvents.registerEventClass(newEventClass);
            // the reflection registration for the event handler field is delayed to the JfrFeature
            // duringAnalysis callback so it does not race/interfere with other retransforms
            JfrJdkCompatibility.retransformClasses(new Class<?>[]{newEventClass});
            return Boolean.TRUE;
        } catch (Throwable ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private boolean needsClassRedefinition(ResolvedJavaType type) {
        return !type.isAbstract() && baseEventType.isAssignableFrom(type) && !baseEventType.equals(type);
    }

    /*
     * Mirror events contain the JFR-specific annotations. The mirrored event does not have any
     * dependency on JFR-specific classes. If the mirrored event is used, we must ensure that the
     * mirror event is registered as well. Otherwise, incorrect JFR metadata would be emitted.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Class<? extends jdk.jfr.Event>> createMirrorEventsMapping() {
        Map<String, Class<? extends jdk.jfr.Event>> result = ObservableImageHeapMapProvider.create();
        Class<? extends Annotation> mirrorEventAnnotationClass = (Class<? extends Annotation>) ReflectionUtil.lookupClass(false, "jdk.jfr.internal.MirrorEvent");
        Class<?> jdkEventsClass = ReflectionUtil.lookupClass(false, "jdk.jfr.internal.instrument.JDKEvents");
        Class<?>[] mirrorEventClasses = ReflectionUtil.readStaticField(jdkEventsClass, "mirrorEventClasses");
        for (int i = 0; i < mirrorEventClasses.length; i++) {
            Class<? extends jdk.jfr.Event> mirrorEventClass = (Class<? extends jdk.jfr.Event>) mirrorEventClasses[i];
            Annotation mirrorEvent = AnnotationAccess.getAnnotation(mirrorEventClass, mirrorEventAnnotationClass);
            Method m = ReflectionUtil.lookupMethod(mirrorEventAnnotationClass, "className");
            try {
                String className = (String) m.invoke(mirrorEvent);
                result.put(className, mirrorEventClass);
            } catch (Exception e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
        return result;
    }
}
