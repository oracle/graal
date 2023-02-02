/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import java.lang.reflect.Array;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.meta.SubstrateMemoryAccessProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.graal.meta.SubstrateConstantReflectionProvider;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateMemoryAccessProviderImpl;
import com.oracle.svm.graal.meta.SubstrateMetaAccess;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

final class IsolateAwareConstantReflectionProvider extends SubstrateConstantReflectionProvider {
    private static final IsolatedMemoryAccessProvider ISOLATED_MEMORY_ACCESS_PROVIDER_SINGLETON = new IsolatedMemoryAccessProvider();

    private static boolean isIsolatedCompilation() {
        return SubstrateOptions.shouldCompileInIsolates();
    }

    static final class IsolatedMemoryAccessProvider implements SubstrateMemoryAccessProvider {
        private IsolatedMemoryAccessProvider() {
        }

        @Override
        public JavaConstant readPrimitiveConstant(JavaKind kind, Constant base, long displacement, int bits) throws IllegalArgumentException {
            return read(kind, base, displacement, bits, null);
        }

        @Override
        public JavaConstant readObjectConstant(Constant base, long displacement) {
            return read(JavaKind.Object, base, displacement, 0, null);
        }

        @Override
        public JavaConstant readNarrowObjectConstant(Constant base, long displacement, CompressEncoding encoding) {
            return read(JavaKind.Object, base, displacement, 0, encoding);
        }

        private static JavaConstant read(JavaKind kind, Constant base, long displacement, int primitiveBits, CompressEncoding encoding) {
            ConstantData baseData = StackValue.get(ConstantData.class);
            ConstantDataConverter.fromCompiler(base, baseData);
            long compressBase = (encoding != null) ? encoding.getBase() : 0;
            int compressShift = (encoding != null) ? encoding.getShift() : 0;
            assert (encoding != null) == (compressBase != 0 || compressShift != 0);
            ConstantData resultData = StackValue.get(ConstantData.class);
            read0(IsolatedCompileContext.get().getClient(), kind.getTypeChar(), baseData, displacement, primitiveBits, compressBase, compressShift, resultData);
            return ConstantDataConverter.toCompiler(resultData);
        }

        @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
        private static void read0(@SuppressWarnings("unused") ClientIsolateThread client, char kindChar, ConstantData baseData, long displacement,
                        int primitiveBits, long compressBase, int compressShift, ConstantData resultData) {
            JavaConstant base = ConstantDataConverter.toClient(baseData);
            JavaConstant result;
            if (kindChar == JavaKind.Object.getTypeChar()) {
                if (compressBase != 0 || compressShift != 0) {
                    result = SubstrateMemoryAccessProviderImpl.SINGLETON.readNarrowObjectConstant(base, displacement, new CompressEncoding(compressBase, compressShift));
                } else {
                    result = SubstrateMemoryAccessProviderImpl.SINGLETON.readObjectConstant(base, displacement);
                }
            } else {
                JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeChar(kindChar);
                result = SubstrateMemoryAccessProviderImpl.SINGLETON.readPrimitiveConstant(kind, base, displacement, primitiveBits);
            }
            ConstantDataConverter.fromClient(result, resultData);
        }
    }

    IsolateAwareConstantReflectionProvider(SubstrateMetaAccess metaAccess) {
        super(metaAccess);
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        if (!isIsolatedCompilation()) {
            return super.readArrayLength(array);
        }
        if (array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }
        if (array instanceof IsolatedObjectConstant) {
            ClientHandle<?> arrayHandle = ((IsolatedObjectConstant) array).getHandle();
            int length = readArrayLength0(IsolatedCompileContext.get().getClient(), arrayHandle);
            return (length >= 0) ? length : null;
        }
        Object arrayObj = SubstrateObjectConstant.asObject(array);
        if (!arrayObj.getClass().isArray()) {
            return null;
        }
        // the length of an image heap array is the same across all isolates
        assert ImageHeapObjects.isInImageHeap(arrayObj);
        return Array.getLength(arrayObj);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static int readArrayLength0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<?> arrayHandle) {
        Object array = IsolatedCompileClient.get().unhand(arrayHandle);
        if (!array.getClass().isArray()) {
            return -1;
        }
        return Array.getLength(array);
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        if (!isIsolatedCompilation()) {
            return super.readArrayElement(array, index);
        }
        if (array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }
        ConstantData arrayData = StackValue.get(ConstantData.class);
        ConstantData resultData = StackValue.get(ConstantData.class);
        ConstantDataConverter.fromCompiler(array, arrayData);
        readArrayElement0(IsolatedCompileContext.get().getClient(), arrayData, index, resultData);
        return ConstantDataConverter.toCompiler(resultData);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void readArrayElement0(@SuppressWarnings("unused") ClientIsolateThread client, ConstantData arrayData, int index, ConstantData resultData) {
        JavaConstant array = ConstantDataConverter.toClient(arrayData);
        Object a = SubstrateObjectConstant.asObject(array);
        Constant result;
        if (index < 0 || index >= Array.getLength(a)) {
            result = null;
        } else if (a instanceof Object[]) {
            Object element = ((Object[]) a)[index];
            result = SubstrateObjectConstant.forObject(element);
        } else {
            result = JavaConstant.forBoxedPrimitive(Array.get(a, index));
        }
        ConstantDataConverter.fromClient(result, resultData);
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        if (!isIsolatedCompilation()) {
            return super.readFieldValue(field, receiver);
        }
        ConstantData resultData = StackValue.get(ConstantData.class);
        ConstantData receiverData = StackValue.get(ConstantData.class);
        ConstantDataConverter.fromCompiler(receiver, receiverData);
        readFieldValue0(IsolatedCompileContext.get().getClient(), ImageHeapObjects.ref((SubstrateField) field), receiverData, resultData);
        return ConstantDataConverter.toCompiler(resultData);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void readFieldValue0(@SuppressWarnings("unused") ClientIsolateThread client, ImageHeapRef<SubstrateField> fieldRef, ConstantData receiverData, ConstantData resultData) {
        JavaConstant receiver = ConstantDataConverter.toClient(receiverData);
        Constant result = readFieldValue(ImageHeapObjects.deref(fieldRef), receiver);
        ConstantDataConverter.fromClient(result, resultData);
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant primitive) {
        if (!isIsolatedCompilation()) {
            return super.boxPrimitive(primitive);
        }
        if (!canBoxPrimitive(primitive)) {
            return null;
        }
        ConstantData resultData = StackValue.get(ConstantData.class);
        ConstantData primitiveData = StackValue.get(ConstantData.class);
        ConstantDataConverter.fromCompiler(primitive, primitiveData);
        boxPrimitive0(IsolatedCompileContext.get().getClient(), primitiveData, resultData);
        return ConstantDataConverter.toCompiler(resultData);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void boxPrimitive0(@SuppressWarnings("unused") ClientIsolateThread client, ConstantData primitiveData, ConstantData resultData) {
        JavaConstant primitive = ConstantDataConverter.toClient(primitiveData);
        Constant result = SubstrateObjectConstant.forObject(primitive.asBoxedPrimitive());
        ConstantDataConverter.fromClient(result, resultData);
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant boxed) {
        if (boxed instanceof IsolatedObjectConstant) {
            ConstantData resultData = StackValue.get(ConstantData.class);
            ConstantData boxedData = StackValue.get(ConstantData.class);
            ConstantDataConverter.fromCompiler(boxed, boxedData);
            unboxPrimitive0(IsolatedCompileContext.get().getClient(), boxedData, resultData);
            return ConstantDataConverter.toCompiler(resultData);
        }
        // boxed primitives are immutable and identical across isolates
        return super.unboxPrimitive(boxed);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void unboxPrimitive0(@SuppressWarnings("unused") ClientIsolateThread client, ConstantData boxedData, ConstantData resultData) {
        Constant boxed = ConstantDataConverter.toClient(boxedData);
        Constant result = JavaConstant.forBoxedPrimitive(SubstrateObjectConstant.asObject(boxed));
        ConstantDataConverter.fromClient(result, resultData);
    }

    @Override
    public JavaConstant forString(String value) {
        return super.forString(value); // Strings must be copied during code installation
    }

    @Override
    public ResolvedJavaType asJavaType(Constant hub) {
        Constant resolved = hub;
        if (hub instanceof IsolatedObjectConstant) {
            ConstantData hubData = StackValue.get(ConstantData.class);
            ConstantDataConverter.fromCompiler(hub, hubData);
            ImageHeapRef<DynamicHub> ref = getHubConstantAsImageHeapRef(IsolatedCompileContext.get().getClient(), hubData);
            resolved = SubstrateObjectConstant.forObject(ImageHeapObjects.deref(ref));
        }
        return super.asJavaType(resolved);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ImageHeapRef<DynamicHub> getHubConstantAsImageHeapRef(@SuppressWarnings("unused") ClientIsolateThread client, ConstantData hubData) {
        JavaConstant hub = ConstantDataConverter.toClient(hubData);
        Object target = SubstrateObjectConstant.asObject(hub);
        return (target instanceof DynamicHub) ? ImageHeapObjects.ref((DynamicHub) target) : WordFactory.nullPointer();
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return isIsolatedCompilation() ? ISOLATED_MEMORY_ACCESS_PROVIDER_SINGLETON : SubstrateMemoryAccessProviderImpl.SINGLETON;
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        // Hubs exist in the image heap and are identical across isolates
        return super.asJavaClass(type);
    }

    @Override
    public int getImageHeapOffset(JavaConstant constant) {
        if (constant instanceof IsolatedObjectConstant) {
            ConstantData constantData = StackValue.get(ConstantData.class);
            ConstantDataConverter.fromCompiler(constant, constantData);
            return getImageHeapOffset0(IsolatedCompileContext.get().getClient(), constantData);
        }
        return super.getImageHeapOffset(constant);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static int getImageHeapOffset0(@SuppressWarnings("unused") ClientIsolateThread client, ConstantData constantData) {
        Constant constant = ConstantDataConverter.toClient(constantData);
        return getImageHeapOffsetInternal((SubstrateObjectConstant) constant);
    }
}
