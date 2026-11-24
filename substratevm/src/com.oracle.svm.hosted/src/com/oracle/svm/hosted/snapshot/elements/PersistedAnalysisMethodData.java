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

import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;

/** Persisted representation of an analysis method. */
public interface PersistedAnalysisMethodData {
    interface Writer {
        SnapshotStringList.Writer initArgumentClassNames(int size);

        void setClassName(String value);

        void setDescriptor(String value);

        void setDeclaringTypeId(int value);

        SnapshotPrimitiveList.Int.Writer initArgumentTypeIds(int size);

        void setId(int value);

        void setName(String value);

        void setReturnTypeId(int value);

        void setIsVarArgs(boolean value);

        void setIsBridge(boolean value);

        void setCanBeStaticallyBound(boolean value);

        void setModifiers(int value);

        void setIsConstructor(boolean value);

        void setIsSynthetic(boolean value);

        void setIsDeclared(boolean value);

        void setBytecode(byte[] value);

        void setBytecodeSize(int value);

        void setMethodHandleIntrinsicName(String value);

        SnapshotStructList.Writer<PersistedAnnotationData.Writer> initAnnotationList(int size);

        void setIsVirtualRootMethod(boolean value);

        void setIsDirectRootMethod(boolean value);

        void setIsInvoked(boolean value);

        void setIsImplementationInvoked(boolean value);

        void setIsIntrinsicMethod(boolean value);

        void setCompilationBehaviorOrdinal(byte value);

        void setAnalysisGraphLocation(String value);

        void setAnalysisGraphIsIntrinsic(boolean value);

        SnapshotPrimitiveList.Int.Writer initClosedFieldWrites(int size);

        void setStrengthenedGraphLocation(String value);

        WrappedMethod.Writer getWrappedMethod();

        void setHostedMethodIndex(int value);
    }

    interface Loader {
        boolean hasArgumentClassNames();

        SnapshotStringList.Loader getArgumentClassNames();

        boolean hasClassName();

        String getClassName();

        String getDescriptor();

        int getDeclaringTypeId();

        SnapshotPrimitiveList.Int.Loader getArgumentTypeIds();

        int getId();

        String getName();

        int getReturnTypeId();

        boolean getIsVarArgs();

        boolean getIsBridge();

        boolean getCanBeStaticallyBound();

        int getModifiers();

        boolean getIsConstructor();

        boolean getIsSynthetic();

        boolean getIsDeclared();

        boolean hasBytecode();

        byte[] getBytecode();

        int getBytecodeSize();

        boolean hasMethodHandleIntrinsicName();

        String getMethodHandleIntrinsicName();

        SnapshotStructList.Loader<PersistedAnnotationData.Loader> getAnnotationList();

        boolean getIsVirtualRootMethod();

        boolean getIsDirectRootMethod();

        boolean getIsInvoked();

        boolean getIsImplementationInvoked();

        boolean getIsIntrinsicMethod();

        byte getCompilationBehaviorOrdinal();

        boolean hasAnalysisGraphLocation();

        String getAnalysisGraphLocation();

        boolean getAnalysisGraphIsIntrinsic();

        boolean hasClosedFieldWrites();

        SnapshotPrimitiveList.Int.Loader getClosedFieldWrites();

        boolean hasStrengthenedGraphLocation();

        String getStrengthenedGraphLocation();

        WrappedMethod.Loader getWrappedMethod();

        int getHostedMethodIndex();
    }

    interface WrappedMethod {
        interface Writer {
            FactoryMethod.Writer initFactoryMethod();

            CEntryPointCallStub.Writer initCEntryPointCallStub();

            WrappedMember.Writer initWrappedMember();

            PolymorphicSignature.Writer initPolymorphicSignature();

            OutlinedSB.Writer initOutlinedSB();
        }

        interface Loader {
            boolean isNone();

            boolean isFactoryMethod();

            FactoryMethod.Loader getFactoryMethod();

            boolean isCEntryPointCallStub();

            CEntryPointCallStub.Loader getCEntryPointCallStub();

            boolean isWrappedMember();

            WrappedMember.Loader getWrappedMember();

            boolean isPolymorphicSignature();

            PolymorphicSignature.Loader getPolymorphicSignature();

            boolean isOutlinedSB();

            OutlinedSB.Loader getOutlinedSB();
        }

        interface FactoryMethod {
            interface Writer {
                void setTargetConstructorId(int value);

                void setThrowAllocatedObject(boolean value);

                void setInstantiatedTypeId(int value);
            }

            interface Loader {
                int getTargetConstructorId();

                boolean getThrowAllocatedObject();

                int getInstantiatedTypeId();
            }
        }

        interface CEntryPointCallStub {
            interface Writer {
                void setOriginalMethodId(int value);

                void setNotPublished(boolean value);
            }

            interface Loader {
                int getOriginalMethodId();

                boolean getNotPublished();
            }
        }

        interface WrappedMember {
            interface Writer {
                void setReflectionExpandSignature();

                void setJavaCallVariantWrapper();

                void setName(String value);

                void setDeclaringClassName(String value);

                SnapshotStringList.Writer initArgumentTypeNames(int size);
            }

            interface Loader {
                boolean isReflectionExpandSignature();

                boolean isJavaCallVariantWrapper();

                String getName();

                String getDeclaringClassName();

                SnapshotStringList.Loader getArgumentTypeNames();
            }
        }

        interface PolymorphicSignature {
            interface Writer {
                SnapshotPrimitiveList.Int.Writer initCallers(int size);
            }

            interface Loader {
                SnapshotPrimitiveList.Int.Loader getCallers();
            }
        }

        interface OutlinedSB {
            interface Writer {
                SnapshotStringList.Writer initMethodTypeParameters(int size);

                void setMethodTypeReturn(String value);
            }

            interface Loader {
                SnapshotStringList.Loader getMethodTypeParameters();

                String getMethodTypeReturn();
            }
        }
    }
}
