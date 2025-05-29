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
package com.oracle.svm.hosted.webimage.codegen;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.nodes.ThrowBytecodeExceptionNode;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.webimage.functionintrinsics.ImplicitExceptions;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;

/**
 * Tune the reachability analysis to mark support methods in {@link ImplicitExceptions} for
 * {@link BytecodeExceptionNode} and {@link ThrowBytecodeExceptionNode} reachable.
 */
@AutomaticallyRegisteredFeature
public final class WebImageImplicitExceptionsFeature implements InternalFeature {
    private final HashMap<String, Method> methodsMap = createMethodsMap(ImplicitExceptions.class);

    /**
     * Used to stop the feature early when all methods of the class `ImplicitExceptions` are
     * reachable.
     * <p>
     * The set is shared across all iterations of an analysis session and reset in `beforeAnalysis`.
     */
    private final HashSet<Method> foundMethods = new HashSet<>();

    /**
     * Used to avoid visiting a method twice across analysis iterations of the same session.
     * <p>
     * The set is shared across all iterations of an analysis session and reset in `beforeAnalysis`.
     */
    private final HashSet<AnalysisMethod> visitedMethods = new HashSet<>();

    /**
     * Return a map from method names to methods defined in `clazz`.
     *
     * <ul>
     * <li>It assumes there are no overloaded methods.</li>
     * <li>It does not look into methods defined in superclasses.</li>
     * </ul>
     */
    private static HashMap<String, Method> createMethodsMap(Class<?> clazz) {
        HashMap<String, Method> map = new HashMap<>();
        for (Method m : clazz.getDeclaredMethods()) {
            map.put(m.getName(), m);
        }
        return map;
    }

    private boolean isAllSupportMethodsReachable() {
        return foundMethods.size() == methodsMap.size();
    }

    /**
     * Determines method name of a method in {@link ImplicitExceptions} that should be used to
     * handle the given bytecode exception kind.
     */
    public static String getSupportMethodName(BytecodeExceptionNode.BytecodeExceptionKind exceptionKind) {
        return switch (exceptionKind) {
            case ARRAY_STORE -> "createNewArrayStoreExceptionWithArgs";
            case NEGATIVE_ARRAY_SIZE -> "createNegativeArraySizeException";
            case CLASS_CAST -> "createNewClassCastExceptionWithArgs";
            case DIVISION_BY_ZERO -> "createNewDivisionByZeroException";
            case INTEGER_EXACT_OVERFLOW, LONG_EXACT_OVERFLOW -> "createNewArithmeticException";
            case NULL_POINTER -> "createNewNullPointerException";
            case OUT_OF_BOUNDS -> "createNewOutOfBoundsExceptionWithArgs";
            case INTRINSIC_OUT_OF_BOUNDS -> "createNewOutOfBoundsException";
            case INCOMPATIBLE_CLASS_CHANGE -> "createNewIncompatibleClassChangeError";
            case ILLEGAL_ARGUMENT_EXCEPTION_NEGATIVE_LENGTH -> "createNewNegativeLengthException";
            case ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY -> "createNewArgumentIsNotArrayException";
            case ASSERTION_ERROR_OBJECT -> "createNewAssertionErrorObject";
            case ASSERTION_ERROR_NULLARY -> "createNewAssertionErrorNullary";

            default -> throw GraalError.shouldNotReachHereUnexpectedValue(exceptionKind);
        };
    }

    private Method resolveSupportMethod(BytecodeExceptionNode.BytecodeExceptionKind exceptionKind) {
        return methodsMap.get(getSupportMethodName(exceptionKind));

    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        foundMethods.clear();
        visitedMethods.clear();

        Method[] methods = new Method[]{
                        methodsMap.get("checkNullPointer"),
                        methodsMap.get("checkArrayBound")
        };

        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        AnalysisMetaAccess meta = accessImpl.getMetaAccess();

        for (Method meth : methods) {
            AnalysisMethod aMethod = meta.lookupJavaMethod(meth);
            accessImpl.registerAsRoot(aMethod, true, "Web image runtime check outlining support, registered in " + WebImageImplicitExceptionsFeature.class);
        }
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        if (isAllSupportMethodsReachable()) {
            return;
        }

        FeatureImpl.DuringAnalysisAccessImpl accessImpl = (FeatureImpl.DuringAnalysisAccessImpl) access;
        BigBang bigbang = accessImpl.getBigBang();
        AnalysisUniverse universe = bigbang.getUniverse();

        for (AnalysisMethod method : universe.getMethods()) {
            if (!method.isReachable() || visitedMethods.contains(method)) {
                continue;
            }

            visitedMethods.add(method);
            StructuredGraph graph = method.decodeAnalyzedGraph(DebugContext.forCurrentThread(), null);

            if (graph == null) {
                // native methods may not have body
                continue;
            }

            for (BytecodeExceptionNode node : graph.getNodes().filter(BytecodeExceptionNode.class)) {
                registerBytecodeException(accessImpl, node.getExceptionKind(), node.getArguments());

                if (isAllSupportMethodsReachable()) {
                    // stop early if all methods are registered
                    return;
                }
            }
        }
    }

    private void registerBytecodeException(FeatureImpl.DuringAnalysisAccessImpl access, BytecodeExceptionNode.BytecodeExceptionKind exceptionKind, List<ValueNode> arguments) {
        Method supportMethod = resolveSupportMethod(exceptionKind);

        if (arguments.size() != supportMethod.getParameterCount()) {
            throw new GraalError("Unexpected number of arguments for %s. Expected %d, got %d", exceptionKind, supportMethod.getParameterCount(), arguments.size());
        }

        AnalysisMethod aMethod = access.getMetaAccess().lookupJavaMethod(supportMethod);

        boolean newMethod = foundMethods.add(supportMethod);
        if (newMethod && !aMethod.isInvoked()) {
            access.registerAsRoot(aMethod, false, "Web image implicit exception support, registered in " + WebImageImplicitExceptionsFeature.class);
            access.requireAnalysisIteration();
        }
    }
}
