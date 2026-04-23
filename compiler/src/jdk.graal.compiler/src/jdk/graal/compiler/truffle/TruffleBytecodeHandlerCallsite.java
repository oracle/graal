/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ReadArgumentNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.truffle.BytecodeHandlerConfig.ArgumentInfo;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Represents a call site for a Truffle bytecode handler, describing the target method, its
 * arguments, return type, and context within the enclosing method. This class is responsible for
 * managing argument transformations for Truffle interpreter handler methods. It also provides logic
 * to generate stubs for Truffle bytecode handlers, determine calling/register conventions, and
 * manage return/argument transformations between the caller and callee.
 * <p>
 * Construction and use of this class is based on detection of specific annotations (such as
 * {@code BytecodeInterpreterSwitch} and {@code BytecodeInterpreterHandler}) on methods and types.
 */
public final class TruffleBytecodeHandlerCallsite {

    /**
     * Holds the annotation types used by Truffle interpreters.
     */
    public record TruffleBytecodeHandlerTypes(ResolvedJavaType typeBytecodeInterpreterSwitch,
                    ResolvedJavaType typeBytecodeInterpreterHandlerConfig,
                    ResolvedJavaType typeBytecodeInterpreterHandler,
                    ResolvedJavaType typeBytecodeInterpreterFetchOpcode) {
    }

    private final ResolvedJavaMethod enclosingMethod;
    private final int bci;
    private final ResolvedJavaMethod targetMethod;
    private final BytecodeHandlerConfig handlerConfig;

    public TruffleBytecodeHandlerCallsite(ResolvedJavaMethod enclosingMethod, int bci, ResolvedJavaMethod targetMethod, TruffleBytecodeHandlerTypes truffleTypes) {
        GraalError.guarantee(AnnotationValueSupport.isAnnotationPresent(truffleTypes.typeBytecodeInterpreterSwitch, enclosingMethod),
                        "Enclosing method %s is not annotated by @BytecodeInterpreterSwitch", enclosingMethod.format("%H.%n(%p)"));
        this.enclosingMethod = enclosingMethod;
        this.bci = bci;

        GraalError.guarantee(AnnotationValueSupport.isAnnotationPresent(truffleTypes.typeBytecodeInterpreterHandler, targetMethod),
                        "Target method %s is not annotated by @BytecodeInterpreterHandler", targetMethod.format("%H.%n(%p)"));
        this.targetMethod = targetMethod;

        this.handlerConfig = BytecodeHandlerConfig.getHandlerConfig(enclosingMethod, targetMethod, truffleTypes);
    }

    public List<ResolvedJavaType> getArgumentTypes() {
        return handlerConfig.getArgumentTypes();
    }

    public ResolvedJavaType getReturnType() {
        return handlerConfig.getReturnType();
    }

    public ResolvedJavaMethod getEnclosingMethod() {
        return enclosingMethod;
    }

    public int getBci() {
        return bci;
    }

    public ResolvedJavaMethod getTargetMethod() {
        return targetMethod;
    }

    public BytecodeHandlerConfig getHandlerConfig() {
        return handlerConfig;
    }

    public List<ArgumentInfo> getArgumentInfos() {
        return handlerConfig.getArgumentInfos();
    }

    public String getStubName() {
        return TruffleBytecodeHandlerStubHelper.getStubName(targetMethod);
    }

    /**
     * Constructs the expanded list of caller argument {@link ValueNode}s for invoking a Truffle
     * bytecode handler stub.
     */
    public ValueNode[] createCallerArguments(ValueNode[] oldArguments, FixedNode insertBefore, Function<ResolvedJavaField, ResolvedJavaField> fieldMap) {
        List<ArgumentInfo> argumentInfos = handlerConfig.getArgumentInfos();
        List<ValueNode> newArguments = new ArrayList<>();
        StructuredGraph graph = insertBefore.graph();
        for (ArgumentInfo argumentInfo : argumentInfos) {
            if (argumentInfo.isExpanded()) {
                ValueNode owner = oldArguments[argumentInfo.originalIndex()];
                LoadFieldNode load = LoadFieldNode.create(graph.getAssumptions(), owner, fieldMap.apply(argumentInfo.field()));
                graph.addBeforeFixed(insertBefore, graph.add(load));
                newArguments.add(load);
            } else {
                newArguments.add(oldArguments[argumentInfo.originalIndex()]);
            }
            if (argumentInfo.nonNull() && !argumentInfo.type().isPrimitive()) {
                LogicNode isNull = graph.addOrUnique(IsNullNode.create(newArguments.getLast()));
                graph.addBeforeFixed(insertBefore, graph.add(new FixedGuardNode(isNull, DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true)));
            }
        }
        return newArguments.toArray(ValueNode.EMPTY_ARRAY);
    }

    /**
     * Updates mutable expanded arguments in the caller frame with new return values produced by the
     * Truffle handler stub.
     */
    public void updateCallerReturns(FixedNode newInvoke, ValueNode[] oldArguments, FixedNode insertBefore, Function<ResolvedJavaField, ResolvedJavaField> fieldMap) {
        StructuredGraph graph = insertBefore.graph();
        List<ArgumentInfo> argumentInfos = handlerConfig.getArgumentInfos();

        for (ArgumentInfo argumentInfo : argumentInfos) {
            if (argumentInfo.isExpanded() && !argumentInfo.isImmutable()) {
                ReadArgumentNode fetchReturn = graph.unique(new ReadArgumentNode(newInvoke, argumentInfo.type().getJavaKind(), argumentInfo.index()));
                ValueNode owner = oldArguments[argumentInfo.originalIndex()];
                StoreFieldNode writeback = new StoreFieldNode(owner, fieldMap.apply(argumentInfo.field()), fetchReturn);
                graph.addBeforeFixed(insertBefore, graph.add(writeback));
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TruffleBytecodeHandlerCallsite other) {
            return enclosingMethod.equals(other.enclosingMethod) && bci == other.bci && targetMethod.equals(other.targetMethod) &&
                            handlerConfig.equals(other.handlerConfig);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enclosingMethod, bci, targetMethod, handlerConfig);
    }
}
