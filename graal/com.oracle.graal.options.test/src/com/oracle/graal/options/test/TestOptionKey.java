/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @run junit com.oracle.graal.options.test.TestOptionKey
 */

package com.oracle.graal.options.test;

import static com.oracle.graal.options.test.TestOptionKey.Options.Mutable;
import static com.oracle.graal.options.test.TestOptionKey.Options.SecondMutable;
import static com.oracle.graal.options.test.TestOptionKey.Options.Stable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.graal.options.OptionDescriptor;
import com.oracle.graal.options.OptionKey;
import com.oracle.graal.options.StableOptionKey;

@SuppressWarnings("try")
public class TestOptionKey {

    public static class Options {
        public static final OptionKey<Boolean> Stable = new StableOptionKey<>(true);
        public static final OptionKey<String> Mutable = new OptionKey<>("original");
        public static final OptionKey<String> SecondMutable = new OptionKey<>("second");
    }

    static final OptionDescriptor stable = OptionDescriptor.create("Stable", Boolean.class, "", Options.class, "Stable", Stable);
    static final OptionDescriptor mutable = OptionDescriptor.create("Mutable", String.class, "", Options.class, "Mutable", Mutable);
    static final OptionDescriptor secondMutable = OptionDescriptor.create("SecondMutable", String.class, "", Options.class, "SecondMutable", SecondMutable);

    @Test
    public void testMutable() {
        assertEquals("original", Mutable.getValue());
    }

    @Test
    public void testMultiple() {
        assertEquals("original", Mutable.getValue());
        assertEquals("second", SecondMutable.getValue());
    }

    @Test
    public void testStable() {
        assertTrue(Stable.getValue());
    }

    @Test
    public void toStringTest() {
        assertEquals("Mutable", Mutable.toString());
    }
}
