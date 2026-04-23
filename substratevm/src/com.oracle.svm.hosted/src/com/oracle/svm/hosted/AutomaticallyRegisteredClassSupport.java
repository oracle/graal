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
package com.oracle.svm.hosted;

import com.oracle.svm.core.service.AutomaticallyRegisteredServiceRegistration;
import com.oracle.svm.util.GuestAccess;
import org.graalvm.nativeimage.AnnotationAccess;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Shared support for automatic-registration handlers that reconcile generated {@link ServiceLoader}
 * metadata with classes discovered through the image builder's {@link ImageClassLoader}.
 * <p>
 * Concrete subclasses define the generated service-entry subtype, the source annotation that should
 * still be present on registered classes, and the caller-specific diagnostics for mismatches
 * between generated metadata and scanned classes. The shared
 * {@link AutomaticallyRegisteredServiceRegistration#getClassName()} contract keeps the class-name
 * lookup in this common helper, so handlers only need to supply scheme-specific behavior.
 */
abstract class AutomaticallyRegisteredClassSupport<S extends AutomaticallyRegisteredServiceRegistration, A extends Annotation> {
    protected final ImageClassLoader loader;

    protected AutomaticallyRegisteredClassSupport(ImageClassLoader loader) {
        this.loader = loader;
    }

    /**
     * Returns the generated service-registration type that enumerates classes participating in this
     * automatic-registration scheme.
     */
    protected abstract Class<S> serviceRegistrationClass();

    /**
     * Returns the source-level annotation that marks classes which should appear in the generated
     * service metadata for this loader.
     */
    protected abstract Class<A> annotationClass();

    /**
     * Reports that generated service metadata names a class that cannot be loaded through the image
     * builder's {@link ImageClassLoader}. Typical causes are stale annotation-processor outputs, an
     * annotation processor that did not run, or a class path mismatch between compilation and image
     * building.
     */
    protected abstract Error missingClassError(Throwable cause, String className);

    /**
     * Reports that generated service metadata still points to a class even though the class no
     * longer carries the annotation that justified the automatic registration. This is the stale
     * generated-output case: the metadata survived a source change that removed the annotation.
     */
    protected abstract Error staleGeneratedRegistrationError(Class<?> registeredClass);

    /**
     * Reports that classpath scanning found a class still annotated for automatic registration, but
     * no corresponding generated service entry was loaded for it. This indicates that generated
     * registration metadata is missing or incomplete for the annotated class.
     */
    protected abstract Error missingGeneratedRegistrationError(Class<?> annotatedClass);

    final LinkedHashSet<Class<?>> loadRegisteredClasses() {
        LinkedHashSet<Class<?>> registeredClasses = new LinkedHashSet<>();
        ClassLoader serviceLoaderClassLoader = NativeImageSystemClassLoader.singleton().defaultSystemClassLoader;
        for (S serviceRegistration : ServiceLoader.load(serviceRegistrationClass(), serviceLoaderClassLoader)) {
            String className = serviceRegistration.getClassName();
            Class<?> registeredClass;
            try {
                registeredClass = loader.findClass(className).getOrFail();
            } catch (IllegalStateException ex) {
                throw missingClassError(ex.getCause(), className);
            }
            if (AnnotationAccess.getAnnotation(registeredClass, annotationClass()) == null) {
                throw staleGeneratedRegistrationError(registeredClass);
            }
            /*
             * For simplicity, we do not look at the Platforms annotation ourselves and instead
             * check if the ImageClassLoader found that class too.
             */
            if (loader.findSubclasses(registeredClass, true).contains(registeredClass)) {
                registeredClasses.add(registeredClass);
            }
        }
        return registeredClasses;
    }

    final void verifyGeneratedRegistrations(LinkedHashSet<Class<?>> registeredClasses) {
        for (Class<?> annotatedClass : loader.findAnnotatedClasses(annotationClass(), true)) {
            if (!registeredClasses.contains(annotatedClass)) {
                throw missingGeneratedRegistrationError(annotatedClass);
            }
        }
    }

    final List<Class<?>> findMostSpecificClasses(Class<?> baseClass, Iterable<Class<?>> candidateClasses) {
        ArrayList<Class<?>> candidates = new ArrayList<>();
        for (Class<?> candidateClass : candidateClasses) {
            if (isAssignableFrom(baseClass, candidateClass)) {
                candidates.add(candidateClass);
            }
        }
        candidates.removeIf(candidate -> hasMoreSpecificCandidate(candidate, candidates));
        return candidates;
    }

    private boolean hasMoreSpecificCandidate(Class<?> candidate, List<Class<?>> candidates) {
        for (Class<?> other : candidates) {
            if (candidate != other && isAssignableFrom(candidate, other)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("static-method")
    private boolean isAssignableFrom(Class<?> supertype, Class<?> subtype) {
        return GuestAccess.get().lookupType(supertype).isAssignableFrom(GuestAccess.get().lookupType(subtype));
    }
}
