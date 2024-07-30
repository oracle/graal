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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.type.CompressibleConstant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.VMConstant;

/**
 * It represents an object snapshot. It stores the replaced object, i.e., the result of applying
 * object replacers on the original hosted object, and the instance field values or array elements
 * of this object. The field values are stored as JavaConstant to also encode primitive values.
 * ImageHeapObject are created only after an object is processed through the object replacers.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public abstract class ImageHeapConstant implements JavaConstant, TypedConstant, CompressibleConstant, VMConstant {

    private static final AtomicInteger currentId = new AtomicInteger(0);

    public static final VarHandle isReachableHandle = ReflectionUtil.unreflectField(ConstantData.class, "isReachable", MethodHandles.lookup());

    abstract static class ConstantData {
        /**
         * Stores the type of this object.
         */
        protected final AnalysisType type;
        /**
         * Stores the hosted object, already processed by the object transformers. It is null for
         * instances of partially evaluated classes.
         */
        private final JavaConstant hostedObject;
        /**
         * The identity hash code for the heap object. This field is only used if
         * {@link #hostedObject} is null, i.e., for objects without a backing object in the heap of
         * the image builder VM. We create a "virtual" identity hash code that has the same
         * properties as the image builder VM by using the identity hash code of a new and otherwise
         * unused object in the image builder VM.
         */
        private final int identityHashCode;
        /**
         * Unique id.
         */
        protected final int id;
        /**
         * A future that reads the hosted field or array elements values lazily only when the
         * receiver object is used. This way the shadow heap can contain hosted only objects, i.e.,
         * objects that cannot be reachable at run time but are processed ahead-of-time.
         */
        AnalysisFuture<Void> hostedValuesReader;
        /**
         * A constant is marked as reachable only when it is decided that it can be used at run-time
         * and its field values/array elements need to be processed. The value of the field is
         * initially null, then it stores the reason why this constant became reachable.
         */
        @SuppressWarnings("unused") private volatile Object isReachable;
        /**
         * A boolean allowing to distinguish a constant that was persisted from a base layer and a
         * constant created in the current layer.
         */
        private boolean isInBaseLayer;

        ConstantData(AnalysisType type, JavaConstant hostedObject, int identityHashCode) {
            Objects.requireNonNull(type);
            this.type = type;
            this.hostedObject = CompressibleConstant.uncompress(hostedObject);

            if (hostedObject == null) {
                if (identityHashCode == -1) {
                    /*
                     * No backing object in the heap of the image builder VM. We want a "virtual"
                     * identity hash code that has the same properties as the image builder VM, so
                     * we use the identity hash code of a new and otherwise unused object in the
                     * image builder VM.
                     */
                    this.identityHashCode = System.identityHashCode(new Object());
                } else {
                    this.identityHashCode = identityHashCode;
                }
            } else {
                /* This value must never be used later on. */
                this.identityHashCode = -1;
            }
            this.id = currentId.getAndIncrement();
        }

        @Override
        public int hashCode() {
            return hostedObject != null ? hostedObject.hashCode() : super.hashCode();
        }
    }

    protected final ConstantData constantData;
    protected final boolean compressed;

    ImageHeapConstant(ConstantData constantData, boolean compressed) {
        this.constantData = constantData;
        this.compressed = compressed;
    }

    public ConstantData getConstantData() {
        return constantData;
    }

    public void ensureReaderInstalled() {
        if (constantData.hostedValuesReader != null) {
            constantData.hostedValuesReader.ensureDone();
        }
    }

    /**
     * A regular image heap constant starts off without any fields or array elements installed. It
     * instead contains a future task, the hostedValuesReader, which creates the tasks that read the
     * hosted values. It must be executed before any values can be accessed. This ensures that the
     * hosted values are only read when the constant is indeed used, i.e., it was not eliminated by
     * constant folding.
     *
     * Simulated constants are fully initialized when they are created.
     */
    protected boolean isReaderInstalled() {
        return constantData.hostedValuesReader == null || constantData.hostedValuesReader.isDone();
    }

    /** Intentionally package private. Should only be set via ImageHeapScanner.markReachable. */
    boolean markReachable(ObjectScanner.ScanReason reason) {
        ensureReaderInstalled();
        return isReachableHandle.compareAndSet(constantData, null, reason);
    }

    public boolean isReachable() {
        return isReachableHandle.get(constantData) != null;
    }

    public boolean allowConstantFolding() {
        /*
         * An object whose type is initialized at run time does not have hosted field values. Only
         * simulated objects can be used for constant folding.
         */
        return constantData.type.isInitialized() || constantData.hostedObject == null;
    }

    public Object getReachableReason() {
        return constantData.isReachable;
    }

    public boolean hasIdentityHashCode() {
        return constantData.identityHashCode > 0;
    }

    public int getIdentityHashCode() {
        AnalysisError.guarantee(constantData.hostedObject == null, "ImageHeapConstant only stores the identity hash code when there is no hosted object.");
        AnalysisError.guarantee(constantData.identityHashCode > 0, "The provided identity hashcode value must be a positive number to be on par with the Java HotSpot VM.");
        return constantData.identityHashCode;
    }

    public void markInBaseLayer() {
        constantData.isInBaseLayer = true;
    }

    public boolean isInBaseLayer() {
        return constantData.isInBaseLayer;
    }

    public JavaConstant getHostedObject() {
        AnalysisError.guarantee(!CompressibleConstant.isCompressed(constantData.hostedObject), "References to hosted objects should never be compressed.");
        return constantData.hostedObject;
    }

    public boolean isBackedByHostedObject() {
        return constantData.hostedObject != null;
    }

    public static int getConstantID(ImageHeapConstant constant) {
        return constant.getConstantData().id;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isDefaultForKind() {
        return false;
    }

    @Override
    public AnalysisType getType() {
        return constantData.type;
    }

    @Override
    public Object asBoxedPrimitive() {
        return null;
    }

    @Override
    public int asInt() {
        return 0;
    }

    @Override
    public boolean asBoolean() {
        return false;
    }

    @Override
    public long asLong() {
        return 0;
    }

    @Override
    public float asFloat() {
        return 0;
    }

    @Override
    public double asDouble() {
        return 0;
    }

    @Override
    public boolean isCompressed() {
        return compressed;
    }

    @Override
    public String toValueString() {
        if (constantData.type.getJavaClass() == String.class && constantData.hostedObject != null) {
            String valueString = constantData.hostedObject.toValueString();
            /* HotSpotObjectConstantImpl.toValueString() puts the string between quotes. */
            return valueString.substring(1, valueString.length() - 1);
        }
        return constantData.type.getName();
    }

    /**
     * Returns a new image heap instance, as if {@link Object#clone} was called on the original
     * object. If the type is not cloneable, then null is returned.
     * <p>
     * The new constant is never backed by a hosted object, regardless of the input object.
     */
    public abstract ImageHeapConstant forObjectClone();

    @Override
    public boolean equals(Object o) {
        if (o instanceof ImageHeapConstant other) {
            /*
             * Object identity doesn't take into account the compressed flag. This is done to match
             * the previous behavior where the raw object was extracted and used as a key when
             * constructing the image heap map.
             */
            return this.constantData == other.constantData && this.compressed == other.compressed;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return constantData.hashCode() + (compressed ? 1 : 0);
    }

    @Override
    public String toString() {
        return "ImageHeapConstant<" + constantData.type.toJavaName() + ", reachable: " + isReachable() + ", reader installed: " + isReaderInstalled() +
                        ", compressed: " + compressed + ", backed: " + isBackedByHostedObject() + ">";
    }
}
