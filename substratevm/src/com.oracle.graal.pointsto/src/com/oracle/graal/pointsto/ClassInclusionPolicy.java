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
package com.oracle.graal.pointsto;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Policy used to determine which classes, methods and fields need to be included in the image when
 * the {@code IncludeAllFromPath} and/or {@code IncludeAllFromModule} options are specified
 * depending on the configuration.
 */
public abstract class ClassInclusionPolicy {
    protected BigBang bb;
    protected final Object reason;

    public ClassInclusionPolicy(Object reason) {
        this.reason = reason;
    }

    public void setBigBang(BigBang bb) {
        this.bb = bb;
    }

    /**
     * Determine if the given class needs to be included in the image according to the policy.
     */
    public abstract boolean isClassIncluded(Class<?> cls);

    /**
     * Determine if the given method needs to be included in the image according to the policy.
     */
    public boolean isMethodIncluded(Executable method) {
        /*
         * Methods annotated with @Fold should not be included in the base image as they must be
         * inlined. An extension image would inline the method as well and would not use the method
         * from the base image.
         */
        return !AnnotationAccess.isAnnotationPresent(bb.getMetaAccess().lookupJavaMethod(method), Fold.class);
    }

    /**
     * Determine if the given field needs to be included in the image according to the policy.
     */
    public boolean isFieldIncluded(Field field) {
        if (!bb.getHostVM().platformSupported(field)) {
            return false;
        }
        return bb.getHostVM().isFieldIncluded(bb, field);
    }

    /**
     * Includes the given class in the image.
     */
    public void includeClass(Class<?> cls) {
        /*
         * Those classes cannot be registered as allocated as they cannot be instantiated. They are
         * instead registered as reachable as they can still have methods or fields that could be
         * used by an extension image.
         */
        if (Modifier.isAbstract(cls.getModifiers()) || cls.isInterface() || cls.isPrimitive()) {
            bb.getMetaAccess().lookupJavaType(cls).registerAsReachable(reason);
        } else {
            bb.getMetaAccess().lookupJavaType(cls).registerAsAllocated(reason);
        }
    }

    /**
     * Includes the given method in the image.
     */
    public abstract void includeMethod(Executable method);

    /**
     * Includes the given field in the image.
     */
    public void includeField(Field field) {
        bb.postTask(debug -> bb.addRootField(field));
    }

    /**
     * The analysis for the base layer of a layered image assumes that any method that is reachable
     * using the base java access rules can be an entry point. An upper layer does not have access
     * to the packages from a lower layer. Thus, only the public classes with their public and
     * protected inner classes and methods can be accessed by an upper layer.
     * <p>
     * Protected elements from a final or sealed class cannot be accessed as an upper layer cannot
     * create a new class that extends the final or sealed class.
     * <p>
     * All the fields are included disregarding access rules as a missing field would cause issues
     * in the object layout.
     */
    public static class LayeredBaseImageInclusionPolicy extends ClassInclusionPolicy {
        public LayeredBaseImageInclusionPolicy(Object reason) {
            super(reason);
        }

        @Override
        public boolean isClassIncluded(Class<?> cls) {
            Class<?> enclosingClass = cls.getEnclosingClass();
            int classModifiers = cls.getModifiers();
            if (enclosingClass != null) {
                return isAccessible(enclosingClass, classModifiers) && isClassIncluded(enclosingClass);
            } else {
                return Modifier.isPublic(classModifiers);
            }
        }

        @Override
        public boolean isMethodIncluded(Executable method) {
            return !Modifier.isAbstract(method.getModifiers()) && isAccessible(method) && super.isMethodIncluded(method);
        }

        @Override
        public void includeMethod(Executable method) {
            bb.postTask(debug -> {
                /*
                 * Non-abstract methods from an abstract class or default methods from an interface
                 * are not registered as implementation invoked by the analysis because their
                 * declaring class cannot be marked as instantiated and AnalysisType.getTypeFlow
                 * only includes instantiated types (see TypeFlow.addObserver). For now, to ensure
                 * those methods are included in the image, they are manually registered as
                 * implementation invoked.
                 */
                Class<?> declaringClass = method.getDeclaringClass();
                if (!Modifier.isAbstract(method.getModifiers()) && (declaringClass.isInterface() || Modifier.isAbstract(declaringClass.getModifiers()))) {
                    AnalysisMethod analysisMethod = bb.getMetaAccess().lookupJavaMethod(method);
                    analysisMethod.registerAsDirectRootMethod(reason);
                    analysisMethod.registerAsImplementationInvoked(reason);
                }
                bb.forcedAddRootMethod(method, false, reason);
            });
        }
    }

    /**
     * The default inclusion policy. Includes all classes and methods. Including all fields causes
     * issues at the moment, so the same rules as for the {@link LayeredBaseImageInclusionPolicy}
     * are used.
     */
    public static class DefaultAllInclusionPolicy extends ClassInclusionPolicy {
        public DefaultAllInclusionPolicy(Object reason) {
            super(reason);
        }

        @Override
        public boolean isClassIncluded(Class<?> cls) {
            return true;
        }

        @Override
        public void includeMethod(Executable method) {
            bb.postTask(debug -> bb.addRootMethod(method, false, reason));
        }
    }

    protected boolean isAccessible(Member member) {
        Class<?> cls = member.getDeclaringClass();
        int modifiers = member.getModifiers();
        return isAccessible(cls, modifiers);
    }

    protected boolean isAccessible(Class<?> cls, int modifiers) {
        return Modifier.isPublic(modifiers) || (!Modifier.isFinal(cls.getModifiers()) && !cls.isSealed() && Modifier.isProtected(modifiers));
    }
}
