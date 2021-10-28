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

import java.lang.reflect.GenericSignatureFormatError;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;

import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class ExceptionSynthesizer {

    /**
     * Cache exception throwing methods. The key is a Class[]: first element is the exception type,
     * the next elements are the parameter types.
     */
    private static final Map<Key, Method> exceptionMethods = new HashMap<>();

    static {
        // ReflectiveOperationException subclasses
        registerMethod(ClassNotFoundException.class, String.class);
        registerMethod(NoSuchFieldException.class, String.class);
        registerMethod(NoSuchMethodException.class, String.class);
        // LinkageError subclasses
        registerMethod(LinkageError.class, String.class);
        registerMethod(ClassCircularityError.class, String.class);
        registerMethod(IncompatibleClassChangeError.class, String.class);
        registerMethod(NoSuchFieldError.class, String.class);
        registerMethod(InstantiationError.class, String.class);
        registerMethod(NoSuchMethodError.class, String.class);
        registerMethod(IllegalAccessError.class, String.class);
        registerMethod(AbstractMethodError.class, String.class);
        registerMethod(BootstrapMethodError.class, String.class);
        registerMethod(ClassFormatError.class, String.class);
        registerMethod(GenericSignatureFormatError.class, String.class);
        registerMethod(UnsupportedClassVersionError.class, String.class);
        registerMethod(UnsatisfiedLinkError.class, String.class);
        registerMethod(NoClassDefFoundError.class, String.class);
        registerMethod(ExceptionInInitializerError.class, String.class);
        registerMethod(VerifyError.class, String.class);
        registerMethod(VerifyError.class);
    }

    private static void registerMethod(Class<?> exceptionClass) {
        try {
            exceptionMethods.put(Key.from(exceptionClass), ImplicitExceptions.class.getDeclaredMethod("throw" + ClassUtil.getUnqualifiedName(exceptionClass)));
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static void registerMethod(Class<?> exceptionClass, Class<?> parameterClass) {
        try {
            exceptionMethods.put(Key.from(exceptionClass, parameterClass), ImplicitExceptions.class.getDeclaredMethod("throw" + ClassUtil.getUnqualifiedName(exceptionClass), parameterClass));
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private ExceptionSynthesizer() {
    }

    public static Method throwExceptionMethod(Class<?>... methodDescriptor) {
        Method method = throwExceptionMethodOrNull(methodDescriptor);
        VMError.guarantee(method != null, "Exception synthesizer method " + Arrays.toString(methodDescriptor) + " not found.");
        return method;
    }

    public static Method throwExceptionMethodOrNull(Class<?>... methodDescriptor) {
        return exceptionMethods.get(Key.from(methodDescriptor));
    }

    public static void throwException(GraphBuilderContext b, Class<?> exceptionClass, String message) {
        /* Get the exception throwing method that has a message parameter. */
        throwException(b, throwExceptionMethod(exceptionClass, String.class), message);
    }

    public static void throwException(GraphBuilderContext b, Method throwExceptionMethod, String message) {
        ValueNode messageNode = ConstantNode.forConstant(b.getConstantReflection().forString(message), b.getMetaAccess(), b.getGraph());
        ResolvedJavaMethod exceptionMethod = b.getMetaAccess().lookupJavaMethod(throwExceptionMethod);
        assert exceptionMethod.isStatic();

        StampPair returnStamp = StampFactory.forDeclaredType(b.getGraph().getAssumptions(), exceptionMethod.getSignature().getReturnType(null), false);
        MethodCallTargetNode callTarget = b.add(new SubstrateMethodCallTargetNode(InvokeKind.Static, exceptionMethod, new ValueNode[]{messageNode}, returnStamp, null, null));
        b.add(new InvokeWithExceptionNode(callTarget, null, b.bci()));
        /* The invoked method always throws an exception, i.e., never returns. */
        b.add(new LoweredDeadEndNode());
    }

    /**
     * The key describes an exception throwing method via a Class[]: first element is the exception
     * type, the next elements are the parameter types.
     */
    static final class Key {
        static Key from(Class<?>... values) {
            return new Key(values);
        }

        private final Class<?>[] elements;

        private Key(Class<?>[] values) {
            elements = values;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key key = (Key) o;
            return Arrays.equals(elements, key.elements);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(elements);
        }
    }
}
