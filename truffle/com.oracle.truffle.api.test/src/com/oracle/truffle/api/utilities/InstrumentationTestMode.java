/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.vm.PolyglotEngine;

public class InstrumentationTestMode {

    public static void set(boolean enable) {

        try {
            final PolyglotEngine vm = PolyglotEngine.newBuilder().build();
            final Field instrumenterField = vm.getClass().getDeclaredField("instrumenter");
            instrumenterField.setAccessible(true);
            final Object instrumenter = instrumenterField.get(vm);
            final java.lang.reflect.Field testVMField = Instrumenter.class.getDeclaredField("testVM");
            testVMField.setAccessible(true);
            if (enable) {
                testVMField.set(instrumenter, vm);
            } else {
                testVMField.set(instrumenter, null);
            }
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            fail("Reflective access to Instrumenter for testing");
        }

    }
}
