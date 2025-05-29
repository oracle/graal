/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.constantpool;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.runtime.EspressoException;

public final class MissingFieldRefConstant implements ResolvedConstant {
    private final EspressoException failure;
    private final Assumption assumption;

    public MissingFieldRefConstant(EspressoException failure, Assumption missingFieldAssumption) {
        this.failure = failure;
        this.assumption = missingFieldAssumption;
    }

    @Override
    public Field value() {
        if (assumption.isValid()) {
            throw failure;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new NeedsFreshResolutionException();
        }
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public Tag tag() {
        return Tag.FIELD_REF;
    }
}
