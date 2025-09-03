/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.object;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.object.CoreLocations.DynamicObjectFieldLocation;

/**
 * Field location information.
 */
final class FieldInfo extends DynamicObjectFieldLocation implements Comparable<FieldInfo> {

    private static final boolean JDK21 = Runtime.version().feature() == 21;
    private static final int UNUSED_OFFSET = 0;

    /** Field offset. Used by AtomicFieldUpdaterOffset recomputation. Do not rename! */
    private final long offset;
    /** Declaring class. Used by AtomicFieldUpdaterOffset recomputation. Do not rename! */
    private final Class<? extends DynamicObject> tclass;
    /** Field {@link VarHandle}. */
    private final VarHandle varHandle;
    /** Field type. */
    private final Class<?> type;
    /** Field name. */
    private final String name;

    FieldInfo(Class<?> type, String name, long offset, Class<? extends DynamicObject> declaringClass, VarHandle varHandle) {
        super(JDK21 ? offset : UNUSED_OFFSET, declaringClass);
        if (type != Object.class && type != long.class) {
            throw new IllegalArgumentException(type.getName());
        }
        this.offset = JDK21 ? UNUSED_OFFSET : offset;
        this.tclass = declaringClass;
        this.varHandle = varHandle;
        this.type = type;
        this.name = name;
    }

    static FieldInfo fromField(Field field, VarHandle varHandle) {
        return new FieldInfo(field.getType(), field.getName(),
                        UnsafeAccess.objectFieldOffset(field), field.getDeclaringClass().asSubclass(DynamicObject.class), varHandle);
    }

    public Class<? extends DynamicObject> getDeclaringClass() {
        return tclass;
    }

    public long offset() {
        if (JDK21) {
            /*
             * GraalVM for JDK 21 does not know about the offset field in this class, so for
             * backwards compatibility, we must use the offset field from the superclass, i.e.
             * CoreLocations.DynamicObjectFieldLocation. Eventually, this can be removed.
             */
            assert super.offset != UNUSED_OFFSET;
            return super.offset;
        }
        // JDK 25+: The offset field is recomputed by a substitution in TruffleBaseFeature.
        assert offset != UNUSED_OFFSET;
        return offset;
    }

    public VarHandle varHandle() {
        return varHandle;
    }

    public Class<?> type() {
        return type;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name + ":" + offset;
    }

    @Override
    public int compareTo(FieldInfo other) {
        return Long.compare(this.offset, other.offset);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Long.hashCode(offset);
        result = prime * result + tclass.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof FieldInfo other)) {
            return false;
        }
        return this.offset == other.offset && this.tclass == other.tclass;
    }

    void receiverCheck(DynamicObject store) {
        if (!tclass.isInstance(store)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw illegalReceiver(store);
        }
    }

    private IllegalArgumentException illegalReceiver(DynamicObject store) {
        CompilerAsserts.neverPartOfCompilation();
        return new IllegalArgumentException("Invalid receiver type (expected " + getDeclaringClass() + ", was " + (store == null ? null : store.getClass()) + ")");
    }
}
