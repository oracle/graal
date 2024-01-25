/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jni;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.nativeimage.c.function.CEntryPoint.FatalExceptionHandler;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.c.function.CEntryPointOptions.AutomaticPrologueBailout;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.EntryPointCallStubMethod;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Generated method for operations on an array with a primitive element type via JNI. An accessor is
 * specific to the {@link JavaKind element kind} of the array, and to an {@link Operation}.
 *
 * The generated method implements one of the following JNI Functions:
 *
 * <ul>
 * <li>{@code GetBooleanArrayElements}</li>
 * <li>{@code GetByteArrayElements}</li>
 * <li>{@code GetCharArrayElements}</li>
 * <li>{@code GetShortArrayElements}</li>
 * <li>{@code GetIntArrayElements}</li>
 * <li>{@code GetLongArrayElements}</li>
 * <li>{@code GetFloatArrayElements}</li>
 * <li>{@code GetDoubleArrayElements}</li>
 * <li>{@code ReleaseBooleanArrayElements}</li>
 * <li>{@code ReleaseByteArrayElements}</li>
 * <li>{@code ReleaseCharArrayElements}</li>
 * <li>{@code ReleaseShortArrayElements}</li>
 * <li>{@code ReleaseIntArrayElements}</li>
 * <li>{@code ReleaseLongArrayElements}</li>
 * <li>{@code ReleaseFloatArrayElements}</li>
 * <li>{@code ReleaseDoubleArrayElements}</li>
 * <li>{@code GetBooleanArrayRegion}</li>
 * <li>{@code GetByteArrayRegion}</li>
 * <li>{@code GetCharArrayRegion}</li>
 * <li>{@code GetShortArrayRegion}</li>
 * <li>{@code GetIntArrayRegion}</li>
 * <li>{@code GetLongArrayRegion}</li>
 * <li>{@code GetFloatArrayRegion}</li>
 * <li>{@code GetDoubleArrayRegion}</li>
 * <li>{@code SetBooleanArrayRegion}</li>
 * <li>{@code SetByteArrayRegion}</li>
 * <li>{@code SetCharArrayRegion}</li>
 * <li>{@code SetShortArrayRegion}</li>
 * <li>{@code SetIntArrayRegion}</li>
 * <li>{@code SetLongArrayRegion}</li>
 * <li>{@code SetFloatArrayRegion}</li>
 * <li>{@code SetDoubleArrayRegion}</li>
 * </ul>
 *
 * @see <a href=
 *      "https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/functions.html">JNI
 *      Functions</a>
 */
public final class JNIPrimitiveArrayOperationMethod extends EntryPointCallStubMethod {

    public enum Operation {
        GET_ELEMENTS,
        RELEASE_ELEMENTS,
        GET_REGION,
        SET_REGION,
    }

    private final JavaKind elementKind;
    private final Operation operation;

    public JNIPrimitiveArrayOperationMethod(JavaKind elementKind, Operation operation, ResolvedJavaType declaringClass, ConstantPool constantPool, MetaAccessProvider metaAccess) {
        super(createName(elementKind, operation), declaringClass, createSignature(operation, metaAccess), constantPool);
        if (!EnumSet.of(JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Short,
                        JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double).contains(elementKind)) {

            throw VMError.shouldNotReachHereUnexpectedInput(elementKind); // ExcludeFromJacocoGeneratedReport
        }
        this.elementKind = elementKind;
        this.operation = operation;
    }

    private static String createName(JavaKind elementKind, Operation operation) {
        StringBuilder sb = new StringBuilder(32);
        String kindName = elementKind.name();
        switch (operation) {
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

    private static ResolvedSignature<ResolvedJavaType> createSignature(Operation operation, MetaAccessProvider metaAccess) {
        ResolvedJavaType objectHandleType = metaAccess.lookupJavaType(JNIObjectHandle.class);
        ResolvedJavaType intType = metaAccess.lookupJavaType(int.class);
        ResolvedJavaType returnType;
        List<ResolvedJavaType> args = new ArrayList<>();
        args.add(metaAccess.lookupJavaType(JNIEnvironment.class));
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
            throw VMError.shouldNotReachHereUnexpectedInput(operation); // ExcludeFromJacocoGeneratedReport
        }
        return ResolvedSignature.fromList(args, returnType);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method);

        List<ValueNode> arguments = kit.getInitialArguments();
        ValueNode vmThread = arguments.get(0);
        kit.append(CEntryPointEnterNode.enter(vmThread));

        ValueNode result = null;
        switch (operation) {
            case GET_ELEMENTS: {
                ValueNode arrayHandle = arguments.get(1);
                ValueNode array = kit.unboxHandle(arrayHandle);
                ValueNode isCopy = arguments.get(2);
                result = kit.createArrayViewAndGetAddress(array, isCopy);
                break;
            }
            case RELEASE_ELEMENTS: {
                ValueNode address = arguments.get(2);
                ValueNode mode = arguments.get(3);
                kit.destroyNewestArrayViewByAddress(address, mode);
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
                    ((MergeNode) fwn).setStateAfter(kit.getFrameState().create(kit.bci(), merge));
                }
                break;
            }
            default:
                throw VMError.shouldNotReachHereUnexpectedInput(operation); // ExcludeFromJacocoGeneratedReport
        }
        kit.appendStateSplitProxy();
        CEntryPointLeaveNode leave = new CEntryPointLeaveNode(LeaveAction.Leave);
        kit.append(leave);
        kit.createReturn(result, (result != null) ? result.getStackKind() : JavaKind.Void);

        return kit.finalizeGraph();
    }

    public CEntryPointData createEntryPointData() {
        return CEntryPointData.create(this, CEntryPointData.DEFAULT_NAME, CEntryPointData.DEFAULT_NAME_TRANSFORMATION, "",
                        NoPrologue.class, AutomaticPrologueBailout.class, NoEpilogue.class, FatalExceptionHandler.class, Publish.NotPublished);
    }
}
