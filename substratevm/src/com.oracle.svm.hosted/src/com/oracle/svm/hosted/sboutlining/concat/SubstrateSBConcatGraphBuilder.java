/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.sboutlining.concat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.c.struct.SizeOf;

import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.sboutlining.concat.SubstrateSBConcatHelper;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Connects {@link SubstrateSBConcatFactory}'s method-handle combinators to a
 * {@link HostedGraphKit}.
 *
 * <p>
 * In addition to the stringification and helper-call bridges used for string results, this graph
 * builder emits a stack allocation for
 * {@code SubstrateSBConcatHelper.LengthCoderAndCapacityStruct}. The generated method uses that
 * structure to carry length, coder, and observable capacity through the mixer chain before it
 * allocates the backing array and creates a {@link StringBuilder} or {@link StringBuffer}.
 *
 * <p>
 * The method handles execute only while an outlined graph is being built. The helper calls they add
 * to the graph execute at native-image runtime.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class SubstrateSBConcatGraphBuilder {

    static final MethodHandle mixInvokeMH;

    static final MethodHandle newStringBuilderMH;
    static final MethodHandle newStringBufferMH;

    static final MethodHandle newArrayMH;

    static final MethodHandle stackAllocateCoderAndCapacityMH;
    static final MethodHandle initializeCoderAndCapacityMH;
    static final MethodHandle getIndexCoderMH;
    static final MethodHandle getCountMH;

    static {
        mixInvokeMH = getMixInvokeMH();

        newStringBuilderMH = sbConcatHelperInvokeMH("newStringBuilder", byte[].class, long.class, long.class);
        newStringBufferMH = sbConcatHelperInvokeMH("newStringBuffer", byte[].class, long.class, long.class);

        newArrayMH = sbConcatHelperInvokeMH("newArray", SubstrateSBConcatHelper.LengthCoderAndCapacityStruct.class);

        stackAllocateCoderAndCapacityMH = createStackAllocateCodeCapacityStructMethodHandle();
        initializeCoderAndCapacityMH = sbConcatHelperInvokeMH("initializeCoderAndCapacity", SubstrateSBConcatHelper.LengthCoderAndCapacityStruct.class, int.class);
        getIndexCoderMH = sbConcatHelperInvokeMH("getIndexCoder", SubstrateSBConcatHelper.LengthCoderAndCapacityStruct.class);
        getCountMH = sbConcatHelperInvokeMH("getCount", SubstrateSBConcatHelper.LengthCoderAndCapacityStruct.class);
    }

    private static MethodHandle getMixInvokeMH() {
        int numArgs = 2;
        try {
            Class<?> rtype = ValueNode.class;
            Class<?>[] ptypes = new Class<?>[numArgs + 2];
            ptypes[0] = Class.class;
            ptypes[1] = HostedGraphKit.class;
            Arrays.fill(ptypes, 2, numArgs + 2, ValueNode.class);
            return MethodHandles.lookup().findStatic(SubstrateSBConcatGraphBuilder.class, "createMixInvoke", MethodType.methodType(rtype, ptypes));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static MethodHandle sbConcatHelperInvokeMH(String name, Class<?>... args) {
        Method invokeMethod = ReflectionUtil.lookupMethod(SubstrateSBConcatHelper.class, name, args);
        return genericInvokeMH(invokeMethod);
    }

    private static MethodHandle genericInvokeMH(Method invokeMethod) {
        try {
            Class<?> rtype = ValueNode.class;
            int numArgs = invokeMethod.getParameterCount();
            String genericName = "create" + numArgs + "ArgInvoke";
            Class<?>[] ptypes = new Class<?>[numArgs + 2];
            ptypes[0] = Method.class;
            ptypes[1] = HostedGraphKit.class;
            Arrays.fill(ptypes, 2, numArgs + 2, ValueNode.class);
            MethodHandle genericMH = MethodHandles.lookup().findStatic(SubstrateSBConcatGraphBuilder.class, genericName, MethodType.methodType(rtype, ptypes));

            return genericMH.bindTo(invokeMethod);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    public static ValueNode create1ArgInvoke(Method method, HostedGraphKit kit, ValueNode x) {
        ResolvedJavaMethod target = kit.getMetaAccess().lookupJavaMethod(method);
        return kit.createInvokeWithExceptionAndUnwind(target, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), x);
    }

    public static ValueNode create2ArgInvoke(Method method, HostedGraphKit kit, ValueNode x, ValueNode y) {
        ResolvedJavaMethod target = kit.getMetaAccess().lookupJavaMethod(method);
        return kit.createInvokeWithExceptionAndUnwind(target, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), x, y);
    }

    public static ValueNode create3ArgInvoke(Method method, HostedGraphKit kit, ValueNode x, ValueNode y, ValueNode z) {
        ResolvedJavaMethod target = kit.getMetaAccess().lookupJavaMethod(method);
        return kit.createInvokeWithExceptionAndUnwind(target, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), x, y, z);
    }

    public static MethodHandle getMixForKind(Class<?> c) {
        Class<?> primitiveClass;
        try {
            primitiveClass = (Class<?>) SubstrateStringConcatGraphBuilder.wrapperAsPrimitiveTypeMH.invoke(c);
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere(t);
        }
        return mixInvokeMH.bindTo(primitiveClass);
    }

    static ValueNode createMixInvoke(Class<?> argType, HostedGraphKit kit, ValueNode lengthCoder, ValueNode value) {
        Method mixMethod = ReflectionUtil.lookupMethod(SubstrateSBConcatHelper.class, "mix", SubstrateSBConcatHelper.LengthCoderAndCapacityStruct.class, argType);
        ResolvedJavaMethod mix = kit.getMetaAccess().lookupJavaMethod(mixMethod);
        return kit.createInvokeWithExceptionAndUnwind(mix, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), lengthCoder, value);
    }

    private static MethodHandle createStackAllocateCodeCapacityStructMethodHandle() {
        try {
            Class<?> rtype = ValueNode.class;
            Class<?>[] ptypes = {HostedGraphKit.class};
            return MethodHandles.lookup().findStatic(SubstrateSBConcatGraphBuilder.class, "stackAllocateCodeCapacityStruct", MethodType.methodType(rtype, ptypes));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    static ValueNode stackAllocateCodeCapacityStruct(HostedGraphKit kit) {
        int structSize = SizeOf.get(SubstrateSBConcatHelper.LengthCoderAndCapacityStruct.class);
        StackValueNode node = kit.append(StackValueNode.create(structSize, kit.getGraph().method(), kit.bci(), true));
        node.setStateAfter(kit.getFrameState().create(kit.bci(), node));
        return node;
    }
}
