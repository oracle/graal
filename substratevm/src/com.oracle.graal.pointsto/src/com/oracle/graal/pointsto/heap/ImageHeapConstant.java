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

import java.util.Objects;

import org.graalvm.compiler.core.common.type.CompressibleConstant;
import org.graalvm.compiler.core.common.type.TypedConstant;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.VMConstant;

/**
 * It represents an object snapshot. It stores the replaced object, i.e., the result of applying
 * object replacers on the original hosted object, and the instance field values or array elements
 * of this object. The field values are stored as JavaConstant to also encode primitive values.
 * ImageHeapObject are created only after an object is processed through the object replacers.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public abstract class ImageHeapConstant implements JavaConstant, TypedConstant, CompressibleConstant, VMConstant {
    /** Stores the type of this object. */
    protected ResolvedJavaType type;
    /**
     * Stores the hosted object, already processed by the object transformers. It is null for
     * instances of partially evaluated classes.
     */
    protected final JavaConstant hostedObject;
    protected final int identityHashCode;

    protected final boolean compressed;

    ImageHeapConstant(ResolvedJavaType type, JavaConstant object, int identityHashCode, boolean compressed) {
        this.type = type;
        this.hostedObject = object;
        this.identityHashCode = identityHashCode;
        this.compressed = compressed;
    }

    static int createIdentityHashCode(JavaConstant object) {
        if (object == null) {
            /*
             * No backing object in the heap of the image builder VM. We want a "virtual" identity
             * hash code that has the same properties as the image builder VM, so we use the
             * identity hash code of a new and otherwise unused object in the image builder VM.
             */
            return System.identityHashCode(new Object());
        } else {
            /* Lazily looked up from the hostedObject when requested. */
            return -1;
        }
    }

    @Override
    public int getIdentityHashCode() {
        if (hostedObject != null) {
            if (hostedObject.isNull()) {
                /*
                 * According to the JavaDoc of System.identityHashCode, the identity hash code of
                 * null is 0.
                 */
                return 0;
            } else {
                return ((TypedConstant) hostedObject).getIdentityHashCode();
            }
        } else {
            /*
             * No backing object in the heap of the image builder VM. We want a "virtual" identity
             * hash code that has the same properties as the image builder VM, so we use the
             * identity hash code of a new and otherwise unused object in the image builder VM.
             */
            assert identityHashCode > 0 : "The Java HotSpot VM only returns positive numbers for the identity hash code, so we want to have the same restriction on Substrate VM in order to not surprise users";
            return identityHashCode;
        }
    }

    public JavaConstant getHostedObject() {
        return hostedObject;
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
    public ResolvedJavaType getType(MetaAccessProvider provider) {
        return type;
    }

    public void setType(ResolvedJavaType type) {
        this.type = type;
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
        return type.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ImageHeapConstant) {
            ImageHeapConstant other = (ImageHeapConstant) o;
            /*
             * Object identity doesn't take into account the compressed flag. This is done to match
             * the previous behavior where the raw object was extracted and used as a key when
             * constructing the image heap map.
             */
            return Objects.equals(this.type, other.type) && Objects.equals(this.hostedObject, other.hostedObject);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hostedObject != null ? hostedObject.hashCode() : 0;
    }
}
