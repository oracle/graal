/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle.nodes;

import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.Kind;
import com.oracle.jvmci.meta.LocationIdentity;
import java.util.*;


/**
 * A {@link LocationIdentity} wrapping an object.
 */
public final class ObjectLocationIdentity extends LocationIdentity {

    private JavaConstant object;

    public static LocationIdentity create(JavaConstant object) {
        assert object.getKind() == Kind.Object && object.isNonNull();
        return new ObjectLocationIdentity(object);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ObjectLocationIdentity) {
            ObjectLocationIdentity that = (ObjectLocationIdentity) obj;
            return Objects.equals(that.object, this.object);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return object.hashCode();
    }

    private ObjectLocationIdentity(JavaConstant object) {
        this.object = object;
    }

    @Override
    public boolean isImmutable() {
        return false;
    }

    @Override
    public String toString() {
        return "Identity(" + object + ")";
    }
}
