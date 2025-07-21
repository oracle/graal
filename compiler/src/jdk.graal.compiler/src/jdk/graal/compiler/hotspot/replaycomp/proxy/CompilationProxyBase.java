/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.proxy;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

//JaCoCo Exclude

/**
 * A base class for compilation proxies providing implementations of shared methods.
 */
public class CompilationProxyBase implements CompilationProxy {
    public static final SymbolicMethod unproxifyMethod = new SymbolicMethod(CompilationProxy.class, "unproxify");
    public static final InvokableMethod unproxifyInvokable = (receiver, args) -> ((CompilationProxy) receiver).unproxify();

    public static final SymbolicMethod hashCodeMethod = new SymbolicMethod(Object.class, "hashCode");
    public static final InvokableMethod hashCodeInvokable = (receiver, args) -> receiver.hashCode();

    public static final SymbolicMethod equalsMethod = new SymbolicMethod(Object.class, "equals", Object.class);
    public static final InvokableMethod equalsInvokable = (receiver, args) -> receiver.equals(args[0]);

    public static final SymbolicMethod toStringMethod = new SymbolicMethod(Object.class, "toString");
    public static final InvokableMethod toStringInvokable = (receiver, args) -> receiver.toString();

    protected final InvocationHandler handler;

    CompilationProxyBase(InvocationHandler handler) {
        this.handler = handler;
    }

    protected final Object handle(SymbolicMethod method, InvokableMethod invokable, Object... args) {
        return CompilationProxy.handle(handler, this, method, invokable, args);
    }

    @Override
    public final Object unproxify() {
        return handle(unproxifyMethod, unproxifyInvokable);
    }

    @Override
    public final int hashCode() {
        return (int) handle(hashCodeMethod, hashCodeInvokable);
    }

    @Override
    public final boolean equals(Object obj) {
        return (boolean) handle(equalsMethod, equalsInvokable, obj);
    }

    @Override
    public final String toString() {
        return (String) handle(toStringMethod, toStringInvokable);
    }

    /**
     * Base class to share method implementations for proxies that implement
     * {@link AnnotatedElement}.
     */
    public abstract static class CompilationProxyAnnotatedBase extends CompilationProxyBase implements AnnotatedElement {
        CompilationProxyAnnotatedBase(InvocationHandler handler) {
            super(handler);
        }

        public static final SymbolicMethod getAnnotationMethod = new SymbolicMethod(AnnotatedElement.class, "getAnnotation", Class.class);
        @SuppressWarnings("unchecked") public static final InvokableMethod getAnnotationInvokable = (receiver, args) -> ((AnnotatedElement) receiver).getAnnotation((Class<Annotation>) args[0]);

        @Override
        @SuppressWarnings("unchecked")
        public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return (T) handle(getAnnotationMethod, getAnnotationInvokable, annotationClass);
        }

        public static final SymbolicMethod getAnnotationsMethod = new SymbolicMethod(AnnotatedElement.class, "getAnnotations");
        public static final InvokableMethod getAnnotationsInvokable = (receiver, args) -> ((AnnotatedElement) receiver).getAnnotations();

        @Override
        public final Annotation[] getAnnotations() {
            return (Annotation[]) handle(getAnnotationsMethod, getAnnotationsInvokable);
        }

        public static final SymbolicMethod getDeclaredAnnotationsMethod = new SymbolicMethod(AnnotatedElement.class, "getDeclaredAnnotations");
        public static final InvokableMethod getDeclaredAnnotationsInvokable = (receiver, args) -> ((AnnotatedElement) receiver).getDeclaredAnnotations();

        @Override
        public final Annotation[] getDeclaredAnnotations() {
            return (Annotation[]) handle(getDeclaredAnnotationsMethod, getDeclaredAnnotationsInvokable);
        }
    }
}
