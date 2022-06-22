/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.substitute;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.List;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;

import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.invoke.MethodHandleUtils;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.util.AnnotationWrapper;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Creates a wrapper around a {@link java.lang.invoke.MethodHandle}.PolymorphicSignature method that
 * assembles the arguments into an array, performing necessary boxing operations. The wrapper then
 * transfers execution to the underlying varargs method.
 */
public class PolymorphicSignatureWrapperMethod implements ResolvedJavaMethod, GraphProvider, AnnotationWrapper {

    private final SubstitutionMethod substitutionBaseMethod;
    private final ResolvedJavaMethod originalMethod;
    private final ConstantPool constantPool;

    private StackTraceElement stackTraceElement;
    private static final LineNumberTable lineNumberTable = new LineNumberTable(new int[]{1}, new int[]{0});

    PolymorphicSignatureWrapperMethod(SubstitutionMethod substitutionBaseMethod, ResolvedJavaMethod originalMethod) {
        this.substitutionBaseMethod = substitutionBaseMethod;
        this.originalMethod = originalMethod;
        this.constantPool = substitutionBaseMethod.getDeclaringClass().getDeclaredConstructors()[0].getConstantPool();
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);

        List<ValueNode> args = kit.loadArguments(method.toParameterTypes());
        ValueNode receiver = null;
        if (!substitutionBaseMethod.isStatic()) {
            receiver = args.remove(0);
        }

        ValueNode parameterArray = kit.append(new NewArrayNode(metaAccess.lookupJavaType(Object.class), kit.createInt(args.size()), true));

        for (int i = 0; i < args.size(); ++i) {
            ValueNode arg = args.get(i);
            if (arg.getStackKind().isPrimitive()) {
                arg = kit.createBoxing(arg, arg.getStackKind(), metaAccess.lookupJavaType(arg.getStackKind().toBoxedJavaClass()));
            }
            StateSplit storeIndexedNode = (StateSplit) kit.createStoreIndexed(parameterArray, i, JavaKind.Object, arg);
            storeIndexedNode.setStateAfter(kit.getFrameState().create(kit.bci(), storeIndexedNode));
        }

        ResolvedJavaMethod invokeTarget;
        if (metaAccess instanceof AnalysisMetaAccess) {
            invokeTarget = metaAccess.getUniverse().lookup(substitutionBaseMethod.getOriginal());
        } else {
            invokeTarget = metaAccess.getUniverse().lookup(((AnalysisMetaAccess) metaAccess.getWrapped()).getUniverse().lookup(substitutionBaseMethod.getOriginal()));
        }

        InvokeWithExceptionNode invoke;
        if (substitutionBaseMethod.isStatic()) {
            invoke = kit.createInvokeWithExceptionAndUnwind(invokeTarget, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), parameterArray);
        } else {
            invoke = kit.createInvokeWithExceptionAndUnwind(invokeTarget, CallTargetNode.InvokeKind.Virtual, kit.getFrameState(), kit.bci(), receiver, parameterArray);
        }

        JavaKind returnKind = getSignature().getReturnKind();
        ValueNode retVal = invoke;
        if (returnKind.isPrimitive() && returnKind != JavaKind.Void) {
            switch (returnKind) {
                /*
                 * It is possible to have a discrepancy between the return type of an invoked method
                 * handle and the bytecode type of the invocation itself (for compatible primitive
                 * types only). For example, int value = mh.invokeBasic(...) where
                 * mh.type().returnType() == short.class.
                 * 
                 * This doesn't cause trouble in HotSpot since these values can be silently casted
                 * to the expected type. However, since Native Image handles the return value as a
                 * boxed object, it needs to explicitly cast it to the required type to avoid tricky
                 * bugs to occur.
                 *
                 * In the example case above, we have a Short value being interpreted as an Integer.
                 * This happens to almost work because they have similar layouts, but the high 16
                 * bits of the Integer are not guaranteed to be zero.
                 *
                 * The only return types worth considering are Short, Int and Long. A Long return
                 * type will always be wide enough to accomodate the full original value. In the
                 * case of a Byte return type, there is no need to worry about this since the result
                 * can only be truncated. In the Char case, the method handle invocation will return
                 * a WrongMethodTypeException when trying to call a method handle with a boolean or
                 * byte return type, so there's no need for special handling here.
                 */
                case Short:
                case Int:
                case Long:
                    ValueNode methodHandleOrMemberName;
                    ResolvedJavaMethod unboxMethod;
                    try {
                        String unboxMethodName = returnKind.toString() + "Unbox";
                        switch (substitutionBaseMethod.getName()) {
                            case "invokeBasic":
                            case "invokeExact":
                            case "invoke":
                                methodHandleOrMemberName = receiver;
                                unboxMethod = metaAccess.lookupJavaMethod(
                                                MethodHandleUtils.class.getMethod(unboxMethodName, Object.class, MethodHandle.class));
                                break;
                            case "linkToVirtual":
                            case "linkToStatic":
                            case "linkToInterface":
                            case "linkToSpecial":
                                methodHandleOrMemberName = args.get(args.size() - 1);
                                unboxMethod = metaAccess.lookupJavaMethod(
                                                MethodHandleUtils.class.getMethod(unboxMethodName, Object.class, Target_java_lang_invoke_MemberName.class));
                                break;
                            default:
                                throw shouldNotReachHere();
                        }
                    } catch (NoSuchMethodException e) {
                        throw shouldNotReachHere();
                    }
                    retVal = kit.createInvokeWithExceptionAndUnwind(unboxMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), retVal, methodHandleOrMemberName);
                    break;
                default:
                    retVal = kit.createUnboxing(invoke, returnKind);
            }
        }
        kit.createReturn(retVal, returnKind);

        return kit.finalizeGraph();
    }

    @Override
    public String getName() {
        return substitutionBaseMethod.getName();
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return substitutionBaseMethod.getDeclaringClass();
    }

    @Override
    public Signature getSignature() {
        return originalMethod.getSignature();
    }

    @Override
    public boolean allowRuntimeCompilation() {
        return false;
    }

    @Override
    public byte[] getCode() {
        return null;
    }

    @Override
    public int getCodeSize() {
        return 0;
    }

    @Override
    public int getMaxLocals() {
        return 2 * getSignature().getParameterCount(true);
    }

    @Override
    public int getMaxStackSize() {
        return 2;
    }

    @Override
    public boolean isSynthetic() {
        return true;
    }

    @Override
    public boolean isVarArgs() {
        return false;
    }

    @Override
    public boolean isBridge() {
        return false;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public boolean isClassInitializer() {
        return false;
    }

    @Override
    public boolean isConstructor() {
        return false;
    }

    @Override
    public boolean canBeStaticallyBound() {
        return true;
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return new ExceptionHandler[0];
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        if (stackTraceElement == null) {
            stackTraceElement = new StackTraceElement(getDeclaringClass().toJavaName(true), getName(), "generated", 0);
        }
        return stackTraceElement;
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        throw VMError.unimplemented();
    }

    @Override
    public void reprofile() {
        throw VMError.unimplemented();
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        throw VMError.unimplemented();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        throw VMError.unimplemented();
    }

    @Override
    public boolean canBeInlined() {
        return true;
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return false;
    }

    @Override
    public boolean shouldBeInlined() {
        return true;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return lineNumberTable;
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return null;
    }

    @Override
    public Constant getEncoding() {
        throw VMError.unimplemented();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        throw VMError.unimplemented();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw VMError.unimplemented();
    }

    @Override
    public AnnotatedElement getAnnotationRoot() {
        return substitutionBaseMethod;
    }

    @Override
    public int getModifiers() {
        return substitutionBaseMethod.getModifiers();
    }
}
