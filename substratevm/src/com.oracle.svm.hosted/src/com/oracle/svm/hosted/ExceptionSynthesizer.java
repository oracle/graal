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

public class ExceptionSynthesizer {

    public static final Method throwClassNotFoundExceptionMethod;
    public static final Method throwNoSuchFieldExceptionMethod;
    public static final Method throwNoSuchMethodExceptionMethod;
    public static final Method throwNoClassDefFoundErrorMethod;
    public static final Method throwNoSuchFieldErrorMethod;
    public static final Method throwNoSuchMethodErrorMethod;
    public static final Method throwVerifyErrorMethod;

    static {
        try {
            throwClassNotFoundExceptionMethod = ImplicitExceptions.class.getDeclaredMethod("throwClassNotFoundException", String.class);
            throwNoSuchFieldExceptionMethod = ImplicitExceptions.class.getDeclaredMethod("throwNoSuchFieldException", String.class);
            throwNoSuchMethodExceptionMethod = ImplicitExceptions.class.getDeclaredMethod("throwNoSuchMethodException", String.class);
            throwNoClassDefFoundErrorMethod = ImplicitExceptions.class.getDeclaredMethod("throwNoClassDefFoundError", String.class);
            throwNoSuchFieldErrorMethod = ImplicitExceptions.class.getDeclaredMethod("throwNoSuchFieldError", String.class);
            throwNoSuchMethodErrorMethod = ImplicitExceptions.class.getDeclaredMethod("throwNoSuchMethodError", String.class);
            throwVerifyErrorMethod = ImplicitExceptions.class.getDeclaredMethod("throwVerifyError");
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    public static void throwClassNotFoundException(GraphBuilderContext b, String targetClass) {
        throwException(b, targetClass, throwClassNotFoundExceptionMethod);
    }

    public static void throwNoSuchFieldException(GraphBuilderContext b, String targetField) {
        throwException(b, targetField, throwNoSuchFieldExceptionMethod);
    }

    public static void throwNoSuchMethodException(GraphBuilderContext b, String targetMethod) {
        throwException(b, targetMethod, throwNoSuchMethodExceptionMethod);
    }

    public static void throwNoClassDefFoundError(GraphBuilderContext b, String targetField) {
        throwException(b, targetField, throwNoClassDefFoundErrorMethod);
    }

    public static void throwNoSuchFieldError(GraphBuilderContext b, String targetField) {
        throwException(b, targetField, throwNoSuchFieldErrorMethod);
    }

    public static void throwNoSuchMethodError(GraphBuilderContext b, String targetMethod) {
        throwException(b, targetMethod, throwNoSuchMethodErrorMethod);
    }

    public static void throwException(GraphBuilderContext b, String message, Method reportExceptionMethod) {
        ValueNode messageNode = ConstantNode.forConstant(SubstrateObjectConstant.forObject(message), b.getMetaAccess(), b.getGraph());
        ResolvedJavaMethod exceptionMethod = b.getMetaAccess().lookupJavaMethod(reportExceptionMethod);
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
}
