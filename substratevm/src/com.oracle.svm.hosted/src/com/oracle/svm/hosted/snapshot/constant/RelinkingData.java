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
package com.oracle.svm.hosted.snapshot.constant;

import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;

/**
 * Relinking metadata for persisted object constants.
 * <p>
 * This metadata records how such objects can be retrieved in extension layers, so their shared
 * layer constant can have a backing hosted object.
 */
public interface RelinkingData {
    interface Writer {
        ClassConstant.Writer initClassConstant();

        StringConstant.Writer initStringConstant();

        EnumConstant.Writer initEnumConstant();

        FieldConstant.Writer initFieldConstant();

        MethodTypeConstant.Writer initMethodTypeConstant();
    }

    interface Loader {
        boolean isNotRelinked();

        boolean isClassConstant();

        ClassConstant.Loader getClassConstant();

        boolean isStringConstant();

        StringConstant.Loader getStringConstant();

        boolean isEnumConstant();

        EnumConstant.Loader getEnumConstant();

        boolean isFieldConstant();

        FieldConstant.Loader getFieldConstant();

        boolean isMethodTypeConstant();

        MethodTypeConstant.Loader getMethodTypeConstant();
    }

    interface ClassConstant {
        interface Writer {
            void setTypeId(int value);
        }

        interface Loader {
            int getTypeId();
        }
    }

    interface StringConstant {
        interface Writer {
            void setValue(String value);
        }

        interface Loader {
            boolean hasValue();

            String getValue();
        }
    }

    interface EnumConstant {
        interface Writer {
            void setEnumClass(String value);

            void setEnumName(String value);
        }

        interface Loader {
            String getEnumClass();

            String getEnumName();
        }
    }

    interface FieldConstant {
        interface Writer {
            void setOriginFieldId(int value);

            void setRequiresLateLoading(boolean value);
        }

        interface Loader {
            int getOriginFieldId();

            boolean getRequiresLateLoading();
        }
    }

    interface MethodTypeConstant {
        interface Writer {
            void setReturnTypeId(int value);

            SnapshotPrimitiveList.Int.Writer initParameterTypeIds(int size);
        }

        interface Loader {
            int getReturnTypeId();

            SnapshotPrimitiveList.Int.Loader getParameterTypeIds();
        }
    }
}
