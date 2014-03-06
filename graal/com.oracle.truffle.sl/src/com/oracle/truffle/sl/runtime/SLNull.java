/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.runtime;

/**
 * The SL type for a {@code null} (i.e., undefined) value. In Truffle, it is generally discouraged
 * to use the Java {@code null} value to represent the guest language {@code null} value. It is not
 * possible to specialize on Java {@code null} (since you cannot ask it for the Java class), and
 * there is always the danger of a spurious {@link NullPointerException}. Representing the guest
 * language {@code null} as a singleton, as in {@link #SINGLETON this class}, is the recommended
 * practice.
 * */
public final class SLNull {

    /**
     * The canonical value to represent {@code null} in SL.
     */
    public static final SLNull SINGLETON = new SLNull();

    /**
     * Disallow instantiation from outside to ensure that the {@link #SINGLETON} is the only
     * instance.
     */
    private SLNull() {
    }

    /**
     * This method is, e.g., called when using the {@code null} value in a string concatenation. So
     * changing it has an effect on SL programs.
     */
    @Override
    public String toString() {
        return "null";
    }
}
