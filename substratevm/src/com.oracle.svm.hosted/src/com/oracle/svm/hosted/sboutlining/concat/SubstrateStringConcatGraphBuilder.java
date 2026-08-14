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

import com.oracle.svm.core.sboutlining.concat.SubstrateStringConcatHelper;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Connects {@link SubstrateStringConcatFactory}'s method-handle combinators to a
 * {@link HostedGraphKit}.
 *
 * <p>
 * The exported method handles consume and produce {@link ValueNode} placeholders. When a factory
 * invokes them during graph generation, they add static helper calls, argument conversions, and
 * exception edges to the current graph. Primitive-specific mixer and prepender handles are selected
 * from the requested Java type so the generated method keeps specialized runtime operations.
 *
 * <p>
 * This class performs build-time graph construction only. The calls inserted into the graph target
 * {@link SubstrateStringConcatHelper} or JDK stringification methods and execute later in the
 * native image.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class SubstrateStringConcatGraphBuilder {

    static final MethodHandle wrapperAsPrimitiveTypeMH;

    static final MethodHandle mixInvokeMH;
    static final MethodHandle prependInvokeMH;

    static final MethodHandle booleanStringifierMH;
    static final MethodHandle charStringifierMH;
    static final MethodHandle intStringifierMH;
    static final MethodHandle longStringifierMH;
    static final MethodHandle floatStringifierMH;
    static final MethodHandle doubleStringifierMH;
    static final MethodHandle objectStringifierMH;
    static final MethodHandle newStringifierMH;

    static final MethodHandle simpleConcatMH;
    static final MethodHandle newStringMH;
    static final MethodHandle newArrayWithSuffixMH;
    static final MethodHandle newArrayMH;

    static {
        try {
            Class<?> wrapperClass = Class.forName("sun.invoke.util.Wrapper");
            wrapperAsPrimitiveTypeMH = MethodHandles.lookup().findStatic(wrapperClass, "asPrimitiveType", MethodType.methodType(Class.class, Class.class));
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere(t);
        }

        mixInvokeMH = getMixPrependMH("createMixInvoke", 2);
        prependInvokeMH = getMixPrependMH("createPrependInvoke", 4);

        booleanStringifierMH = genericInvokeMH(ReflectionUtil.lookupMethod(String.class, "valueOf", boolean.class));
        charStringifierMH = genericInvokeMH(ReflectionUtil.lookupMethod(String.class, "valueOf", char.class));
        intStringifierMH = genericInvokeMH(ReflectionUtil.lookupMethod(String.class, "valueOf", int.class));
        longStringifierMH = genericInvokeMH(ReflectionUtil.lookupMethod(String.class, "valueOf", long.class));
        floatStringifierMH = genericInvokeMH(ReflectionUtil.lookupMethod(String.class, "valueOf", float.class));
        doubleStringifierMH = genericInvokeMH(ReflectionUtil.lookupMethod(String.class, "valueOf", double.class));
        objectStringifierMH = stringConcatHelperInvokeMH("stringOf", Object.class);
        newStringifierMH = stringConcatHelperInvokeMH("newStringOf", Object.class);

        simpleConcatMH = stringConcatHelperInvokeMH("simpleConcat", Object.class, Object.class);
        newStringMH = stringConcatHelperInvokeMH("newString", byte[].class, long.class);
        newArrayWithSuffixMH = stringConcatHelperInvokeMH("newArrayWithSuffix", String.class, long.class);
        newArrayMH = stringConcatHelperInvokeMH("newArray", long.class);
    }

    private static MethodHandle getMixPrependMH(String name, int numArgs) {
        try {
            Class<?> rtype = ValueNode.class;
            Class<?>[] ptypes = new Class<?>[numArgs + 2];
            ptypes[0] = Class.class;
            ptypes[1] = HostedGraphKit.class;
            Arrays.fill(ptypes, 2, numArgs + 2, ValueNode.class);
            return MethodHandles.lookup().findStatic(SubstrateStringConcatGraphBuilder.class, name, MethodType.methodType(rtype, ptypes));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    static MethodHandle stringConcatHelperInvokeMH(String name, Class<?>... args) {
        Method invokeMethod = ReflectionUtil.lookupMethod(SubstrateStringConcatHelper.class, name, args);
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
            MethodHandle genericMH = MethodHandles.lookup().findStatic(SubstrateStringConcatGraphBuilder.class, genericName, MethodType.methodType(rtype, ptypes));

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

    public static MethodHandle getMixForKind(Class<?> c) {
        Class<?> primitiveClass;
        try {
            primitiveClass = (Class<?>) wrapperAsPrimitiveTypeMH.invoke(c);
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere(t);
        }
        return mixInvokeMH.bindTo(primitiveClass);
    }

    static ValueNode createMixInvoke(Class<?> argType, HostedGraphKit kit, ValueNode lengthCoder, ValueNode value) {
        Method mixMethod = ReflectionUtil.lookupMethod(SubstrateStringConcatHelper.class, "mix", long.class, argType);
        ResolvedJavaMethod mix = kit.getMetaAccess().lookupJavaMethod(mixMethod);
        return kit.createInvokeWithExceptionAndUnwind(mix, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), lengthCoder, value);
    }

    public static MethodHandle getPrependForKind(Class<?> c) {
        Class<?> primitiveClass;
        try {
            primitiveClass = (Class<?>) wrapperAsPrimitiveTypeMH.invoke(c);
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere(t);
        }
        return prependInvokeMH.bindTo(primitiveClass);
    }

    static ValueNode createPrependInvoke(Class<?> argType, HostedGraphKit kit, ValueNode indexCoder, ValueNode buf, ValueNode value, ValueNode prefix) {
        Method prependMethod = ReflectionUtil.lookupMethod(SubstrateStringConcatHelper.class, "prepend", long.class, byte[].class, argType, String.class);
        ResolvedJavaMethod mix = kit.getMetaAccess().lookupJavaMethod(prependMethod);
        return kit.createInvokeWithExceptionAndUnwind(mix, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), indexCoder, buf, value, prefix);
    }
}
