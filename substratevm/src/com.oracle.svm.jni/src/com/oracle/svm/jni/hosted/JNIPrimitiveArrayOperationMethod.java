/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.hosted;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.nativeimage.c.function.CEntryPoint.FatalExceptionHandler;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Generated method for operations on an array with a primitive element type via JNI. An accessor is
 * specific to the {@link JavaKind element kind} of the array, and to an {@link Operation}.
 */
public final class JNIPrimitiveArrayOperationMethod extends JNIGeneratedMethod {

    public enum Operation {
        NEW,
        GET_ELEMENTS,
        RELEASE_ELEMENTS,
        GET_REGION,
        SET_REGION,
    }

    private final JavaKind elementKind;
    private final Operation operation;
    private final ResolvedJavaType declaringClass;
    private final ConstantPool constantPool;
    private final String name;
    private final Signature signature;

    public JNIPrimitiveArrayOperationMethod(JavaKind elementKind, Operation operation, ResolvedJavaType declaringClass, ConstantPool constantPool, MetaAccessProvider metaAccess) {
        if (!EnumSet.of(JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Short,
                        JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double).contains(elementKind)) {

            throw VMError.shouldNotReachHere();
        }
        this.elementKind = elementKind;
        this.operation = operation;
        this.declaringClass = declaringClass;
        this.constantPool = constantPool;
        this.name = createName();
        this.signature = createSignature(metaAccess);
    }

    private String createName() {
        StringBuilder sb = new StringBuilder(32);
        String kindName = elementKind.name();
        switch (operation) {
            case NEW:
                sb.append("New").append(kindName).append("Array");
                break;
            case GET_ELEMENTS:
                sb.append("Get").append(kindName).append("ArrayElements");
                break;
            case RELEASE_ELEMENTS:
                sb.append("Release").append(kindName).append("ArrayElements");
                break;
            case GET_REGION:
                sb.append("Get").append(kindName).append("ArrayRegion");
                break;
            case SET_REGION:
                sb.append("Set").append(kindName).append("ArrayRegion");
                break;
        }
        return sb.toString();
    }

    private SimpleSignature createSignature(MetaAccessProvider metaAccess) {
        ResolvedJavaType objectHandleType = metaAccess.lookupJavaType(JNIObjectHandle.class);
        ResolvedJavaType intType = metaAccess.lookupJavaType(int.class);
        ResolvedJavaType returnType;
        List<JavaType> args = new ArrayList<>();
        args.add(metaAccess.lookupJavaType(JNIEnvironment.class));
        if (operation == Operation.NEW) {
            args.add(intType); // jsize length;
            returnType = objectHandleType;
        } else {
            args.add(objectHandleType); // j<PrimitiveType>Array array;
            if (operation == Operation.GET_ELEMENTS) {
                args.add(metaAccess.lookupJavaType(CCharPointer.class)); // jboolean *isCopy;
                returnType = metaAccess.lookupJavaType(WordPointer.class);
            } else if (operation == Operation.RELEASE_ELEMENTS) {
                args.add(metaAccess.lookupJavaType(WordPointer.class)); // NativeType *elems;
                args.add(intType); // jint mode;
                returnType = metaAccess.lookupJavaType(Void.TYPE);
            } else if (operation == Operation.GET_REGION || operation == Operation.SET_REGION) {
                args.add(intType); // jsize start;
                args.add(intType); // jsize len;
                args.add(metaAccess.lookupJavaType(WordPointer.class)); // NativeType *buf;
                returnType = metaAccess.lookupJavaType(Void.TYPE);
            } else {
                throw VMError.shouldNotReachHere();
            }
        }
        return new SimpleSignature(args, returnType);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method);
        StructuredGraph graph = kit.getGraph();
        FrameStateBuilder state = new FrameStateBuilder(null, method, graph);
        state.initializeForMethodStart(null, true, providers.getGraphBuilderPlugins());

        ValueNode vmThread = kit.loadLocal(0, signature.getParameterKind(0));
        kit.append(CEntryPointEnterNode.enter(vmThread));

        List<ValueNode> arguments = kit.loadArguments(signature.toParameterTypes(null));

        ValueNode result = null;
        switch (operation) {
            case NEW:
                result = newArray(providers, kit, arguments);
                break;
            case GET_ELEMENTS: {
                ValueNode arrayHandle = arguments.get(1);
                ValueNode array = kit.unboxHandle(arrayHandle);
                ValueNode isCopy = arguments.get(2);
                result = kit.pinArrayAndGetAddress(array, isCopy);
                break;
            }
            case RELEASE_ELEMENTS: {
                ValueNode address = arguments.get(2);
                kit.unpinArrayByAddress(address);
                break;
            }
            case GET_REGION:
            case SET_REGION: {
                ValueNode arrayHandle = arguments.get(1);
                ValueNode array = kit.unboxHandle(arrayHandle);
                ValueNode start = arguments.get(2);
                ValueNode count = arguments.get(3);
                ValueNode buffer = arguments.get(4);
                FixedWithNextNode fwn;
                if (operation == Operation.GET_REGION) {
                    fwn = kit.getPrimitiveArrayRegionRetainException(elementKind, array, start, count, buffer);
                } else {
                    fwn = kit.setPrimitiveArrayRegionRetainException(elementKind, array, start, count, buffer);
                }
                if (fwn instanceof MergeNode) {
                    MergeNode merge = (MergeNode) fwn;
                    ((MergeNode) fwn).setStateAfter(state.create(kit.bci(), merge));
                }
                break;
            }
            default:
                throw VMError.shouldNotReachHere();
        }
        kit.appendStateSplitProxy(state);
        CEntryPointLeaveNode leave = new CEntryPointLeaveNode(LeaveAction.Leave);
        kit.append(leave);
        kit.createReturn(result, (result != null) ? result.getStackKind() : JavaKind.Void);

        return kit.finalizeGraph();
    }

    private ValueNode newArray(HostedProviders providers, JNIGraphKit kit, List<ValueNode> arguments) {
        ResolvedJavaType elementType = providers.getMetaAccess().lookupJavaType(elementKind.toJavaClass());
        ValueNode length = arguments.get(1);
        ConstantNode zero = kit.createInt(0);
        kit.startIf(new IntegerLessThanNode(length, zero), BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY);
        kit.thenPart();
        ValueNode nullHandle = kit.createConstant(JavaConstant.INT_0, providers.getWordTypes().getWordKind());
        kit.elsePart();
        ValueNode array = kit.append(new NewArrayNode(elementType, length, true));
        ValueNode arrayHandle = kit.boxObjectInLocalHandle(array);
        AbstractMergeNode merge = kit.endIf();
        merge.setStateAfter(kit.getFrameState().create(kit.bci(), merge));
        Stamp handleStamp = providers.getWordTypes().getWordStamp(providers.getMetaAccess().lookupJavaType(JNIObjectHandle.class));
        return kit.unique(new ValuePhiNode(handleStamp, merge, new ValueNode[]{nullHandle, arrayHandle}));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    public CEntryPointData createEntryPointData() {
        return CEntryPointData.create(this, CEntryPointData.DEFAULT_NAME, CEntryPointData.DEFAULT_NAME_TRANSFORMATION, "",
                        NoPrologue.class, NoEpilogue.class, FatalExceptionHandler.class, Publish.NotPublished);
    }
}
