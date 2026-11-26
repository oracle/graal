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

import com.oracle.svm.hosted.snapshot.capnproto.constant.CapnProtoConstantReferenceData;
import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisField;
import com.oracle.svm.hosted.snapshot.constant.ConstantReferenceData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisFieldData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnnotationData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

import jdk.vm.ci.meta.JavaKind;

public final class CapnProtoPersistedAnalysisFieldData {
    public static PersistedAnalysisFieldData.Writer writer(PersistedAnalysisField.Builder delegate) {
        return new PersistedAnalysisFieldWriterAdapter(delegate);
    }

    public static PersistedAnalysisFieldData.Loader loader(PersistedAnalysisField.Reader delegate) {
        return new PersistedAnalysisFieldLoaderAdapter(delegate);
    }
}

record PersistedAnalysisFieldWriterAdapter(PersistedAnalysisField.Builder delegate) implements PersistedAnalysisFieldData.Writer {
    @Override
    public void setId(int value) {
        delegate.setId(value);
    }

    @Override
    public void setDeclaringTypeId(int value) {
        delegate.setDeclaringTypeId(value);
    }

    @Override
    public void setName(String value) {
        delegate.setName(value);
    }

    @Override
    public void setStorageKind(JavaKind value) {
        delegate.setStorageKind((byte) value.getTypeChar());
    }

    @Override
    public void setIsAccessed(boolean value) {
        delegate.setIsAccessed(value);
    }

    @Override
    public void setIsRead(boolean value) {
        delegate.setIsRead(value);
    }

    @Override
    public void setIsWritten(boolean value) {
        delegate.setIsWritten(value);
    }

    @Override
    public void setIsFolded(boolean value) {
        delegate.setIsFolded(value);
    }

    @Override
    public void setIsUnsafeAccessed(boolean value) {
        delegate.setIsUnsafeAccessed(value);
    }

    @Override
    public void setIsStatic(boolean value) {
        delegate.setIsStatic(value);
    }

    @Override
    public void setIsInternal(boolean value) {
        delegate.setIsInternal(value);
    }

    @Override
    public void setIsSynthetic(boolean value) {
        delegate.setIsSynthetic(value);
    }

    @Override
    public void setTypeId(int value) {
        delegate.setTypeId(value);
    }

    @Override
    public void setModifiers(int value) {
        delegate.setModifiers(value);
    }

    @Override
    public void setPosition(int value) {
        delegate.setPosition(value);
    }

    @Override
    public void setLocation(int value) {
        delegate.setLocation(value);
    }

    @Override
    public void setPriorInstalledLayerNum(int value) {
        delegate.setPriorInstalledLayerNum(value);
    }

    @Override
    public void setAssignmentStatus(int value) {
        delegate.setAssignmentStatus(value);
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initUpdatableReceivers(int size) {
        return wrapIntListWriter(delegate.initUpdatableReceivers(size));
    }

    @Override
    public SnapshotStructList.Writer<PersistedAnnotationData.Writer> initAnnotationList(int size) {
        return wrapStructListWriter(delegate.initAnnotationList(size), CapnProtoPersistedAnnotationData::writer);
    }

    @Override
    public ConstantReferenceData.Writer initSimulatedFieldValue() {
        return CapnProtoConstantReferenceData.writer(delegate.initSimulatedFieldValue());
    }
}

record PersistedAnalysisFieldLoaderAdapter(PersistedAnalysisField.Reader delegate) implements PersistedAnalysisFieldData.Loader {
    @Override
    public int getId() {
        return delegate.getId();
    }

    @Override
    public int getDeclaringTypeId() {
        return delegate.getDeclaringTypeId();
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
    public String getName() {
        return delegate.getName().toString();
    }

    @Override
    public JavaKind getStorageKind() {
        char typeChar = (char) delegate.getStorageKind();
        if (typeChar == JavaKind.Object.getTypeChar()) {
            return JavaKind.Object;
        }
        if (typeChar == 0 || typeChar == JavaKind.Illegal.getTypeChar()) {
            return JavaKind.Illegal;
        }
        return JavaKind.fromPrimitiveOrVoidTypeChar(typeChar);
    }

    @Override
    public boolean getIsAccessed() {
        return delegate.getIsAccessed();
    }

    @Override
    public boolean getIsRead() {
        return delegate.getIsRead();
    }

    @Override
    public boolean getIsWritten() {
        return delegate.getIsWritten();
    }

    @Override
    public boolean getIsFolded() {
        return delegate.getIsFolded();
    }

    @Override
    public boolean getIsUnsafeAccessed() {
        return delegate.getIsUnsafeAccessed();
    }

    @Override
    public boolean getIsStatic() {
        return delegate.getIsStatic();
    }

    @Override
    public boolean getIsInternal() {
        return delegate.getIsInternal();
    }

    @Override
    public boolean getIsSynthetic() {
        return delegate.getIsSynthetic();
    }

    @Override
    public int getTypeId() {
        return delegate.getTypeId();
    }

    @Override
    public int getModifiers() {
        return delegate.getModifiers();
    }

    @Override
    public int getPosition() {
        return delegate.getPosition();
    }

    @Override
    public int getLocation() {
        return delegate.getLocation();
    }

    @Override
    public int getPriorInstalledLayerNum() {
        return delegate.getPriorInstalledLayerNum();
    }

    @Override
    public int getAssignmentStatus() {
        return delegate.getAssignmentStatus();
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getUpdatableReceivers() {
        return wrapIntListLoader(delegate.getUpdatableReceivers());
    }

    @Override
    public SnapshotStructList.Loader<PersistedAnnotationData.Loader> getAnnotationList() {
        return wrapStructListLoader(delegate.getAnnotationList(), CapnProtoPersistedAnnotationData::loader);
    }

    @Override
    public ConstantReferenceData.Loader getSimulatedFieldValue() {
        return CapnProtoConstantReferenceData.loader(delegate.getSimulatedFieldValue());
    }

    @Override
    public boolean hasSimulatedFieldValue() {
        return delegate.hasSimulatedFieldValue();
    }
}
