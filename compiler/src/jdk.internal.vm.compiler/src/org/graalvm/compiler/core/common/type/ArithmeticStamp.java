/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.type;

import java.nio.ByteBuffer;
import java.util.Objects;

import jdk.vm.ci.meta.SerializableConstant;

/**
 * Type describing values that support arithmetic operations.
 */
public abstract class ArithmeticStamp extends Stamp {

    private final ArithmeticOpTable ops;

    protected ArithmeticStamp(ArithmeticOpTable ops) {
        this.ops = ops;
    }

    public ArithmeticOpTable getOps() {
        return ops;
    }

    public abstract SerializableConstant deserialize(ByteBuffer buffer);

    @Override
    public Stamp improveWith(Stamp other) {
        if (this.isCompatible(other)) {
            return this.join(other);
        }
        // Cannot improve, because stamps are not compatible.
        return this;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ops.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ArithmeticStamp)) {
            return false;
        }
        assert Objects.equals(ops, ((ArithmeticStamp) obj).ops) : ops + " vs. " + ((ArithmeticStamp) obj).ops;
        return true;
    }
}
