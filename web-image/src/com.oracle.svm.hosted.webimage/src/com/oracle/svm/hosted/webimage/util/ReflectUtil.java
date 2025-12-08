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

package com.oracle.svm.hosted.webimage.util;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicSet;
import org.graalvm.webimage.api.JS;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.AnnotationUtil;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Various reflection utilities that the normal Java reflection API does not expose.
 */
public class ReflectUtil {
    private static final ConcurrentHashMap<ResolvedJavaType, Object> SAM_CACHE = new ConcurrentHashMap<>();
    private static UnmodifiableEconomicSet<String> objectMethodDescriptors;

    private static synchronized UnmodifiableEconomicSet<String> getObjectMethodDescriptors(MetaAccessProvider metaAccess) {
        if (objectMethodDescriptors == null) {
            EconomicSet<String> descriptors = EconomicSet.create();
            for (ResolvedJavaMethod declaredMethod : metaAccess.lookupJavaType(Object.class).getDeclaredMethods(false)) {
                if (declaredMethod.isPublic()) {
                    descriptors.add(fullDescriptor(declaredMethod));
                }
            }
            objectMethodDescriptors = descriptors;
        }

        return objectMethodDescriptors;
    }

    private static String fullDescriptor(ResolvedJavaMethod method) {
        return method.getName() + method.getSignature().toMethodDescriptor();
    }

    private static boolean isJavaLangObjectMethod(MetaAccessProvider metaAccess, ResolvedJavaMethod method) {
        return getObjectMethodDescriptors(metaAccess).contains(fullDescriptor(method));
    }

    /// Checks if the interface type is a [FunctionalInterface], and returns its only single
    /// abstract method if it is.
    /// This follows
    /// [JLS-9.8](https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.8) to
    /// determine which method is the single abstract method.
    public static Optional<ResolvedJavaMethod> singleAbstractMethodForInterface(MetaAccessProvider metaAccess, ResolvedJavaType javaInterface) {
        GraalError.guarantee(javaInterface.isInterface(), "Got non-interface type %s", javaInterface);
        if (!AnnotationUtil.isAnnotationPresent(javaInterface, FunctionalInterface.class)) {
            return Optional.empty();
        }

        ResolvedJavaMethod singleMethod = null;

        for (final ResolvedJavaMethod method : javaInterface.getDeclaredMethods(false)) {
            /*
             * Only abstract methods that do not match a public method in java.lang.Object can be
             * single abstract methods (e.g. a functional interface could declare an abstract `int
             * hashCode()`, which should be ignored when determining the single abstract method)
             */
            if (method.isAbstract() && !isJavaLangObjectMethod(metaAccess, method)) {
                GraalError.guarantee(singleMethod == null,
                                "Incorrect single abstract method logic for interface %s. Found at least two candidate methods: %s and %s",
                                javaInterface,
                                singleMethod,
                                method);
                singleMethod = method;
            }

        }

        return Optional.ofNullable(singleMethod);
    }

    /**
     * Optionally returns a method of the class if that method implements a unique "single abstract
     * method" (SAM) of some functional interface. Even if it implements multiple functional
     * interfaces, if the SAMs of those interfaces are compatible, a unique SAM can be determined.
     *
     * @param classType Must be a non-abstract instance class, otherwise returns an empty optional.
     */
    @SuppressWarnings("unchecked")
    public static <M extends ResolvedJavaMethod> Optional<M> singleAbstractMethodForClass(MetaAccessProvider metaAccess, ResolvedJavaType classType) {
        if (!classType.isInstanceClass() || classType.isAbstract()) {
            /*
             * Only concrete classes that can have an allocated instance need to know about their
             * single abstract methods. For all other types we can avoid the computation (even
             * though it could technically yield a result).
             *
             * Array and primitive types are also included here since they cannot implement a SAM.
             */
            return Optional.empty();
        }

        return (Optional<M>) SAM_CACHE.computeIfAbsent(classType, t -> Optional.ofNullable((M) findSingleAbstractMethodForClass(metaAccess, t)));
    }

    private static ResolvedJavaMethod findSingleAbstractMethodForClass(MetaAccessProvider metaAccess, ResolvedJavaType classType) {
        assert classType.isLinked() : classType + " was not linked";
        LinkedHashSet<ResolvedJavaType> interfaces = new LinkedHashSet<>();
        findAllInterfaces(classType, interfaces);

        /*
         * Multiple different interface methods may resolve to the same concrete implementation. We
         * use a set to deduplicate these.
         */
        EconomicSet<ResolvedJavaMethod> candidates = EconomicSet.create();
        for (ResolvedJavaType i : interfaces) {
            Optional<ResolvedJavaMethod> candidate = singleAbstractMethodForInterface(metaAccess, i);
            if (candidate.isPresent()) {
                ResolvedJavaMethod method = candidate.orElseThrow();
                ResolvedJavaMethod concreteCandidate = classType.resolveConcreteMethod(method, method.getDeclaringClass());
                if (concreteCandidate != null) {
                    candidates.add(concreteCandidate);

                    if (candidates.size() > 1) {
                        // Break out early if there is now more than one candidate
                        return null;
                    }
                } else {
                    /*
                     * Resolution may produce null for various reasons. One of them being that the
                     * hosted method is not implementation invoked and thus doesn't exist in the
                     * hosted universe.
                     *
                     * The method that failed resolution could be incompatible with other candidates
                     * or not, we can't tell. This decision is unfortunately based on the analysis
                     * outcome.
                     *
                     * If the resolution fails (for whatever reason) we treat it as if this class
                     * does not have a unique SAM implementation.
                     */
                    candidates.clear();
                    return null;
                }
            } else {
                // This interface is not functional.
            }
        }

        if (candidates.size() == 1) {
            return candidates.iterator().next();
        } else {
            // There are none or multiple candidates, so no unique SAM implementation.
            return null;
        }
    }

    private static void findAllInterfaces(ResolvedJavaType type, LinkedHashSet<ResolvedJavaType> interfaces) {
        if (interfaces.contains(type)) {
            return;
        }

        if (type.isInterface()) {
            interfaces.add(type);
        }

        for (ResolvedJavaType superInterface : type.getInterfaces()) {
            findAllInterfaces(superInterface, interfaces);
        }

        if (type.getSuperclass() != null) {
            findAllInterfaces(type.getSuperclass(), interfaces);
        }
    }

    /**
     * Finds all methods annotated with {@link JS} as well as all the methods it overrides.
     */
    public static Set<AnalysisMethod> findBaseMethodsOfJSAnnotated(AnalysisMetaAccess metaAccess, ImageClassLoader imageClassLoader) {
        Set<AnalysisMethod> methods = new HashSet<>();

        List<Method> annotatedMethods = imageClassLoader.findAnnotatedMethods(JS.class);

        for (Method annotatedMethod : annotatedMethods) {
            AnalysisMethod aMethod = metaAccess.lookupJavaMethod(annotatedMethod);
            findBaseMethods(aMethod, aMethod.getDeclaringClass(), methods);
        }

        return methods;
    }

    /**
     * Finds all methods the given {@code originalMethod} overrides (including itself) from the
     * given class upwards and adds them to the given set.
     */
    private static void findBaseMethods(AnalysisMethod originalMethod, ResolvedJavaType type, Set<AnalysisMethod> jsOverridenMethodSet) {
        if (type == null) {
            return;
        }

        // This is either the same method as originalMethod or a method it overrides.
        AnalysisMethod baseMethod = (AnalysisMethod) JVMCIReflectionUtil.getUniqueDeclaredMethod(true, type, originalMethod.getName(),
                        originalMethod.toParameterList().toArray(AnalysisType.EMPTY_ARRAY));

        if (baseMethod != null) {
            jsOverridenMethodSet.add(baseMethod);
        }

        for (ResolvedJavaType clazzInterface : type.getInterfaces()) {
            findBaseMethods(originalMethod, clazzInterface, jsOverridenMethodSet);
        }

        findBaseMethods(originalMethod, type.getSuperclass(), jsOverridenMethodSet);
    }

}
