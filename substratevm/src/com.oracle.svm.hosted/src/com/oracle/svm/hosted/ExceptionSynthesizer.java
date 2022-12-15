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

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.ReflectionUtil;

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
        MethodCallTargetNode callTarget = b.add(new SubstrateMethodCallTargetNode(InvokeKind.Static, exceptionMethod, new ValueNode[]{messageNode}, returnStamp, null, null, null));
        b.add(new InvokeWithExceptionNode(callTarget, null, b.bci()));
        /* The invoked method always throws an exception, i.e., never returns. */
        b.add(new LoweredDeadEndNode());
    }

    /**
     * This method is used to delay errors from image build-time to run-time. It does so by invoking
     * a synthesized method that throws an instance like the one given as throwable in the given
     * GraphBuilderContext. If the given throwable has a non-null cause, a cause-instance of the
     * same type with a proper cause-message is created first that is then passed to the method that
     * creates and throws the outer throwable-instance.
     */
    public static <T extends Throwable> void replaceWithThrowingAtRuntime(GraphBuilderContext b, T throwable) {
        Throwable cause = throwable.getCause();
        if (cause != null) {
            var metaAccess = (UniverseMetaAccess) b.getMetaAccess();
            /* Invoke method that creates a cause-instance with cause-message */
            var causeCtor = ReflectionUtil.lookupConstructor(cause.getClass(), String.class);
            ResolvedJavaMethod causeCtorMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(causeCtor), false);
            ValueNode causeMessageNode = ConstantNode.forConstant(b.getConstantReflection().forString(cause.getMessage()), metaAccess, b.getGraph());
            StampPair returnStamp = StampFactory.forDeclaredType(b.getGraph().getAssumptions(), causeCtorMethod.getSignature().getReturnType(null), false);
            MethodCallTargetNode callTarget = b.add(new SubstrateMethodCallTargetNode(InvokeKind.Static, causeCtorMethod, new ValueNode[]{causeMessageNode}, returnStamp, null, null, null));
            InvokeWithExceptionNode causeCtorInvoke = new InvokeWithExceptionNode(callTarget, null, b.bci());
            b.add(causeCtorInvoke);
            /* Invoke method that creates and throws throwable-instance with message and cause */
            var errorCtor = ReflectionUtil.lookupConstructor(throwable.getClass(), String.class, Throwable.class);
            ResolvedJavaMethod throwingMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(errorCtor), true);
            ValueNode messageNode = ConstantNode.forConstant(b.getConstantReflection().forString(throwable.getMessage()), metaAccess, b.getGraph());
            b.handleReplacedInvoke(InvokeKind.Static, throwingMethod, new ValueNode[]{messageNode, causeCtorInvoke.asNode()}, false);
            b.add(new LoweredDeadEndNode());
        } else {
            replaceWithThrowingAtRuntime(b, throwable.getClass(), throwable.getMessage());
        }
    }

    /**
     * This method is used to delay errors from image build-time to run-time. It does so by invoking
     * a synthesized method that creates an instance of type throwableClass with throwableMessage as
     * argument and then throws that instance in the given GraphBuilderContext.
     */
    public static void replaceWithThrowingAtRuntime(GraphBuilderContext b, Class<? extends Throwable> throwableClass, String throwableMessage) {
        /*
         * This method is currently not able to replace
         * ExceptionSynthesizer.throwException(GraphBuilderContext, Method, String) because there
         * are places where GraphBuilderContext.getMetaAccess() does not contain a
         * UniverseMetaAccess (e.g. in case of ParsingReason.EarlyClassInitializerAnalysis). If we
         * can access the ParsingReason in here we will be able to get rid of throwException.
         */
        var errorCtor = ReflectionUtil.lookupConstructor(throwableClass, String.class);
        var metaAccess = (UniverseMetaAccess) b.getMetaAccess();
        ResolvedJavaMethod throwingMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(errorCtor), true);
        ValueNode messageNode = ConstantNode.forConstant(b.getConstantReflection().forString(throwableMessage), b.getMetaAccess(), b.getGraph());
        b.handleReplacedInvoke(InvokeKind.Static, throwingMethod, new ValueNode[]{messageNode}, false);
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
