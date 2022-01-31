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
package com.oracle.graal.pointsto.heap;

import java.util.function.Consumer;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisFuture;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Model for snapshotted objects. It stores the replaced object, i.e., the result of applying object
 * replacers on the original object, and the instance field values of this object. The field values
 * are stored as JavaConstant to also encode primitive values. ImageHeapObject are created only
 * after an object is processed through the object replacers.
 */
public class ImageHeapObject {
    /** Store the object, already processed by the object transformers. */
    private final JavaConstant object;

    ImageHeapObject(JavaConstant object) {
        this.object = object;
    }

    public JavaConstant getObject() {
        return object;
    }

    /* Equals and hashCode just compare the replaced constant. */

    @Override
    public boolean equals(Object o) {
        if (o instanceof ImageHeapObject) {
            ImageHeapObject other = (ImageHeapObject) o;
            return this.object.equals(other.object);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return object.hashCode();
    }
}

final class ImageHeapInstance extends ImageHeapObject {

    /** Store original field values of the object. */
    private final AnalysisFuture<JavaConstant>[] objectFieldValues;

    ImageHeapInstance(JavaConstant object, AnalysisFuture<JavaConstant>[] objectFieldValues) {
        super(object);
        this.objectFieldValues = objectFieldValues;
    }

    public JavaConstant readFieldValue(AnalysisField field) {
        return objectFieldValues[field.getPosition()].ensureDone();
    }

    /**
     * Return a task for transforming and snapshotting the field value, effectively a future for
     * {@link ImageHeapScanner#onFieldValueReachable(AnalysisField, JavaConstant, JavaConstant, ObjectScanner.ScanReason, Consumer)}.
     */
    public AnalysisFuture<JavaConstant> getFieldTask(AnalysisField field) {
        return objectFieldValues[field.getPosition()];
    }

    /**
     * Read the field value, executing the field task in this thread if not already executed.
     */
    public JavaConstant readField(AnalysisField field) {
        return objectFieldValues[field.getPosition()].ensureDone();
    }

    public void setFieldTask(AnalysisField field, AnalysisFuture<JavaConstant> task) {
        objectFieldValues[field.getPosition()] = task;
    }
}

final class ImageHeapArray extends ImageHeapObject {
    /** Contains the already scanned array elements. */
    private final JavaConstant[] arrayElementValues;

    ImageHeapArray(JavaConstant object, JavaConstant[] arrayElementValues) {
        super(object);
        this.arrayElementValues = arrayElementValues;
    }

    /**
     * Return the value of the element at the specified index as computed by
     * {@link ImageHeapScanner#onArrayElementReachable(JavaConstant, AnalysisType, JavaConstant, int, ObjectScanner.ScanReason)}.
     */
    public JavaConstant getElement(int idx) {
        return arrayElementValues[idx];
    }

    public void setElement(int idx, JavaConstant value) {
        arrayElementValues[idx] = value;
    }

    public int getLength() {
        return arrayElementValues.length;
    }
}
