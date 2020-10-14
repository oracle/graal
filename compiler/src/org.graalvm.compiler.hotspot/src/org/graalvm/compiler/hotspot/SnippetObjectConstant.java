/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import java.util.Objects;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SnippetObjectConstant implements JavaConstant {

    protected final Object object;

    public SnippetObjectConstant(Object object) {
        this.object = object;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof SnippetObjectConstant) {
            SnippetObjectConstant other = (SnippetObjectConstant) o;
            return object == other.object;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(object);
    }

    /**
     * Gets the object represented by this constant if it is of the given type.
     *
     * @param type the expected type of the object represented by this constant. If the object is
     *            required to be of this type, then wrap the call to this method in
     *            {@link Objects#requireNonNull(Object)}.
     * @return the object value represented by this constant if it is an
     *         {@link ResolvedJavaType#isInstance(JavaConstant) instance of} {@code type} otherwise
     *         {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T asObject(Class<T> type) {
        if (type.isInstance(object)) {
            return type.cast(object);
        }
        return null;
    }

    @Override
    public String toString() {
        return "SnippetObjectConstant{" +
                        "object=" + object +
                        '}';
    }

    @Override
    public String toValueString() {
        return JavaKind.Object.format(object);
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
    public Object asBoxedPrimitive() {
        throw new IllegalArgumentException();
    }

    @Override
    public int asInt() {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean asBoolean() {
        throw new IllegalArgumentException();
    }

    @Override
    public long asLong() {
        throw new IllegalArgumentException();
    }

    @Override
    public float asFloat() {
        throw new IllegalArgumentException();
    }

    @Override
    public double asDouble() {
        throw new IllegalArgumentException();
    }
}
