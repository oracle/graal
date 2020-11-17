/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.UnreachedCode;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;

import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class ExceptionSynthesizer {

    /**
     * Cache exception throwing methods. The key is a List<Class>: first element is the exception
     * type, the next elements are the parameter types.
     */
    private static final Map<Key<Class<?>>, Method> exceptionMethods = new HashMap<>();

    static {
        try {
            // ReflectiveOperationException subclasses
            exceptionMethods.put(Key.from(ClassNotFoundException.class, String.class), ImplicitExceptions.class.getDeclaredMethod("throwClassNotFoundException", String.class));
            exceptionMethods.put(Key.from(NoSuchFieldException.class, String.class), ImplicitExceptions.class.getDeclaredMethod("throwNoSuchFieldException", String.class));
            exceptionMethods.put(Key.from(NoSuchMethodException.class, String.class), ImplicitExceptions.class.getDeclaredMethod("throwNoSuchMethodException", String.class));
            // LinkageError subclasses
            exceptionMethods.put(Key.from(NoClassDefFoundError.class, String.class), ImplicitExceptions.class.getDeclaredMethod("throwNoClassDefFoundError", String.class));
            exceptionMethods.put(Key.from(NoSuchFieldError.class, String.class), ImplicitExceptions.class.getDeclaredMethod("throwNoSuchFieldError", String.class));
            exceptionMethods.put(Key.from(NoSuchMethodError.class, String.class), ImplicitExceptions.class.getDeclaredMethod("throwNoSuchMethodError", String.class));
            exceptionMethods.put(Key.from(VerifyError.class, String.class), ImplicitExceptions.class.getDeclaredMethod("throwVerifyError", String.class));
            exceptionMethods.put(Key.from(VerifyError.class), ImplicitExceptions.class.getDeclaredMethod("throwVerifyError"));
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private ExceptionSynthesizer() {
    }

    public static Method throwExceptionMethod(Class<?>... methodDescriptor) {
        Method method = exceptionMethods.get(Key.from(methodDescriptor));
        VMError.guarantee(method != null, "Exception synthesizer method " + Arrays.asList(methodDescriptor) + " not found.");
        return method;
    }

    public static void throwException(GraphBuilderContext b, Class<?> exceptionClass, String message) {
        /* Get the exception throwing method that has a message parameter. */
        throwException(b, throwExceptionMethod(exceptionClass, String.class), message);
    }

    public static void throwException(GraphBuilderContext b, Method throwExceptionMethod, String message) {
        ValueNode messageNode = ConstantNode.forConstant(SubstrateObjectConstant.forObject(message), b.getMetaAccess(), b.getGraph());
        ResolvedJavaMethod exceptionMethod = b.getMetaAccess().lookupJavaMethod(throwExceptionMethod);
        assert exceptionMethod.isStatic();
        Invoke invoke = b.handleReplacedInvoke(InvokeKind.Static, exceptionMethod, new ValueNode[]{messageNode}, false);
        if (invoke != null) {
            /*
             * If there is an invoke node, i.e., the call was not inlined, append a deopt node to
             * stop parsing. This way we don't need to make sure that the stack is left in a
             * consistent state after the new invoke is introduced, e.g., like pushing a dummy value
             * for a replaced field load.
             *
             * If there is no invoke node then the error reporting method call was inlined. In that
             * case the deopt node is not required since the body of the error reporting method is
             * "throw ...".
             */
            b.add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
        }
    }

    static class Key<T> {
        @SafeVarargs
        static <V> Key<V> from(V... values) {
            return new Key<V>(values);
        }

        private final List<T> elements;

        private Key(T[] values) {
            elements = Arrays.asList(values);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key<?> key = (Key<?>) o;
            return elements.equals(key.elements);
        }

        @Override
        public int hashCode() {
            return elements.hashCode();
        }
    }
}
