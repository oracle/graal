/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.test.classes;

import java.util.Optional;

/**
 * Fixture that combines object-array flow with a small JDK container-style access path.
 */
public class StandaloneArrayAndContainerCase {

    public static Object arrayResult;
    public static Object optionalResult;

    /**
     * Entry point that drives both the plain array and {@link Optional} scenarios through both
     * value choices.
     */
    public static void main(String[] args) {
        publishArray(readArray(true));
        publishArray(readArray(false));

        publishOptional(readOptional(true));
        publishOptional(readOptional(false));
    }

    /**
     * Reads from a small object array populated with two concrete values.
     */
    public static Object readArray(boolean first) {
        Object[] values = new Object[2];
        values[0] = new A();
        values[1] = new B();
        return first ? values[0] : values[1];
    }

    /**
     * Reads through a small {@link Optional}-based wrapper over the same value types.
     */
    public static Object readOptional(boolean first) {
        Optional<Object> value = first ? Optional.of(new A()) : Optional.of(new B());
        return value.orElseThrow();
    }

    private static void publishArray(Object value) {
        arrayResult = value;
    }

    private static void publishOptional(Object value) {
        optionalResult = value;
    }

    /**
     * First concrete element type.
     */
    public static final class A {
    }

    /**
     * Second concrete element type.
     */
    public static final class B {
    }
}
