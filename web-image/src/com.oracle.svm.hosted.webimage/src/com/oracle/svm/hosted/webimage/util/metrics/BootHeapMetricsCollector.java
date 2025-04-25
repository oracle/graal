/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.util.metrics;

import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.logging.LoggerScope;
import com.oracle.svm.hosted.webimage.metrickeys.BootHeapMetricKeys;
import com.oracle.svm.webimage.object.ObjectInspector;
import com.oracle.svm.webimage.type.TypeControl;

import jdk.vm.ci.common.JVMCIError;

/**
 * A utility class that is used to collect the Boot Image Heap statistics
 * ({@link BootHeapMetricKeys}) with the {@linkplain LoggerScope Logging API}.
 */
public class BootHeapMetricsCollector {
    /**
     * The constant types that can appear in the boot image heap.
     */
    private enum ConstantType {
        OBJECT,
        PRIMITIVE,
        ARRAY,
        STRING,
        METHOD_POINTER
    }

    private final LoggerScope scope;
    private final TypeControl typeControl;

    public BootHeapMetricsCollector(LoggerScope scope, TypeControl typeControl) {
        this.scope = scope;
        this.typeControl = typeControl;
        /*
         * We force the existence of all the metrics found in the BootHeapMetricKeys class, since it
         * may happen that some constant types may not appear in the image heap (eg. primitive
         * constants) and their metrics wouldn't appear in the scope.
         */
        MetricsUtil.forceMetricExistence(scope, BootHeapMetricKeys.class);
    }

    /**
     * A utility method used to report the boot image heap metrics for the specified constant type
     * to the currently used scope.
     */
    private void logConstant(ConstantType constantType, long constantSize) {
        scope.counter(BootHeapMetricKeys.JVM_CONSTANTS_SIZE).add(constantSize);
        if (constantType == ConstantType.OBJECT) {
            scope.counter(BootHeapMetricKeys.NUM_OBJECTS).increment();
            scope.counter(BootHeapMetricKeys.JVM_OBJECTS_SIZE).add(constantSize);
        } else if (constantType == ConstantType.ARRAY) {
            scope.counter(BootHeapMetricKeys.NUM_ARRAYS).increment();
            scope.counter(BootHeapMetricKeys.JVM_ARRAYS_SIZE).add(constantSize);
        } else if (constantType == ConstantType.PRIMITIVE) {
            scope.counter(BootHeapMetricKeys.NUM_PRIMITIVES).increment();
            scope.counter(BootHeapMetricKeys.JVM_PRIMITIVES_SIZE).add(constantSize);
        } else if (constantType == ConstantType.STRING) {
            scope.counter(BootHeapMetricKeys.NUM_STRINGS).increment();
            scope.counter(BootHeapMetricKeys.JVM_STRINGS_SIZE).add(constantSize);
        } else if (constantType == ConstantType.METHOD_POINTER) {
            scope.counter(BootHeapMetricKeys.NUM_METHOD_POINTERS).increment();
            scope.counter(BootHeapMetricKeys.JVM_METHOD_POINTERS_SIZE).add(constantSize);
        } else {
            assert false : "Unrecognized constant type" + constantType.name();
        }
    }

    /**
     * A method used to calculate and report the size of the specified constant object. The size is
     * calculated as the size of the instance.
     */
    public void constantObject(HostedInstanceClass constObjectType) {
        logConstant(ConstantType.OBJECT, constObjectType.getInstanceSize());
    }

    /**
     * A method used to calculate and report the size of the specified constant array. The size of
     * the array is calculated as array_index_scale * number_of_elements.
     */
    public void constantArray(ObjectInspector.ArrayType<?> arrayType) {
        HostedType elemType = arrayType.componentType;
        logConstant(ConstantType.ARRAY, (long) typeControl.getMetaAccess().getArrayIndexScale(elemType.getJavaKind()) * arrayType.length());
    }

    /**
     * Reports the size of the specified primitive type through the {@linkplain LoggerScope Logging
     * API}. The sizes of the primitive types are equal to the sizes of
     * <a href="https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html">the JVM
     * Primitive Data Types</a>.
     */
    public void constantPrimitive(ObjectInspector.ValueType valueType) {
        long primitiveSize;

        switch (valueType.kind) {
            case Boolean:
                /*
                 * The size of the boolean type is VM dependant, but we assume that it's 1 byte.
                 */
                primitiveSize = 1;
                break;
            case Byte:
                primitiveSize = Byte.BYTES;
                break;
            case Short:
                primitiveSize = Short.BYTES;
                break;
            case Char:
                primitiveSize = Character.BYTES;
                break;
            case Int:
                primitiveSize = Integer.BYTES;
                break;
            case Float:
                primitiveSize = Float.BYTES;
                break;
            case Long:
                primitiveSize = Long.BYTES;
                break;
            case Double:
                primitiveSize = Double.BYTES;
                break;
            default:
                throw JVMCIError.shouldNotReachHere(valueType.kind.toString());
        }

        logConstant(ConstantType.PRIMITIVE, primitiveSize);
    }

    public void constantString(ObjectInspector.StringType stringType) {
        logConstant(ConstantType.STRING, stringType.getBytes().length);
    }

    public void constantMethodPointer(ObjectInspector.MethodPointerType methodPointerType) {
        String name = typeControl.requestMethodName(methodPointerType.getMethod());
        logConstant(ConstantType.METHOD_POINTER, name.length());
    }
}
