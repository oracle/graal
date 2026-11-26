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
package com.oracle.svm.hosted.snapshot.elements;

import com.oracle.svm.hosted.snapshot.constant.ConstantReferenceData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

import jdk.vm.ci.meta.JavaKind;

/** Persisted representation of an analysis field. */
public interface PersistedAnalysisFieldData {
    interface Writer {
        void setId(int value);

        void setDeclaringTypeId(int value);

        void setName(String value);

        void setIsAccessed(boolean value);

        void setStorageKind(JavaKind value);

        void setIsRead(boolean value);

        void setIsWritten(boolean value);

        void setIsFolded(boolean value);

        void setIsUnsafeAccessed(boolean value);

        void setIsStatic(boolean value);

        void setIsInternal(boolean value);

        void setIsSynthetic(boolean value);

        void setTypeId(int value);

        void setModifiers(int value);

        void setPosition(int value);

        void setLocation(int value);

        void setPriorInstalledLayerNum(int value);

        void setAssignmentStatus(int value);

        SnapshotPrimitiveList.Int.Writer initUpdatableReceivers(int size);

        SnapshotStructList.Writer<PersistedAnnotationData.Writer> initAnnotationList(int size);

        ConstantReferenceData.Writer initSimulatedFieldValue();
    }

    interface Loader {
        int getId();

        int getDeclaringTypeId();

        boolean hasClassName();

        String getClassName();

        String getName();

        boolean getIsAccessed();

        JavaKind getStorageKind();

        boolean getIsRead();

        boolean getIsWritten();

        boolean getIsFolded();

        boolean getIsUnsafeAccessed();

        boolean getIsStatic();

        boolean getIsInternal();

        boolean getIsSynthetic();

        int getTypeId();

        int getModifiers();

        int getPosition();

        int getLocation();

        int getPriorInstalledLayerNum();

        int getAssignmentStatus();

        SnapshotPrimitiveList.Int.Loader getUpdatableReceivers();

        SnapshotStructList.Loader<PersistedAnnotationData.Loader> getAnnotationList();

        ConstantReferenceData.Loader getSimulatedFieldValue();

        boolean hasSimulatedFieldValue();
    }
}
