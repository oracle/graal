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

import com.oracle.svm.hosted.snapshot.dynamichub.ClassInitializationInfoData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

/** Persisted representation of an analysis type. */
public interface PersistedAnalysisTypeData {
    interface Writer {
        void setHubIdentityHashCode(int value);

        void setHasArrayType(boolean value);

        void setHasClassInitInfo(boolean value);

        ClassInitializationInfoData.Writer initClassInitializationInfo();

        void setId(int value);

        void setDescriptor(String value);

        SnapshotPrimitiveList.Int.Writer initFields(int size);

        void setClassJavaName(String value);

        void setClassName(String value);

        void setModifiers(int value);

        void setIsInterface(boolean value);

        void setIsEnum(boolean value);

        void setIsRecord(boolean value);

        void setIsInitialized(boolean value);

        void setIsSuccessfulSimulation(boolean value);

        void setIsFailedSimulation(boolean value);

        void setIsFailedInitialization(boolean value);

        void setIsLinked(boolean value);

        void setSourceFileName(String value);

        void setEnclosingTypeId(int value);

        void setComponentTypeId(int value);

        void setSuperClassTypeId(int value);

        SnapshotPrimitiveList.Int.Writer initInterfaces(int size);

        SnapshotPrimitiveList.Int.Writer initInstanceFieldIds(int size);

        SnapshotPrimitiveList.Int.Writer initInstanceFieldIdsWithSuper(int size);

        SnapshotPrimitiveList.Int.Writer initStaticFieldIds(int size);

        SnapshotStructList.Writer<PersistedAnnotationData.Writer> initAnnotationList(int size);

        void setIsInstantiated(boolean value);

        void setIsUnsafeAllocated(boolean value);

        void setIsReachable(boolean value);

        WrappedType.Writer getWrappedType();

        SnapshotPrimitiveList.Int.Writer initSubTypes(int size);

        void setIsAnySubtypeInstantiated(boolean value);
    }

    interface Loader {
        int getHubIdentityHashCode();

        boolean getHasArrayType();

        boolean getHasClassInitInfo();

        ClassInitializationInfoData.Loader getClassInitializationInfo();

        int getId();

        String getDescriptor();

        SnapshotPrimitiveList.Int.Loader getFields();

        String getClassJavaName();

        String getClassName();

        int getModifiers();

        boolean getIsInterface();

        boolean getIsEnum();

        boolean getIsRecord();

        boolean getIsInitialized();

        boolean getIsSuccessfulSimulation();

        boolean getIsFailedSimulation();

        boolean getIsFailedInitialization();

        boolean getIsLinked();

        boolean hasSourceFileName();

        String getSourceFileName();

        int getEnclosingTypeId();

        int getComponentTypeId();

        int getSuperClassTypeId();

        SnapshotPrimitiveList.Int.Loader getInterfaces();

        SnapshotPrimitiveList.Int.Loader getInstanceFieldIds();

        SnapshotPrimitiveList.Int.Loader getInstanceFieldIdsWithSuper();

        SnapshotPrimitiveList.Int.Loader getStaticFieldIds();

        SnapshotStructList.Loader<PersistedAnnotationData.Loader> getAnnotationList();

        boolean getIsInstantiated();

        boolean getIsUnsafeAllocated();

        boolean getIsReachable();

        WrappedType.Loader getWrappedType();

        SnapshotPrimitiveList.Int.Loader getSubTypes();

        boolean getIsAnySubtypeInstantiated();
    }

    interface WrappedType {
        interface Writer {
            SerializationGenerated.Writer initSerializationGenerated();

            Lambda.Writer initLambda();

            void setProxyType();
        }

        interface Loader {
            boolean isNone();

            boolean isSerializationGenerated();

            SerializationGenerated.Loader getSerializationGenerated();

            boolean isLambda();

            Lambda.Loader getLambda();

            boolean isProxyType();
        }

        interface SerializationGenerated {
            interface Writer {
                void setRawDeclaringClassId(int value);

                void setRawTargetConstructorId(int value);
            }

            interface Loader {
                int getRawDeclaringClassId();

                int getRawTargetConstructorId();
            }
        }

        interface Lambda {
            interface Writer {
                void setCapturingClass(String value);

                void setCaptureSite(String value);
            }

            interface Loader {
                String getCapturingClass();

                String getCaptureSite();
            }
        }
    }
}
