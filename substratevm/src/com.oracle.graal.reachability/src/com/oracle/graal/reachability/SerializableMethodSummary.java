/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.reachability;

import com.oracle.graal.reachability.summaries.MethodHash;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public class SerializableMethodSummary implements Serializable {

    public SerializableMethodSummary(MethodHash hash, MethodId[] invokedMethods, MethodId[] implementationInvokedMethods, ClassId[] accessedTypes, ClassId[] instantiatedTypes, FieldId[] readFields,
                    FieldId[] writtenFields) {
        this.hash = hash;
        this.invokedMethods = invokedMethods;
        this.implementationInvokedMethods = implementationInvokedMethods;
        this.accessedTypes = accessedTypes;
        this.instantiatedTypes = instantiatedTypes;
        this.readFields = readFields;
        this.writtenFields = writtenFields;
    }

    public static class ClassId implements Serializable {
        public final String className;

        public ClassId(String className) {
            this.className = className;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClassId classId = (ClassId) o;
            return Objects.equals(className, classId.className);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className);
        }

        @Override
        public String toString() {
            return "ClassId{" +
                            "className='" + className + '\'' +
                            '}';
        }
    }

    public static class MethodId implements Serializable {
        public final ClassId classId;
        public final String methodName;

        public MethodId(ClassId classId, String methodName) {
            this.classId = classId;
            this.methodName = methodName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            MethodId methodId = (MethodId) o;
            return Objects.equals(classId, methodId.classId) && Objects.equals(methodName, methodId.methodName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(classId, methodName);
        }

        @Override
        public String toString() {
            return "MethodId{" +
                            "classId=" + classId +
                            ", methodName='" + methodName + '\'' +
                            '}';
        }
    }

    public static class FieldId implements Serializable {
        public final ClassId classId;
        public final String fieldName;

        public FieldId(ClassId classId, String fieldName) {
            this.classId = classId;
            this.fieldName = fieldName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            FieldId fieldId = (FieldId) o;
            return Objects.equals(classId, fieldId.classId) && Objects.equals(fieldName, fieldId.fieldName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(classId, fieldName);
        }

        @Override
        public String toString() {
            return "FieldId{" +
                            "classId=" + classId +
                            ", fieldName='" + fieldName + '\'' +
                            '}';
        }
    }

    public final MethodHash hash;
    public final MethodId[] invokedMethods;
    public final MethodId[] implementationInvokedMethods;
    public final ClassId[] accessedTypes;
    public final ClassId[] instantiatedTypes;
    public final FieldId[] readFields;
    public final FieldId[] writtenFields;

    @Override
    public String toString() {
        return "SerializableMethodSummary{" +
                        "hash=" + hash +
                        ", invokedMethods=" + Arrays.toString(invokedMethods) +
                        ", implementationInvokedMethods=" + Arrays.toString(implementationInvokedMethods) +
                        ", accessedTypes=" + Arrays.toString(accessedTypes) +
                        ", instantiatedTypes=" + Arrays.toString(instantiatedTypes) +
                        ", readFields=" + Arrays.toString(readFields) +
                        ", writtenFields=" + Arrays.toString(writtenFields) +
                        '}';
    }
}
