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
package com.oracle.svm.hosted.phases;

import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.c.enums.CEnumRuntimeData;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.cenum.CEnumCallWrapperMethod;
import com.oracle.svm.hosted.code.CCallStubMethod;
import com.oracle.svm.hosted.code.CEntryPointCallStubMethod;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaKind;

/**
 * Implements some shared parts for converting Java enums (see {@link CEnum}) to primitive values
 * and vice versa. This class is used in the following scenarios:
 *
 * <ul>
 * <li>A {@link CEnum} is used in the signature of a {@link CEntryPoint}, see
 * {@link CEntryPointCallStubMethod}.</li>
 * <li>A {@link CEnum} is used in the signature of a C function call, see
 * {@link CCallStubMethod}.</li>
 * <li>A method is called that is annotated with {@link CEnumValue} or {@link CEnumLookup}, see
 * {@link CEnumCallWrapperMethod}.</li>
 *
 * {@link CEnumValue} and {@link CEnumLookup} methods may use arbitrary primitive Java data types
 * that are completely unrelated to the actual type of the C enum. However, note that the specified
 * Java types only have an effect if those methods are called explicitly. When implicitly converting
 * between C values and Java enums (e.g., for C call arguments and return types), we do not use the
 * annotated methods. So, for implicit conversion, only the type of the C enum is relevant and the
 * semantics are similar to reading/writing a C value.
 */
public class CInterfaceEnumTool {
    interface CallTargetFactory {
        MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, AnalysisMethod targetMethod, ValueNode[] args, StampPair returnStamp, int bci);

        static CallTargetFactory from(BytecodeParser p) {
            return (invokeKind, targetMethod, args, returnStamp, bci) -> p.createMethodCallTarget(invokeKind, targetMethod, args, returnStamp, null);
        }

        static CallTargetFactory from(HostedGraphKit kit) {
            return kit::createMethodCallTarget;
        }
    }

    private final AnalysisMethod enumToBooleanMethod;
    private final AnalysisMethod enumToByteMethod;
    private final AnalysisMethod enumToShortMethod;
    private final AnalysisMethod enumToCharMethod;
    private final AnalysisMethod enumToIntMethod;
    private final AnalysisMethod enumToLongMethod;
    private final AnalysisMethod enumToUnsignedWordMethod;
    private final AnalysisMethod enumToSignedWordMethod;

    private final AnalysisMethod longToEnumMethod;

    CInterfaceEnumTool(AnalysisMetaAccess metaAccess) {
        try {
            enumToBooleanMethod = metaAccess.lookupJavaMethod(CEnumRuntimeData.class.getDeclaredMethod("enumToBoolean", Enum.class));
            enumToByteMethod = metaAccess.lookupJavaMethod(CEnumRuntimeData.class.getDeclaredMethod("enumToByte", Enum.class));
            enumToShortMethod = metaAccess.lookupJavaMethod(CEnumRuntimeData.class.getDeclaredMethod("enumToShort", Enum.class));
            enumToCharMethod = metaAccess.lookupJavaMethod(CEnumRuntimeData.class.getDeclaredMethod("enumToChar", Enum.class));
            enumToIntMethod = metaAccess.lookupJavaMethod(CEnumRuntimeData.class.getDeclaredMethod("enumToInt", Enum.class));
            enumToLongMethod = metaAccess.lookupJavaMethod(CEnumRuntimeData.class.getDeclaredMethod("enumToLong", Enum.class));
            enumToSignedWordMethod = metaAccess.lookupJavaMethod(CEnumRuntimeData.class.getDeclaredMethod("enumToSignedWord", Enum.class));
            enumToUnsignedWordMethod = metaAccess.lookupJavaMethod(CEnumRuntimeData.class.getDeclaredMethod("enumToUnsignedWord", Enum.class));

            longToEnumMethod = metaAccess.lookupJavaMethod(CEnumRuntimeData.class.getDeclaredMethod("longToEnum", long.class));
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    public static CInterfaceEnumTool singleton() {
        return ImageSingletons.lookup(CInterfaceEnumTool.class);
    }

    public ValueNode createInvokeEnumToValue(HostedGraphKit kit, EnumInfo enumInfo, AnalysisType returnType, ValueNode arg) {
        int invokeBci = kit.bci();
        MethodCallTargetNode callTarget = createInvokeEnumToValue(kit, CallTargetFactory.from(kit), invokeBci, enumInfo, returnType, arg);
        return kit.createInvokeWithExceptionAndUnwind(callTarget, kit.getFrameState(), invokeBci);
    }

    public InvokeWithExceptionNode startInvokeWithExceptionEnumToValue(HostedGraphKit kit, EnumInfo enumInfo, AnalysisType returnType, ValueNode arg) {
        int invokeBci = kit.bci();
        MethodCallTargetNode callTarget = createInvokeEnumToValue(kit, CallTargetFactory.from(kit), invokeBci, enumInfo, returnType, arg);
        return kit.startInvokeWithException(callTarget, kit.getFrameState(), invokeBci);
    }

    private MethodCallTargetNode createInvokeEnumToValue(GraphBuilderTool b, CallTargetFactory callTargetFactory, int bci, EnumInfo enumInfo, AnalysisType returnType, ValueNode arg) {
        AnalysisMethod method = getEnumToValueMethod(b, returnType);
        assert !Modifier.isStatic(method.getModifiers()) && method.getSignature().getParameterCount(true) == 2;

        ValueNode[] args = new ValueNode[2];
        args[0] = ConstantNode.forConstant(b.getSnippetReflection().forObject(enumInfo.getRuntimeData()), b.getMetaAccess(), b.getGraph());
        args[1] = arg;

        WordTypes wordTypes = b.getWordTypes();
        StampPair returnStamp = wordTypes.isWord(returnType) ? StampPair.createSingle(wordTypes.getWordStamp(returnType)) : StampFactory.forDeclaredType(null, returnType, false);
        return b.append(callTargetFactory.createMethodCallTarget(InvokeKind.Special, method, args, returnStamp, bci));
    }

    private AnalysisMethod getEnumToValueMethod(GraphBuilderTool b, AnalysisType returnType) {
        if (b.getWordTypes().isWord(returnType)) {
            assert !b.getMetaAccess().lookupJavaType(PointerBase.class).isAssignableFrom(returnType) : "only integer types are allowed";

            if (b.getMetaAccess().lookupJavaType(UnsignedWord.class).isAssignableFrom(returnType)) {
                return enumToUnsignedWordMethod;
            } else if (b.getMetaAccess().lookupJavaType(SignedWord.class).isAssignableFrom(returnType)) {
                return enumToSignedWordMethod;
            } else {
                throw VMError.shouldNotReachHere("Unexpected return type: " + returnType);
            }
        }

        JavaKind returnKind = returnType.getJavaKind();
        return switch (returnKind) {
            case Boolean -> enumToBooleanMethod;
            case Byte -> enumToByteMethod;
            case Short -> enumToShortMethod;
            case Char -> enumToCharMethod;
            case Int -> enumToIntMethod;
            case Long -> enumToLongMethod;
            default -> throw VMError.shouldNotReachHere("Unexpected return kind: " + returnKind);
        };
    }

    public ValueNode createInvokeLookupEnum(HostedGraphKit kit, AnalysisType enumType, EnumInfo enumInfo, ValueNode arg, boolean allowInlining) {
        // Invoke the conversion function (from long to enum)
        int invokeBci = kit.bci();
        MethodCallTargetNode callTarget = createInvokeLongToEnum(kit, CallTargetFactory.from(kit), invokeBci, enumInfo, arg);
        InvokeWithExceptionNode invoke = kit.createInvokeWithExceptionAndUnwind(callTarget, kit.getFrameState(), invokeBci);
        if (!allowInlining) {
            invoke.setUseForInlining(false);
        }

        // Create the instanceof guard to narrow the return type for the analysis
        LogicNode instanceOfNode = kit.append(InstanceOfNode.createAllowNull(TypeReference.createExactTrusted(enumType), invoke, null, null));
        ConstantNode enumClass = kit.createConstant(kit.getConstantReflection().asJavaClass(enumType), JavaKind.Object);
        GuardingNode guard = kit.createCheckThrowingBytecodeException(instanceOfNode, false, BytecodeExceptionNode.BytecodeExceptionKind.CLASS_CAST, invoke, enumClass);

        // Create the PiNode anchored at the guard to narrow the return type for compilation
        ObjectStamp resultStamp = StampFactory.object(TypeReference.create(null, enumType), false);
        return kit.unique(new PiNode(invoke, resultStamp, guard.asNode()));
    }

    private MethodCallTargetNode createInvokeLongToEnum(GraphBuilderTool b, CallTargetFactory callTargetFactory, int bci, EnumInfo enumInfo, ValueNode arg) {
        assert !Modifier.isStatic(longToEnumMethod.getModifiers()) && longToEnumMethod.getSignature().getParameterCount(false) == 1;

        StructuredGraph graph = b.getGraph();
        ValueNode[] args = new ValueNode[2];
        args[0] = ConstantNode.forConstant(b.getSnippetReflection().forObject(enumInfo.getRuntimeData()), b.getMetaAccess(), graph);
        /* The exact size and signedness only matters at a later point. */
        args[1] = graph.unique(new ZeroExtendNode(arg, 64));

        AnalysisType enumReturnType = longToEnumMethod.getSignature().getReturnType();
        StampPair returnStamp = StampFactory.forDeclaredType(null, enumReturnType, false);
        return b.append(callTargetFactory.createMethodCallTarget(InvokeKind.Special, longToEnumMethod, args, returnStamp, bci));
    }

    public static boolean isPrimitiveOrWord(AnalysisType type) {
        return type.getStorageKind().isPrimitive();
    }

    /**
     * For method signatures, only the size (but not the signedness) matters for {@link CEnum}
     * values. So, when we convert {@link CEnum}s in the signature to primitive types, we always use
     * signed primitive Java data types that have the same size as the enum data type in C.
     */
    public static AnalysisType getCEnumValueType(EnumInfo enumInfo, AnalysisMetaAccess metaAccess) {
        int sizeInBytes = enumInfo.getSizeInBytes();
        return switch (sizeInBytes) {
            case 1 -> metaAccess.lookupJavaType(byte.class);
            case 2 -> metaAccess.lookupJavaType(short.class);
            case 4 -> metaAccess.lookupJavaType(int.class);
            case 8 -> metaAccess.lookupJavaType(long.class);
            default -> throw VMError.shouldNotReachHere("CEnum has unexpected size: " + sizeInBytes);
        };
    }
}

@AutomaticallyRegisteredFeature
class CInterfaceEnumToolFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        ImageSingletons.add(CInterfaceEnumTool.class, new CInterfaceEnumTool(access.getMetaAccess()));
    }
}
