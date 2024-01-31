/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.c.BoxedRelocatedPointer;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.graal.code.SubstrateCompilationIdentifier;
import com.oracle.svm.core.graal.replacements.SubstrateGraphKit;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HostedGraphKit extends SubstrateGraphKit {

    public HostedGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method) {
        super(debug, method, providers, providers.getGraphBuilderPlugins(), new SubstrateCompilationIdentifier(),
                        SubstrateCompilationDirectives.isRuntimeCompiledMethod(method));
    }

    @Override
    public AnalysisMetaAccess getMetaAccess() {
        return (AnalysisMetaAccess) super.getMetaAccess();
    }

    public void emitEnsureInitializedCall(ResolvedJavaType type) {
        if (EnsureClassInitializedNode.needsRuntimeInitialization(graph.method().getDeclaringClass(), type)) {
            ValueNode hub = createConstant(getConstantReflection().asJavaClass(type), JavaKind.Object);
            appendWithUnwind(new EnsureClassInitializedNode(hub));
        }
    }

    /**
     * Appends the provided node to the control flow graph. The exception edge is connected to an
     * {@link UnwindNode}, i.e., the exception is not handled in this method.
     */
    public <T extends WithExceptionNode> T appendWithUnwind(T withExceptionNode) {
        return appendWithUnwind(withExceptionNode, bci());
    }

    public LoadFieldNode createLoadFieldNode(ConstantNode receiver, Class<BoxedRelocatedPointer> clazz, String fieldName) {
        try {
            var field = getMetaAccess().lookupJavaField(clazz.getDeclaredField(fieldName));
            return LoadFieldNode.createOverrideStamp(StampPair.createSingle(wordStamp(field.getType())), receiver, field);
        } catch (NoSuchFieldException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    public GuardingNode createCheckThrowingBytecodeException(LogicNode condition, boolean failOnTrue, BytecodeExceptionNode.BytecodeExceptionKind exceptionKind, ValueNode... arguments) {
        BranchProbabilityData trueProbability = failOnTrue ? BranchProbabilityNode.SLOW_PATH_PROFILE : BranchProbabilityNode.FAST_PATH_PROFILE;
        IfNode ifNode = startIf(condition, trueProbability);
        if (failOnTrue) {
            thenPart();
        } else {
            elsePart();
        }
        BytecodeExceptionNode exception = createBytecodeExceptionObjectNode(exceptionKind, true, arguments);
        append(new UnwindNode(exception));
        AbstractMergeNode merge = endIf();
        assert merge == null;
        return failOnTrue ? ifNode.falseSuccessor() : ifNode.trueSuccessor();
    }

    public BytecodeExceptionNode createBytecodeExceptionObjectNode(BytecodeExceptionNode.BytecodeExceptionKind exceptionKind, boolean rethrow, ValueNode... arguments) {
        BytecodeExceptionNode exception = append(new BytecodeExceptionNode(getMetaAccess(), exceptionKind, arguments));
        setStateAfterException(getFrameState(), bci(), exception, rethrow);
        return exception;
    }

    public ValueNode maybeCreateExplicitNullCheck(ValueNode object) {
        assert object.stamp(NodeView.DEFAULT).isPointerStamp();
        if (StampTool.isPointerNonNull(object)) {
            return object;
        }
        createCheckThrowingBytecodeException(IsNullNode.create(object), true, BytecodeExceptionNode.BytecodeExceptionKind.NULL_POINTER);
        return append(PiNode.create(object, StampFactory.objectNonNull()));
    }

    /**
     * Lift a node representing an array into a list of nodes representing the values in that array.
     * 
     * @param array The array to lift
     * @param elementKinds The kinds of the elements in the array
     * @param length The length of the array
     */
    public List<ValueNode> loadArrayElements(ValueNode array, JavaKind[] elementKinds, int length) {
        assert elementKinds.length == length;

        List<ValueNode> result = new ArrayList<>();
        for (int i = 0; i < length; ++i) {
            ValueNode load = createLoadIndexed(array, i, elementKinds[i], null);
            append(load);
            result.add(load);
        }
        return result;
    }

    public List<ValueNode> loadArrayElements(ValueNode array, JavaKind elementKind, int length) {
        JavaKind[] elementKinds = new JavaKind[length];
        Arrays.fill(elementKinds, elementKind);
        return loadArrayElements(array, elementKinds, length);
    }
}
