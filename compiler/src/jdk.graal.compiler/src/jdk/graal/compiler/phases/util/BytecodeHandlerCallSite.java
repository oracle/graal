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
package jdk.graal.compiler.phases.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig.ArgumentInfo;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Represents one call from an interpreter method to a bytecode handler. The callsite owns
 * the handler metadata resolved in the context of the enclosing interpreter method and uses it to
 * translate between the Java call shape and the generated stub ABI.
 */
public final class BytecodeHandlerCallSite {

    private final ResolvedJavaMethod enclosingMethod;
    private final int bci;
    private final ResolvedJavaMethod targetMethod;
    private final BytecodeHandlerConfig handlerConfig;

    public BytecodeHandlerCallSite(ResolvedJavaMethod enclosingMethod, int bci, ResolvedJavaMethod targetMethod) {
        this(enclosingMethod, bci, targetMethod, false);
    }

    /**
     * Creates metadata for a handler call site.
     *
     * @param enclosingMethod the interpreter method containing the handler invocation
     * @param bci bytecode index of the invocation in {@code enclosingMethod}
     * @param targetMethod the invoked bytecode handler method
     * @param templateModeEnabled whether {@code templateVariable} fields are modeled as template
     *            state instead of ordinary stub ABI arguments
     */
    public BytecodeHandlerCallSite(ResolvedJavaMethod enclosingMethod, int bci, ResolvedJavaMethod targetMethod,
                    boolean templateModeEnabled) {
        this.enclosingMethod = enclosingMethod;
        this.bci = bci;

        GraalError.guarantee(BytecodeInterpreterAnnotations.getBytecodeInterpreterHandler(targetMethod) != null,
                        "Target method %s is not annotated by @BytecodeInterpreterHandler", targetMethod.format("%H.%n(%p)"));
        this.targetMethod = targetMethod;

        this.handlerConfig = BytecodeHandlerConfig.getHandlerConfig(enclosingMethod, targetMethod, templateModeEnabled);
    }

    public List<ResolvedJavaType> getStubAbiArgumentTypes() {
        return handlerConfig.getStubAbiArgumentTypes();
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

    public List<ArgumentInfo> getStubAbiArgumentInfos() {
        return handlerConfig.getStubAbiArgumentInfos();
    }

    public String getStubName() {
        return BytecodeHandlerStubHelper.getStubName(targetMethod);
    }

    /**
     * Constructs the stub ABI argument list at the caller. Expanded arguments are lowered to field
     * loads from their Java owner objects; non-expanded arguments are forwarded unchanged.
     */
    public ValueNode[] createCallerArguments(ValueNode[] oldArguments, FixedNode insertBefore, Function<ResolvedJavaField, ResolvedJavaField> fieldMap) {
        List<ArgumentInfo> stubAbiArgumentInfos = handlerConfig.getStubAbiArgumentInfos();
        List<ValueNode> newArguments = new ArrayList<>();
        StructuredGraph graph = insertBefore.graph();
        for (ArgumentInfo argumentInfo : stubAbiArgumentInfos) {
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
                FixedGuardNode nullCheck = new FixedGuardNode(isNull, DeoptimizationReason.NullCheckException,
                                DeoptimizationAction.InvalidateReprofile, true);
                graph.addBeforeFixed(insertBefore, graph.add(nullCheck));
            }
        }
        return newArguments.toArray(ValueNode.EMPTY_ARRAY);
    }

    /**
     * Writes mutable expanded argument values returned by the stub's multi-return payload back into
     * their Java owner objects on the normal return path.
     */
    public void updateCallerReturns(FixedNode newInvoke, ValueNode[] oldArguments, FixedNode insertBefore,
                    Function<ResolvedJavaField, ResolvedJavaField> fieldMap) {
        StructuredGraph graph = insertBefore.graph();
        List<ArgumentInfo> stubAbiArgumentInfos = handlerConfig.getStubAbiArgumentInfos();

        for (ArgumentInfo argumentInfo : stubAbiArgumentInfos) {
            if (argumentInfo.isExpanded() && !argumentInfo.isImmutable()) {
                ReadArgumentNode fetchReturn = graph.unique(new ReadArgumentNode(newInvoke,
                                argumentInfo.type().getJavaKind(), argumentInfo.index()));
                ValueNode owner = oldArguments[argumentInfo.originalIndex()];
                StoreFieldNode writeback = new StoreFieldNode(owner, fieldMap.apply(argumentInfo.field()), fetchReturn);
                graph.addBeforeFixed(insertBefore, graph.add(writeback));
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BytecodeHandlerCallSite other) {
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
