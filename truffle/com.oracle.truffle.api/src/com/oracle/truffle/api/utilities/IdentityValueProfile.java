/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Represents a {@link ValueProfile} that speculates on the object identity of a value.
 */
public final class IdentityValueProfile extends ValueProfile {

    private static final Object UNINITIALIZED = new Object();
    private static final Object GENERIC = new Object();

    @CompilationFinal protected Object cachedValue = UNINITIALIZED;

    IdentityValueProfile() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T profile(T value) {
        if (cachedValue != GENERIC) {
            if (cachedValue == value) {
                return (T) cachedValue;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (cachedValue == UNINITIALIZED) {
                    cachedValue = value;
                } else {
                    cachedValue = GENERIC;
                }
            }
        }
        return value;
    }

    public boolean isGeneric() {
        return getCachedValue() == GENERIC;
    }

    public boolean isUninitialized() {
        return getCachedValue() == UNINITIALIZED;
    }

    public Object getCachedValue() {
        return cachedValue;
    }

    @Override
    public String toString() {
        return String.format("%s(%s)@%x", getClass().getSimpleName(), isUninitialized() ? "uninitialized" : (isGeneric() ? "generic" : String.format("@%x", Objects.hash(cachedValue))), hashCode());
    }

}
