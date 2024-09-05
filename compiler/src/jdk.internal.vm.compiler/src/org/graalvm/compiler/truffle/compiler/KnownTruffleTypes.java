/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@SuppressWarnings("this-escape")
public class KnownTruffleTypes extends AbstractKnownTruffleTypes {

    // Checkstyle: stop field name check

    // java.base
    public final ResolvedJavaType Object_Array = lookupType(Object[].class);
    public final ResolvedJavaType java_lang_Object = lookupType(Object.class);
    public final ResolvedJavaType Objects = lookupType(Objects.class);
    public final ResolvedJavaType MethodHandle = lookupType(MethodHandle.class);
    public final ResolvedJavaType Buffer = lookupTypeCached(Buffer.class);
    public final ResolvedJavaField Buffer_segment = findField(lookupType(Buffer.class), "segment");
    public final ResolvedJavaType ArithmeticException = lookupType(ArithmeticException.class);
    public final ResolvedJavaType IllegalArgumentException = lookupType(IllegalArgumentException.class);
    public final ResolvedJavaType IllegalStateException = lookupType(IllegalStateException.class);
    public final ResolvedJavaType VirtualMachineError = lookupType(VirtualMachineError.class);
    public final ResolvedJavaType IndexOutOfBoundsException = lookupType(IndexOutOfBoundsException.class);
    public final ResolvedJavaType ClassCastException = lookupType(ClassCastException.class);
    public final ResolvedJavaType BufferUnderflowException = lookupType(BufferUnderflowException.class);
    public final ResolvedJavaType BufferOverflowException = lookupType(BufferOverflowException.class);
    public final ResolvedJavaType ReadOnlyBufferException = lookupType(ReadOnlyBufferException.class);
    public final ResolvedJavaType ScopedMemoryAccess_ScopedAccessError = lookupTypeOptional("jdk.internal.misc.ScopedMemoryAccess$ScopedAccessError");
    public final ResolvedJavaType AbstractMemorySegmentImpl = lookupTypeOptional("jdk.internal.foreign.AbstractMemorySegmentImpl");
    public final ResolvedJavaType MemorySegmentProxy = lookupTypeOptional("jdk.internal.access.foreign.MemorySegmentProxy");

    public final Set<ResolvedJavaType> primitiveBoxTypes = Set.of(
                    lookupType(JavaKind.Boolean.toBoxedJavaClass()),
                    lookupType(JavaKind.Byte.toBoxedJavaClass()),
                    lookupType(JavaKind.Char.toBoxedJavaClass()),
                    lookupType(JavaKind.Double.toBoxedJavaClass()),
                    lookupType(JavaKind.Float.toBoxedJavaClass()),
                    lookupType(JavaKind.Int.toBoxedJavaClass()),
                    lookupType(JavaKind.Long.toBoxedJavaClass()),
                    lookupType(JavaKind.Short.toBoxedJavaClass()));
    public final ResolvedJavaField Reference_referent = findField(lookupTypeCached(Reference.class), "referent");

    // truffle.api
    public final ResolvedJavaType CompilerDirectives = lookupType("com.oracle.truffle.api.CompilerDirectives");
    public final ResolvedJavaType CompilerAsserts = lookupType("com.oracle.truffle.api.CompilerAsserts");
    public final ResolvedJavaType ExactMath = lookupType("com.oracle.truffle.api.ExactMath");
    public final ResolvedJavaType HostCompilerDirectives = lookupType("com.oracle.truffle.api.HostCompilerDirectives");
    public final ResolvedJavaType TruffleSafepoint = lookupType("com.oracle.truffle.api.TruffleSafepoint");

    // truffle.api.nodes
    public final ResolvedJavaType RootNode = lookupType("com.oracle.truffle.api.nodes.RootNode");
    public final ResolvedJavaType Node = lookupTypeCached("com.oracle.truffle.api.nodes.Node");
    public final ResolvedJavaField Node_parent = findField(Node, "parent");
    public final ResolvedJavaType UnexpectedResultException = lookupType("com.oracle.truffle.api.nodes.UnexpectedResultException");
    public final ResolvedJavaType SlowPathException = lookupType("com.oracle.truffle.api.nodes.SlowPathException");

    // truffle.api.frame
    public final ResolvedJavaType VirtualFrame = lookupType("com.oracle.truffle.api.frame.VirtualFrame");
    public final ResolvedJavaType FrameDescriptor = lookupTypeCached("com.oracle.truffle.api.frame.FrameDescriptor");
    public final ResolvedJavaField FrameDescriptor_defaultValue = findField(FrameDescriptor, "defaultValue");
    public final ResolvedJavaField FrameDescriptor_materializeCalled = findField(FrameDescriptor, "materializeCalled");
    public final ResolvedJavaField FrameDescriptor_indexedSlotTags = findField(FrameDescriptor, "indexedSlotTags");
    public final ResolvedJavaField FrameDescriptor_auxiliarySlotCount = findField(FrameDescriptor, "auxiliarySlotCount");
    public final ResolvedJavaField FrameDescriptor_staticMode = findField(FrameDescriptor, "staticMode");

    public final ResolvedJavaType FrameSlotKind = lookupTypeCached("com.oracle.truffle.api.frame.FrameSlotKind");
    public final ResolvedJavaField FrameSlotKind_Object = findField(FrameSlotKind, "Object");
    public final ResolvedJavaField FrameSlotKind_Long = findField(FrameSlotKind, "Long");
    public final ResolvedJavaField FrameSlotKind_Int = findField(FrameSlotKind, "Int");
    public final ResolvedJavaField FrameSlotKind_Double = findField(FrameSlotKind, "Double");
    public final ResolvedJavaField FrameSlotKind_Float = findField(FrameSlotKind, "Float");
    public final ResolvedJavaField FrameSlotKind_Boolean = findField(FrameSlotKind, "Boolean");
    public final ResolvedJavaField FrameSlotKind_Byte = findField(FrameSlotKind, "Byte");
    public final ResolvedJavaField FrameSlotKind_Illegal = findField(FrameSlotKind, "Illegal");
    public final ResolvedJavaField FrameSlotKind_Static = findField(FrameSlotKind, "Static");
    public final ResolvedJavaField FrameSlotKind_tag = findField(FrameSlotKind, "tag");

    public final JavaKind[] FrameSlotKind_tagIndexToJavaKind;
    public final EnumMap<JavaKind, Integer> FrameSlotKind_javaKindToTagIndex;

    // truffle.api.object
    public final ResolvedJavaType Shape = lookupType("com.oracle.truffle.api.object.Shape");
    public final ResolvedJavaType DynamicObject = lookupType("com.oracle.truffle.api.object.DynamicObject");
    public final ResolvedJavaType UnsafeAccess = lookupType("com.oracle.truffle.object.UnsafeAccess");

    // truffle.api.string
    public final ResolvedJavaType TruffleString = lookupType("com.oracle.truffle.api.strings.TruffleString");
    public final ResolvedJavaType AbstractTruffleString = lookupTypeCached("com.oracle.truffle.api.strings.AbstractTruffleString");
    public final ResolvedJavaField AbstractTruffleString_data = findField(AbstractTruffleString, "data");
    public final ResolvedJavaField AbstractTruffleString_hashCode = findField(AbstractTruffleString, "hashCode");
    public final ResolvedJavaField AbstractTruffleString_codeRange = findField(AbstractTruffleString, "codeRange");
    public final ResolvedJavaField AbstractTruffleString_codePointLength = findField(AbstractTruffleString, "codePointLength");

    // truffle.api.impl
    public final ResolvedJavaType FrameWithoutBoxing = lookupTypeCached("com.oracle.truffle.api.impl.FrameWithoutBoxing");
    public final ResolvedJavaField FrameWithoutBoxing_descriptor = findField(FrameWithoutBoxing, "descriptor");
    public final ResolvedJavaField FrameWithoutBoxing_arguments = findField(FrameWithoutBoxing, "arguments");
    public final ResolvedJavaField FrameWithoutBoxing_auxiliarySlots = findField(FrameWithoutBoxing, "auxiliarySlots");
    public final ResolvedJavaField FrameWithoutBoxing_indexedTags = findField(FrameWithoutBoxing, "indexedTags");
    public final ResolvedJavaField FrameWithoutBoxing_indexedLocals = findField(FrameWithoutBoxing, "indexedLocals");
    public final ResolvedJavaField FrameWithoutBoxing_indexedPrimitiveLocals = findField(FrameWithoutBoxing, "indexedPrimitiveLocals");
    public final ResolvedJavaField FrameWithoutBoxing_EMPTY_OBJECT_ARRAY = findField(FrameWithoutBoxing, "EMPTY_OBJECT_ARRAY");
    public final ResolvedJavaField FrameWithoutBoxing_EMPTY_LONG_ARRAY = findField(FrameWithoutBoxing, "EMPTY_LONG_ARRAY");
    public final ResolvedJavaField FrameWithoutBoxing_EMPTY_BYTE_ARRAY = findField(FrameWithoutBoxing, "EMPTY_BYTE_ARRAY");
    public final ResolvedJavaField[] FrameWithoutBoxing_instanceFields = findInstanceFields(FrameWithoutBoxing);
    public final ResolvedJavaType AbstractAssumption = lookupTypeCached("com.oracle.truffle.api.impl.AbstractAssumption");
    public final ResolvedJavaField AbstractAssumption_isValid = findField(AbstractAssumption, "isValid");

    // truffle.runtime
    public final ResolvedJavaType BaseOSRRootNode = lookupTypeCached("com.oracle.truffle.runtime.BaseOSRRootNode");
    public final ResolvedJavaField BaseOSRRootNode_loopNode = findField(BaseOSRRootNode, "loopNode");
    public final ResolvedJavaType CompilationState = lookupType("com.oracle.truffle.runtime.CompilationState");

    public final ResolvedJavaType OptimizedCallTarget = lookupTypeCached("com.oracle.truffle.runtime.OptimizedCallTarget");
    public final ResolvedJavaMethod OptimizedCallTarget_call = findMethod(OptimizedCallTarget, "call", Object_Array);
    public final ResolvedJavaMethod OptimizedCallTarget_callDirect = findMethod(OptimizedCallTarget, "callDirect", Node, Object_Array);
    public final ResolvedJavaMethod OptimizedCallTarget_callInlined = findMethod(OptimizedCallTarget, "callInlined", Node, Object_Array);
    public final ResolvedJavaMethod OptimizedCallTarget_callIndirect = findMethod(OptimizedCallTarget, "callIndirect", Node, Object_Array);
    public final ResolvedJavaMethod OptimizedCallTarget_callBoundary = findMethod(OptimizedCallTarget, "callBoundary", Object_Array);
    public final ResolvedJavaMethod OptimizedCallTarget_executeRootNode = findMethod(OptimizedCallTarget, "executeRootNode", VirtualFrame, CompilationState);
    public final ResolvedJavaMethod OptimizedCallTarget_profiledPERoot = findMethod(OptimizedCallTarget, "profiledPERoot", Object_Array);
    public final ResolvedJavaField OptimizedCallTarget_nodeRewritingAssumption = findField(OptimizedCallTarget, "nodeRewritingAssumption");
    public final ResolvedJavaField OptimizedCallTarget_validRootAssumption = findField(OptimizedCallTarget, "validRootAssumption");
    public final ResolvedJavaField OptimizedCallTarget_rootNode = findField(OptimizedCallTarget, "rootNode");

    public final ResolvedJavaType OptimizedDirectCallNode = lookupTypeCached("com.oracle.truffle.runtime.OptimizedDirectCallNode");
    public final ResolvedJavaField OptimizedDirectCallNode_inliningForced = findField(OptimizedDirectCallNode, "inliningForced");
    public final ResolvedJavaField OptimizedDirectCallNode_callCount = findField(OptimizedDirectCallNode, "callCount");

    public final ResolvedJavaType OptimizedAssumption = lookupType("com.oracle.truffle.runtime.OptimizedAssumption");
    public final ResolvedJavaType[] skippedExceptionTypes = createSkippedExceptionTypes();

    // Checkstyle: resume field name check
    protected final ConstantReflectionProvider constantReflection;

    public KnownTruffleTypes(TruffleCompilerRuntime runtime, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        super(runtime, metaAccess);
        this.constantReflection = constantReflection;

        FrameSlotKind_tagIndexToJavaKind = createJavaKindByTagIndex(new ResolvedJavaField[]{
                        FrameSlotKind_Object,
                        FrameSlotKind_Long,
                        FrameSlotKind_Int,
                        FrameSlotKind_Double,
                        FrameSlotKind_Float,
                        FrameSlotKind_Boolean,
                        FrameSlotKind_Byte,
                        FrameSlotKind_Illegal,
                        FrameSlotKind_Static,
        }, new JavaKind[]{
                        JavaKind.Object,
                        JavaKind.Long,
                        JavaKind.Int,
                        JavaKind.Double,
                        JavaKind.Float,
                        JavaKind.Boolean,
                        JavaKind.Byte,
                        JavaKind.Illegal,
                        JavaKind.Illegal,
        });
        FrameSlotKind_javaKindToTagIndex = createJavaKindMap(FrameSlotKind_tagIndexToJavaKind);
    }

    private ResolvedJavaType[] createSkippedExceptionTypes() {
        List<ResolvedJavaType> types = new ArrayList<>(16);
        types.add(UnexpectedResultException);
        types.add(SlowPathException);
        if (ScopedMemoryAccess_ScopedAccessError != null) {
            types.add(ScopedMemoryAccess_ScopedAccessError);
        }
        types.add(ArithmeticException);
        types.add(IllegalArgumentException);
        types.add(IllegalStateException);
        types.add(VirtualMachineError);
        types.add(IndexOutOfBoundsException);
        types.add(ClassCastException);
        types.add(BufferUnderflowException);
        types.add(BufferOverflowException);
        types.add(ReadOnlyBufferException);
        return types.toArray(ResolvedJavaType[]::new);
    }

    private JavaKind[] createJavaKindByTagIndex(ResolvedJavaField[] fields, JavaKind[] kinds) {
        int[] tagIndexes = new int[fields.length];
        int maxValue = -1;
        for (int i = 0; i < fields.length; i++) {
            ResolvedJavaField field = fields[i];
            JavaConstant constant = constantReflection.readFieldValue(field, null);
            tagIndexes[i] = constantReflection.readFieldValue(FrameSlotKind_tag, constant).asInt();
            maxValue = Math.max(maxValue, tagIndexes[i]);
        }
        JavaKind[] indexToJavaKind = new JavaKind[maxValue + 1];
        for (int i = 0; i < tagIndexes.length; i++) {
            assert indexToJavaKind[tagIndexes[i]] == null : "tag indices must be unique";
            indexToJavaKind[tagIndexes[i]] = kinds[i];
        }
        return indexToJavaKind;
    }

    private static EnumMap<JavaKind, Integer> createJavaKindMap(JavaKind[] tagIndexToJavaKind) {
        EnumMap<JavaKind, Integer> map = new EnumMap<>(JavaKind.class);
        for (int i = 0; i < tagIndexToJavaKind.length; i++) {
            map.putIfAbsent(tagIndexToJavaKind[i], i);
        }
        return map;
    }

}
