/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common;

import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.meta.ValueKind;

/**
 * This represents the type of a value which has been cast to a different {@link ValueKind}. In some
 * cases it's important to recover the original type of the underlying value in particular if it's
 * larger than the cast type.
 */
public final class LIRKindWithCast extends LIRKind {

    /**
     * This is the kind of the actual underlying value.
     */
    private final ValueKind<LIRKind> actualKind;

    private LIRKindWithCast(LIRKind toKind, LIRKind actualKind) {
        super(toKind.getPlatformKind(), toKind.getReferenceMask(), toKind.getReferenceCompressionMask(), toKind.getDerivedReferenceBase());
        assert actualKind.isUnknownReference() == toKind.isUnknownReference() : "Reference kind mismatch: " + Assertions.errorMessageContext("toKind", toKind, "actualKind", actualKind);
        this.actualKind = actualKind;
        assert actualKind.getClass() == LIRKind.class : "must exactly LIRKind";
    }

    public static ValueKind<?> castToKind(ValueKind<?> toKind, ValueKind<?> valueKind) {
        if (valueKind instanceof LIRKindWithCast) {
            ValueKind<LIRKind> actualKind = ((LIRKindWithCast) valueKind).getActualKind();
            if (actualKind == toKind) {
                return toKind;
            }
        }
        return new LIRKindWithCast((LIRKind) toKind, (LIRKind) valueKind);
    }

    public ValueKind<LIRKind> getActualKind() {
        return actualKind;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj.getClass() == LIRKindWithCast.class) {
            return ((LIRKindWithCast) obj).actualKind.equals(actualKind) && super.equals(obj);
        }
        return false;
    }

    @Override
    public String toString() {
        return super.toString() + "(" + actualKind + ")";
    }
}
