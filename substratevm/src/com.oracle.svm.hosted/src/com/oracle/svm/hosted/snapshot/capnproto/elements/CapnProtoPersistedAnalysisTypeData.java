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
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapStructListWriter;

import com.oracle.svm.hosted.snapshot.capnproto.dynamichub.CapnProtoClassInitializationInfoData;
import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType;
import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType.WrappedType;
import com.oracle.svm.hosted.snapshot.dynamichub.ClassInitializationInfoData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisTypeData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnnotationData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;
import com.oracle.svm.shaded.org.capnproto.Void;

public final class CapnProtoPersistedAnalysisTypeData {
    public static PersistedAnalysisTypeData.Writer writer(PersistedAnalysisType.Builder delegate) {
        return new PersistedAnalysisTypeWriterAdapter(delegate);
    }

    public static PersistedAnalysisTypeData.Loader loader(PersistedAnalysisType.Reader delegate) {
        return new PersistedAnalysisTypeLoaderAdapter(delegate);
    }
}

record PersistedAnalysisTypeWriterAdapter(PersistedAnalysisType.Builder delegate) implements PersistedAnalysisTypeData.Writer {
    @Override
    public void setHubIdentityHashCode(int value) {
        delegate.setHubIdentityHashCode(value);
    }

    @Override
    public void setHasArrayType(boolean value) {
        delegate.setHasArrayType(value);
    }

    @Override
    public void setHasClassInitInfo(boolean value) {
        delegate.setHasClassInitInfo(value);
    }

    @Override
    public ClassInitializationInfoData.Writer initClassInitializationInfo() {
        return CapnProtoClassInitializationInfoData.writer(delegate.initClassInitializationInfo());
    }

    @Override
    public void setId(int value) {
        delegate.setId(value);
    }

    @Override
    public void setDescriptor(String value) {
        delegate.setDescriptor(value);
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initFields(int size) {
        return wrapIntListWriter(delegate.initFields(size));
    }

    @Override
    public void setClassJavaName(String value) {
        delegate.setClassJavaName(value);
    }

    @Override
    public void setClassName(String value) {
        delegate.setClassName(value);
    }

    @Override
    public void setModifiers(int value) {
        delegate.setModifiers(value);
    }

    @Override
    public void setIsInterface(boolean value) {
        delegate.setIsInterface(value);
    }

    @Override
    public void setIsEnum(boolean value) {
        delegate.setIsEnum(value);
    }

    @Override
    public void setIsRecord(boolean value) {
        delegate.setIsRecord(value);
    }

    @Override
    public void setIsInitialized(boolean value) {
        delegate.setIsInitialized(value);
    }

    @Override
    public void setIsSuccessfulSimulation(boolean value) {
        delegate.setIsSuccessfulSimulation(value);
    }

    @Override
    public void setIsFailedSimulation(boolean value) {
        delegate.setIsFailedSimulation(value);
    }

    @Override
    public void setIsFailedInitialization(boolean value) {
        delegate.setIsFailedInitialization(value);
    }

    @Override
    public void setIsLinked(boolean value) {
        delegate.setIsLinked(value);
    }

    @Override
    public void setSourceFileName(String value) {
        delegate.setSourceFileName(value);
    }

    @Override
    public void setEnclosingTypeId(int value) {
        delegate.setEnclosingTypeId(value);
    }

    @Override
    public void setComponentTypeId(int value) {
        delegate.setComponentTypeId(value);
    }

    @Override
    public void setSuperClassTypeId(int value) {
        delegate.setSuperClassTypeId(value);
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initInterfaces(int size) {
        return wrapIntListWriter(delegate.initInterfaces(size));
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initInstanceFieldIds(int size) {
        return wrapIntListWriter(delegate.initInstanceFieldIds(size));
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initInstanceFieldIdsWithSuper(int size) {
        return wrapIntListWriter(delegate.initInstanceFieldIdsWithSuper(size));
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initStaticFieldIds(int size) {
        return wrapIntListWriter(delegate.initStaticFieldIds(size));
    }

    @Override
    public SnapshotStructList.Writer<PersistedAnnotationData.Writer> initAnnotationList(int size) {
        return wrapStructListWriter(delegate.initAnnotationList(size), CapnProtoPersistedAnnotationData::writer);
    }

    @Override
    public void setIsInstantiated(boolean value) {
        delegate.setIsInstantiated(value);
    }

    @Override
    public void setIsUnsafeAllocated(boolean value) {
        delegate.setIsUnsafeAllocated(value);
    }

    @Override
    public void setIsReachable(boolean value) {
        delegate.setIsReachable(value);
    }

    @Override
    public PersistedAnalysisTypeData.WrappedType.Writer getWrappedType() {
        return new WrappedTypeWriterAdapter(delegate.getWrappedType());
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initSubTypes(int size) {
        return wrapIntListWriter(delegate.initSubTypes(size));
    }

    @Override
    public void setIsAnySubtypeInstantiated(boolean value) {
        delegate.setIsAnySubtypeInstantiated(value);
    }
}

record PersistedAnalysisTypeLoaderAdapter(PersistedAnalysisType.Reader delegate) implements PersistedAnalysisTypeData.Loader {
    @Override
    public int getHubIdentityHashCode() {
        return delegate.getHubIdentityHashCode();
    }

    @Override
    public boolean getHasArrayType() {
        return delegate.getHasArrayType();
    }

    @Override
    public boolean getHasClassInitInfo() {
        return delegate.getHasClassInitInfo();
    }

    @Override
    public ClassInitializationInfoData.Loader getClassInitializationInfo() {
        return CapnProtoClassInitializationInfoData.loader(delegate.getClassInitializationInfo());
    }

    @Override
    public int getId() {
        return delegate.getId();
    }

    @Override
    public String getDescriptor() {
        return delegate.getDescriptor().toString();
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getFields() {
        return wrapIntListLoader(delegate.getFields());
    }

    @Override
    public String getClassJavaName() {
        return delegate.getClassJavaName().toString();
    }

    @Override
    public String getClassName() {
        return delegate.getClassName().toString();
    }

    @Override
    public int getModifiers() {
        return delegate.getModifiers();
    }

    @Override
    public boolean getIsInterface() {
        return delegate.getIsInterface();
    }

    @Override
    public boolean getIsEnum() {
        return delegate.getIsEnum();
    }

    @Override
    public boolean getIsRecord() {
        return delegate.getIsRecord();
    }

    @Override
    public boolean getIsInitialized() {
        return delegate.getIsInitialized();
    }

    @Override
    public boolean getIsSuccessfulSimulation() {
        return delegate.getIsSuccessfulSimulation();
    }

    @Override
    public boolean getIsFailedSimulation() {
        return delegate.getIsFailedSimulation();
    }

    @Override
    public boolean getIsFailedInitialization() {
        return delegate.getIsFailedInitialization();
    }

    @Override
    public boolean getIsLinked() {
        return delegate.getIsLinked();
    }

    @Override
    public boolean hasSourceFileName() {
        return delegate.hasSourceFileName();
    }

    @Override
    public String getSourceFileName() {
        return delegate.getSourceFileName().toString();
    }

    @Override
    public int getEnclosingTypeId() {
        return delegate.getEnclosingTypeId();
    }

    @Override
    public int getComponentTypeId() {
        return delegate.getComponentTypeId();
    }

    @Override
    public int getSuperClassTypeId() {
        return delegate.getSuperClassTypeId();
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getInterfaces() {
        return wrapIntListLoader(delegate.getInterfaces());
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getInstanceFieldIds() {
        return wrapIntListLoader(delegate.getInstanceFieldIds());
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getInstanceFieldIdsWithSuper() {
        return wrapIntListLoader(delegate.getInstanceFieldIdsWithSuper());
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getStaticFieldIds() {
        return wrapIntListLoader(delegate.getStaticFieldIds());
    }

    @Override
    public SnapshotStructList.Loader<PersistedAnnotationData.Loader> getAnnotationList() {
        return wrapStructListLoader(delegate.getAnnotationList(), CapnProtoPersistedAnnotationData::loader);
    }

    @Override
    public boolean getIsInstantiated() {
        return delegate.getIsInstantiated();
    }

    @Override
    public boolean getIsUnsafeAllocated() {
        return delegate.getIsUnsafeAllocated();
    }

    @Override
    public boolean getIsReachable() {
        return delegate.getIsReachable();
    }

    @Override
    public PersistedAnalysisTypeData.WrappedType.Loader getWrappedType() {
        return new WrappedTypeLoaderAdapter(delegate.getWrappedType());
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getSubTypes() {
        return wrapIntListLoader(delegate.getSubTypes());
    }

    @Override
    public boolean getIsAnySubtypeInstantiated() {
        return delegate.getIsAnySubtypeInstantiated();
    }
}

record WrappedTypeWriterAdapter(WrappedType.Builder delegate) implements PersistedAnalysisTypeData.WrappedType.Writer {
    @Override
    public PersistedAnalysisTypeData.WrappedType.SerializationGenerated.Writer initSerializationGenerated() {
        return new SerializationGeneratedWriterAdapter(delegate.initSerializationGenerated());
    }

    @Override
    public PersistedAnalysisTypeData.WrappedType.Lambda.Writer initLambda() {
        return new LambdaWriterAdapter(delegate.initLambda());
    }

    @Override
    public void setProxyType() {
        delegate.setProxyType(Void.VOID);
    }
}

record WrappedTypeLoaderAdapter(WrappedType.Reader delegate) implements PersistedAnalysisTypeData.WrappedType.Loader {
    @Override
    public boolean isNone() {
        return delegate.isNone();
    }

    @Override
    public boolean isSerializationGenerated() {
        return delegate.isSerializationGenerated();
    }

    @Override
    public PersistedAnalysisTypeData.WrappedType.SerializationGenerated.Loader getSerializationGenerated() {
        return new SerializationGeneratedLoaderAdapter(delegate.getSerializationGenerated());
    }

    @Override
    public boolean isLambda() {
        return delegate.isLambda();
    }

    @Override
    public PersistedAnalysisTypeData.WrappedType.Lambda.Loader getLambda() {
        return new LambdaLoaderAdapter(delegate.getLambda());
    }

    @Override
    public boolean isProxyType() {
        return delegate.isProxyType();
    }
}

record SerializationGeneratedWriterAdapter(WrappedType.SerializationGenerated.Builder delegate) implements PersistedAnalysisTypeData.WrappedType.SerializationGenerated.Writer {
    @Override
    public void setRawDeclaringClassId(int value) {
        delegate.setRawDeclaringClassId(value);
    }

    @Override
    public void setRawTargetConstructorId(int value) {
        delegate.setRawTargetConstructorId(value);
    }
}

record SerializationGeneratedLoaderAdapter(WrappedType.SerializationGenerated.Reader delegate) implements PersistedAnalysisTypeData.WrappedType.SerializationGenerated.Loader {
    @Override
    public int getRawDeclaringClassId() {
        return delegate.getRawDeclaringClassId();
    }

    @Override
    public int getRawTargetConstructorId() {
        return delegate.getRawTargetConstructorId();
    }
}

record LambdaWriterAdapter(WrappedType.Lambda.Builder delegate) implements PersistedAnalysisTypeData.WrappedType.Lambda.Writer {
    @Override
    public void setCapturingClass(String value) {
        delegate.setCapturingClass(value);
    }

    @Override
    public void setCaptureSite(String value) {
        delegate.setCaptureSite(value);
    }
}

record LambdaLoaderAdapter(WrappedType.Lambda.Reader delegate) implements PersistedAnalysisTypeData.WrappedType.Lambda.Loader {
    @Override
    public String getCapturingClass() {
        return delegate.getCapturingClass().toString();
    }

    @Override
    public String getCaptureSite() {
        return delegate.getCaptureSite().toString();
    }
}
