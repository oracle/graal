/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.options.test;

import static com.oracle.graal.options.test.TestOptionValue.Options.*;
import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.oracle.graal.options.*;
import com.oracle.graal.options.OptionValue.OverrideScope;

public class TestOptionValue {

    public static class Options {
        public static final OptionValue<Boolean> Stable = new StableOptionValue<>(true);
        public static final OptionValue<String> Mutable = new OptionValue<>("original");
        public static final OptionValue<String> SecondMutable = new OptionValue<>("second");
    }

    static final OptionDescriptor stable = new OptionDescriptor("Stable", Boolean.class, "", Options.class, "Stable", Stable);
    static final OptionDescriptor mutable = new OptionDescriptor("Mutable", String.class, "", Options.class, "Mutable", Mutable);
    static final OptionDescriptor secondMutable = new OptionDescriptor("SecondMutable", String.class, "", Options.class, "SecondMutable", SecondMutable);

    @Test
    public void testMutable() {
        assertEquals("original", Mutable.getValue());
        try (OverrideScope s1 = OptionValue.override(Mutable, "override1")) {
            assertEquals("override1", Mutable.getValue());
            try (OverrideScope s2 = OptionValue.override(Mutable, "override2")) {
                assertEquals("override2", Mutable.getValue());
            }
            assertEquals("override1", Mutable.getValue());
            try (OverrideScope s3 = OptionValue.override(Mutable, "override3")) {
                assertEquals("override3", Mutable.getValue());
            }
            assertEquals("override1", Mutable.getValue());
        }
        assertEquals("original", Mutable.getValue());
        try (OverrideScope s1 = OptionValue.override(Mutable, "original")) {
            assertEquals("original", Mutable.getValue());
        }
    }

    @Test
    public void testMultiple() {
        assertEquals("original", Mutable.getValue());
        assertEquals("second", SecondMutable.getValue());
        try (OverrideScope s1 = OptionValue.override(Mutable, "override1", SecondMutable, "secondOverride1")) {
            assertEquals("override1", Mutable.getValue());
            assertEquals("secondOverride1", SecondMutable.getValue());
            try (OverrideScope s2 = OptionValue.override(Mutable, "override2", SecondMutable, "secondOverride2")) {
                assertEquals("override2", Mutable.getValue());
                assertEquals("secondOverride2", SecondMutable.getValue());
            }
            assertEquals("override1", Mutable.getValue());
            assertEquals("secondOverride1", SecondMutable.getValue());
            try (OverrideScope s3 = OptionValue.override(Mutable, "override3", SecondMutable, "secondOverride3")) {
                assertEquals("override3", Mutable.getValue());
                assertEquals("secondOverride3", SecondMutable.getValue());
            }
            assertEquals("override1", Mutable.getValue());
            assertEquals("secondOverride1", SecondMutable.getValue());
        }
        assertEquals("original", Mutable.getValue());
        assertEquals("second", SecondMutable.getValue());
        try (OverrideScope s1 = OptionValue.override(Mutable, "original", SecondMutable, "second")) {
            assertEquals("original", Mutable.getValue());
            assertEquals("second", SecondMutable.getValue());
        }
    }

    @Test
    public void testStable() {
        assertTrue(Stable.getValue());
        try (OverrideScope s = OptionValue.override(Stable, false)) {
            fail("cannot override stable option");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void toStringTest() {
        assertEquals("com.oracle.graal.options.test.TestOptionValue$Options.Mutable=original", Mutable.toString());
        try (OverrideScope s1 = OptionValue.override(Mutable, "override1")) {
            assertEquals("com.oracle.graal.options.test.TestOptionValue$Options.Mutable=override1", Mutable.toString());
            try (OverrideScope s2 = OptionValue.override(Mutable, "override2")) {
                assertEquals("com.oracle.graal.options.test.TestOptionValue$Options.Mutable=override2", Mutable.toString());
            }
        }
    }

    @Test
    public void getValuesTest() {
        assertEquals(Arrays.asList("original"), Mutable.getValues(null));
        assertEquals(Arrays.asList(true), Stable.getValues(null));
        try (OverrideScope s1 = OptionValue.override(Mutable, "override1")) {
            assertEquals(Arrays.asList("override1", "original"), Mutable.getValues(null));
            try (OverrideScope s2 = OptionValue.override(Mutable, "override2")) {
                assertEquals(Arrays.asList("override2", "override1", "original"), Mutable.getValues(null));
            }
        }
    }
}
