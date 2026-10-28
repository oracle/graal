/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.snapshot.capnproto.elements;

import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapIntListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapIntListWriter;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStringListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStringListWriter;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListWriter;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod;
import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod.WrappedMethod;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisMethodData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnnotationData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;
import com.oracle.svm.shaded.org.capnproto.Void;

public final class CapnProtoPersistedAnalysisMethodData {
    public static PersistedAnalysisMethodData.Writer writer(PersistedAnalysisMethod.Builder delegate) {
        return new PersistedAnalysisMethodWriterAdapter(delegate);
    }

    public static PersistedAnalysisMethodData.Loader loader(PersistedAnalysisMethod.Reader delegate) {
        return new PersistedAnalysisMethodLoaderAdapter(delegate);
    }
}

record PersistedAnalysisMethodWriterAdapter(PersistedAnalysisMethod.Builder delegate) implements PersistedAnalysisMethodData.Writer {
    @Override
    public SnapshotStringList.Writer initArgumentClassNames(int size) {
        return wrapStringListWriter(delegate.initArgumentClassNames(size));
    }

    @Override
    public void setClassName(String value) {
        delegate.setClassName(value);
    }

    @Override
    public void setDescriptor(String value) {
        delegate.setDescriptor(value);
    }

    @Override
    public void setDeclaringTypeId(int value) {
        delegate.setDeclaringTypeId(value);
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initArgumentTypeIds(int size) {
        return wrapIntListWriter(delegate.initArgumentTypeIds(size));
    }

    @Override
    public void setId(int value) {
        delegate.setId(value);
    }

    @Override
    public void setName(String value) {
        delegate.setName(value);
    }

    @Override
    public void setReturnTypeId(int value) {
        delegate.setReturnTypeId(value);
    }

    @Override
    public void setIsVarArgs(boolean value) {
        delegate.setIsVarArgs(value);
    }

    @Override
    public void setIsBridge(boolean value) {
        delegate.setIsBridge(value);
    }

    @Override
    public void setCanBeStaticallyBound(boolean value) {
        delegate.setCanBeStaticallyBound(value);
    }

    @Override
    public void setModifiers(int value) {
        delegate.setModifiers(value);
    }

    @Override
    public void setIsConstructor(boolean value) {
        delegate.setIsConstructor(value);
    }

    @Override
    public void setIsSynthetic(boolean value) {
        delegate.setIsSynthetic(value);
    }

    @Override
    public void setIsDeclared(boolean value) {
        delegate.setIsDeclared(value);
    }

    @Override
    public void setBytecode(byte[] value) {
        delegate.setBytecode(value);
    }

    @Override
    public void setBytecodeSize(int value) {
        delegate.setBytecodeSize(value);
    }

    @Override
    public void setMethodHandleIntrinsicName(String value) {
        delegate.setMethodHandleIntrinsicName(value);
    }

    @Override
    public SnapshotStructList.Writer<PersistedAnnotationData.Writer> initAnnotationList(int size) {
        return wrapStructListWriter(delegate.initAnnotationList(size), CapnProtoPersistedAnnotationData::writer);
    }

    @Override
    public void setIsVirtualRootMethod(boolean value) {
        delegate.setIsVirtualRootMethod(value);
    }

    @Override
    public void setIsDirectRootMethod(boolean value) {
        delegate.setIsDirectRootMethod(value);
    }

    @Override
    public void setIsInvoked(boolean value) {
        delegate.setIsInvoked(value);
    }

    @Override
    public void setIsImplementationInvoked(boolean value) {
        delegate.setIsImplementationInvoked(value);
    }

    @Override
    public void setIsIntrinsicMethod(boolean value) {
        delegate.setIsIntrinsicMethod(value);
    }

    @Override
    public void setCompilationBehaviorOrdinal(byte value) {
        delegate.setCompilationBehaviorOrdinal(value);
    }

    @Override
    public void setAnalysisGraphLocation(String value) {
        delegate.setAnalysisGraphLocation(value);
    }

    @Override
    public void setAnalysisGraphIsIntrinsic(boolean value) {
        delegate.setAnalysisGraphIsIntrinsic(value);
    }

    @Override
    public void setStrengthenedGraphLocation(String value) {
        delegate.setStrengthenedGraphLocation(value);
    }

    @Override
    public PersistedAnalysisMethodData.WrappedMethod.Writer getWrappedMethod() {
        return new WrappedMethodWriterAdapter(delegate.getWrappedMethod());
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initClosedFieldWrites(int size) {
        return wrapIntListWriter(delegate.initClosedFieldWrites(size));
    }

    @Override
    public void setHostedMethodIndex(int value) {
        delegate.setHostedMethodIndex(value);
    }
}

record PersistedAnalysisMethodLoaderAdapter(PersistedAnalysisMethod.Reader delegate) implements PersistedAnalysisMethodData.Loader {
    @Override
    public boolean hasArgumentClassNames() {
        return delegate.hasArgumentClassNames();
    }

    @Override
    public SnapshotStringList.Loader getArgumentClassNames() {
        return wrapStringListLoader(delegate.getArgumentClassNames());
    }

    @Override
    public boolean hasClassName() {
        return delegate.hasClassName();
    }

    @Override
    public String getClassName() {
        return delegate.getClassName().toString();
    }

    @Override
    public String getDescriptor() {
        return delegate.getDescriptor().toString();
    }

    @Override
    public int getDeclaringTypeId() {
        return delegate.getDeclaringTypeId();
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getArgumentTypeIds() {
        return wrapIntListLoader(delegate.getArgumentTypeIds());
    }

    @Override
    public int getId() {
        return delegate.getId();
    }

    @Override
    public String getName() {
        return delegate.getName().toString();
    }

    @Override
    public int getReturnTypeId() {
        return delegate.getReturnTypeId();
    }

    @Override
    public boolean getIsVarArgs() {
        return delegate.getIsVarArgs();
    }

    @Override
    public boolean getIsBridge() {
        return delegate.getIsBridge();
    }

    @Override
    public boolean getCanBeStaticallyBound() {
        return delegate.getCanBeStaticallyBound();
    }

    @Override
    public int getModifiers() {
        return delegate.getModifiers();
    }

    @Override
    public boolean getIsConstructor() {
        return delegate.getIsConstructor();
    }

    @Override
    public boolean getIsSynthetic() {
        return delegate.getIsSynthetic();
    }

    @Override
    public boolean getIsDeclared() {
        return delegate.getIsDeclared();
    }

    @Override
    public boolean hasBytecode() {
        return delegate.hasBytecode();
    }

    @Override
    public byte[] getBytecode() {
        return delegate.getBytecode().toArray();
    }

    @Override
    public int getBytecodeSize() {
        return delegate.getBytecodeSize();
    }

    @Override
    public boolean hasMethodHandleIntrinsicName() {
        return delegate.hasMethodHandleIntrinsicName();
    }

    @Override
    public String getMethodHandleIntrinsicName() {
        return delegate.getMethodHandleIntrinsicName().toString();
    }

    @Override
    public SnapshotStructList.Loader<PersistedAnnotationData.Loader> getAnnotationList() {
        return wrapStructListLoader(delegate.getAnnotationList(), CapnProtoPersistedAnnotationData::loader);
    }

    @Override
    public boolean getIsVirtualRootMethod() {
        return delegate.getIsVirtualRootMethod();
    }

    @Override
    public boolean getIsDirectRootMethod() {
        return delegate.getIsDirectRootMethod();
    }

    @Override
    public boolean getIsInvoked() {
        return delegate.getIsInvoked();
    }

    @Override
    public boolean getIsImplementationInvoked() {
        return delegate.getIsImplementationInvoked();
    }

    @Override
    public boolean getIsIntrinsicMethod() {
        return delegate.getIsIntrinsicMethod();
    }

    @Override
    public byte getCompilationBehaviorOrdinal() {
        return delegate.getCompilationBehaviorOrdinal();
    }

    @Override
    public boolean hasAnalysisGraphLocation() {
        return delegate.hasAnalysisGraphLocation();
    }

    @Override
    public String getAnalysisGraphLocation() {
        return delegate.getAnalysisGraphLocation().toString();
    }

    @Override
    public boolean getAnalysisGraphIsIntrinsic() {
        return delegate.getAnalysisGraphIsIntrinsic();
    }

    @Override
    public boolean hasStrengthenedGraphLocation() {
        return delegate.hasStrengthenedGraphLocation();
    }

    @Override
    public String getStrengthenedGraphLocation() {
        return delegate.getStrengthenedGraphLocation().toString();
    }

    @Override
    public PersistedAnalysisMethodData.WrappedMethod.Loader getWrappedMethod() {
        return new WrappedMethodLoaderAdapter(delegate.getWrappedMethod());
    }

    @Override
    public boolean hasClosedFieldWrites() {
        return delegate.hasClosedFieldWrites();
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getClosedFieldWrites() {
        return wrapIntListLoader(delegate.getClosedFieldWrites());
    }

    @Override
    public int getHostedMethodIndex() {
        return delegate.getHostedMethodIndex();
    }
}

record WrappedMethodWriterAdapter(WrappedMethod.Builder delegate) implements PersistedAnalysisMethodData.WrappedMethod.Writer {
    @Override
    public PersistedAnalysisMethodData.WrappedMethod.FactoryMethod.Writer initFactoryMethod() {
        return new FactoryMethodWriterAdapter(delegate.initFactoryMethod());
    }

    @Override
    public PersistedAnalysisMethodData.WrappedMethod.CEntryPointCallStub.Writer initCEntryPointCallStub() {
        return new CEntryPointCallStubWriterAdapter(delegate.initCEntryPointCallStub());
    }

    @Override
    public PersistedAnalysisMethodData.WrappedMethod.WrappedMember.Writer initWrappedMember() {
        return new WrappedMemberWriterAdapter(delegate.initWrappedMember());
    }

    @Override
    public PersistedAnalysisMethodData.WrappedMethod.PolymorphicSignature.Writer initPolymorphicSignature() {
        return new PolymorphicSignatureWriterAdapter(delegate.initPolymorphicSignature());
    }

    @Override
    public PersistedAnalysisMethodData.WrappedMethod.OutlinedSB.Writer initOutlinedSB() {
        return new OutlinedSBWriterAdapter(delegate.initOutlinedSB());
    }
}

record WrappedMethodLoaderAdapter(WrappedMethod.Reader delegate) implements PersistedAnalysisMethodData.WrappedMethod.Loader {
    @Override
    public boolean isNone() {
        return delegate.isNone();
    }

    @Override
    public boolean isFactoryMethod() {
        return delegate.isFactoryMethod();
    }

    @Override
    public PersistedAnalysisMethodData.WrappedMethod.FactoryMethod.Loader getFactoryMethod() {
        return new FactoryMethodLoaderAdapter(delegate.getFactoryMethod());
    }

    @Override
    public boolean isCEntryPointCallStub() {
        return delegate.isCEntryPointCallStub();
    }

    @Override
    public PersistedAnalysisMethodData.WrappedMethod.CEntryPointCallStub.Loader getCEntryPointCallStub() {
        return new CEntryPointCallStubLoaderAdapter(delegate.getCEntryPointCallStub());
    }

    @Override
    public boolean isWrappedMember() {
        return delegate.isWrappedMember();
    }

    @Override
    public PersistedAnalysisMethodData.WrappedMethod.WrappedMember.Loader getWrappedMember() {
        return new WrappedMemberLoaderAdapter(delegate.getWrappedMember());
    }

    @Override
    public boolean isPolymorphicSignature() {
        return delegate.isPolymorphicSignature();
    }

    @Override
    public PersistedAnalysisMethodData.WrappedMethod.PolymorphicSignature.Loader getPolymorphicSignature() {
        return new PolymorphicSignatureLoaderAdapter(delegate.getPolymorphicSignature());
    }

    @Override
    public boolean isOutlinedSB() {
        return delegate.isOutlinedSB();
    }

    @Override
    public PersistedAnalysisMethodData.WrappedMethod.OutlinedSB.Loader getOutlinedSB() {
        return new OutlinedSBLoaderAdapter(delegate.getOutlinedSB());
    }
}

record FactoryMethodWriterAdapter(WrappedMethod.FactoryMethod.Builder delegate) implements PersistedAnalysisMethodData.WrappedMethod.FactoryMethod.Writer {
    @Override
    public void setTargetConstructorId(int value) {
        delegate.setTargetConstructorId(value);
    }

    @Override
    public void setThrowAllocatedObject(boolean value) {
        delegate.setThrowAllocatedObject(value);
    }

    @Override
    public void setInstantiatedTypeId(int value) {
        delegate.setInstantiatedTypeId(value);
    }
}

record FactoryMethodLoaderAdapter(WrappedMethod.FactoryMethod.Reader delegate) implements PersistedAnalysisMethodData.WrappedMethod.FactoryMethod.Loader {
    @Override
    public int getTargetConstructorId() {
        return delegate.getTargetConstructorId();
    }

    @Override
    public boolean getThrowAllocatedObject() {
        return delegate.getThrowAllocatedObject();
    }

    @Override
    public int getInstantiatedTypeId() {
        return delegate.getInstantiatedTypeId();
    }
}

record CEntryPointCallStubWriterAdapter(WrappedMethod.CEntryPointCallStub.Builder delegate) implements PersistedAnalysisMethodData.WrappedMethod.CEntryPointCallStub.Writer {
    @Override
    public void setOriginalMethodId(int value) {
        delegate.setOriginalMethodId(value);
    }

    @Override
    public void setNotPublished(boolean value) {
        delegate.setNotPublished(value);
    }
}

record CEntryPointCallStubLoaderAdapter(WrappedMethod.CEntryPointCallStub.Reader delegate) implements PersistedAnalysisMethodData.WrappedMethod.CEntryPointCallStub.Loader {
    @Override
    public int getOriginalMethodId() {
        return delegate.getOriginalMethodId();
    }

    @Override
    public boolean getNotPublished() {
        return delegate.getNotPublished();
    }
}

record WrappedMemberWriterAdapter(WrappedMethod.WrappedMember.Builder delegate) implements PersistedAnalysisMethodData.WrappedMethod.WrappedMember.Writer {
    @Override
    public void setReflectionExpandSignature() {
        delegate.setReflectionExpandSignature(Void.VOID);
    }

    @Override
    public void setJavaCallVariantWrapper() {
        delegate.setJavaCallVariantWrapper(Void.VOID);
    }

    @Override
    public void setName(String value) {
        delegate.setName(value);
    }

    @Override
    public void setDeclaringClassName(String value) {
        delegate.setDeclaringClassName(value);
    }

    @Override
    public SnapshotStringList.Writer initArgumentTypeNames(int size) {
        return wrapStringListWriter(delegate.initArgumentTypeNames(size));
    }
}

record WrappedMemberLoaderAdapter(WrappedMethod.WrappedMember.Reader delegate) implements PersistedAnalysisMethodData.WrappedMethod.WrappedMember.Loader {
    @Override
    public boolean isReflectionExpandSignature() {
        return delegate.isReflectionExpandSignature();
    }

    @Override
    public boolean isJavaCallVariantWrapper() {
        return delegate.isJavaCallVariantWrapper();
    }

    @Override
    public String getName() {
        return delegate.getName().toString();
    }

    @Override
    public String getDeclaringClassName() {
        return delegate.getDeclaringClassName().toString();
    }

    @Override
    public SnapshotStringList.Loader getArgumentTypeNames() {
        return wrapStringListLoader(delegate.getArgumentTypeNames());
    }
}

record PolymorphicSignatureWriterAdapter(WrappedMethod.PolymorphicSignature.Builder delegate) implements PersistedAnalysisMethodData.WrappedMethod.PolymorphicSignature.Writer {
    @Override
    public SnapshotPrimitiveList.Int.Writer initCallers(int size) {
        return wrapIntListWriter(delegate.initCallers(size));
    }
}

record PolymorphicSignatureLoaderAdapter(WrappedMethod.PolymorphicSignature.Reader delegate) implements PersistedAnalysisMethodData.WrappedMethod.PolymorphicSignature.Loader {
    @Override
    public SnapshotPrimitiveList.Int.Loader getCallers() {
        return wrapIntListLoader(delegate.getCallers());
    }
}

record OutlinedSBWriterAdapter(WrappedMethod.OutlinedSB.Builder delegate) implements PersistedAnalysisMethodData.WrappedMethod.OutlinedSB.Writer {
    @Override
    public SnapshotStringList.Writer initMethodTypeParameters(int size) {
        return wrapStringListWriter(delegate.initMethodTypeParameters(size));
    }

    @Override
    public void setMethodTypeReturn(String value) {
        delegate.setMethodTypeReturn(value);
    }
}

record OutlinedSBLoaderAdapter(WrappedMethod.OutlinedSB.Reader delegate) implements PersistedAnalysisMethodData.WrappedMethod.OutlinedSB.Loader {
    @Override
    public SnapshotStringList.Loader getMethodTypeParameters() {
        return wrapStringListLoader(delegate.getMethodTypeParameters());
    }

    @Override
    public String getMethodTypeReturn() {
        return delegate.getMethodTypeReturn().toString();
    }
}
