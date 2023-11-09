/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @run junit jdk.vm.ci.options.test.NestedBooleanOptionKeyTest
 */

package jdk.graal.compiler.options.test;

import static jdk.graal.compiler.options.test.NestedBooleanOptionKeyTest.Options.NestedOption0;
import static jdk.graal.compiler.options.test.NestedBooleanOptionKeyTest.Options.NestedOption1;
import static jdk.graal.compiler.options.test.NestedBooleanOptionKeyTest.Options.NestedOption2;
import static jdk.graal.compiler.options.test.NestedBooleanOptionKeyTest.Options.Parent0;
import static jdk.graal.compiler.options.test.NestedBooleanOptionKeyTest.Options.Parent1;
import static jdk.graal.compiler.options.test.NestedBooleanOptionKeyTest.Options.Parent2;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jdk.graal.compiler.options.NestedBooleanOptionKey;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

public class NestedBooleanOptionKeyTest {

    public static class Options {
        public static final OptionKey<Boolean> Parent0 = new OptionKey<>(true);
        public static final OptionKey<Boolean> Parent1 = new OptionKey<>(true);
        public static final OptionKey<Boolean> Parent2 = new OptionKey<>(true);
        public static final NestedBooleanOptionKey NestedOption0 = new NestedBooleanOptionKey(Parent0, true);
        public static final NestedBooleanOptionKey NestedOption1 = new NestedBooleanOptionKey(Parent1, true);
        public static final NestedBooleanOptionKey NestedOption2 = new NestedBooleanOptionKey(Parent2, false);
    }

    static final OptionDescriptor parent0 = OptionDescriptor.create("Parent0", OptionType.Debug, Boolean.class, "", Options.class, "Parent0", Parent0);
    static final OptionDescriptor nestedOption0 = OptionDescriptor.create("NestedOption0", OptionType.Debug, Boolean.class, "", Options.class, "NestedOption0", NestedOption0);
    static final OptionDescriptor parent1 = OptionDescriptor.create("Parent1", OptionType.Debug, Boolean.class, "", Options.class, "Parent1", Parent1);
    static final OptionDescriptor nestedOption1 = OptionDescriptor.create("NestedOption1", OptionType.Debug, Boolean.class, "", Options.class, "NestedOption1", NestedOption1);
    static final OptionDescriptor parent2 = OptionDescriptor.create("Parent2", OptionType.Debug, Boolean.class, "", Options.class, "Parent2", Parent2);
    static final OptionDescriptor nestedOption2 = OptionDescriptor.create("NestedOption2", OptionType.Debug, Boolean.class, "", Options.class, "NestedOption2", NestedOption2);

    @Test
    public void testGetParentOption() {
        Assert.assertEquals(NestedOption0.getParentOption(), Parent0);
        Assert.assertEquals(NestedOption1.getParentOption(), Parent1);
        Assert.assertEquals(NestedOption2.getParentOption(), Parent2);
    }

    @Test
    public void runDefaultTrue() {
        OptionValues options = new OptionValues(null, Parent1, true);
        assertTrue(Parent1.getValue(options));
        assertTrue(NestedOption1.getValue(options));

        // nested value unset
        options = new OptionValues(null, Parent1, false);
        assertFalse(Parent1.getValue(options));
        assertFalse(NestedOption1.getValue(options));

        // set false
        options = new OptionValues(null, Parent1, false, NestedOption1, false);
        assertFalse(Parent1.getValue(options));
        assertFalse(NestedOption1.getValue(options));

        options = new OptionValues(null, Parent1, true, NestedOption1, false);
        assertTrue(Parent1.getValue(options));
        assertFalse(NestedOption1.getValue(options));

        // set true
        options = new OptionValues(null, Parent1, false, NestedOption1, true);
        assertFalse(Parent1.getValue(options));
        assertTrue(NestedOption1.getValue(options));

        options = new OptionValues(null, Parent1, true, NestedOption1, true);
        assertTrue(Parent1.getValue(options));
        assertTrue(NestedOption1.getValue(options));
    }

    @Test
    public void runDefaultFalse() {
        OptionValues options = new OptionValues(null, Parent2, true);
        assertTrue(Parent2.getValue(options));
        assertFalse(NestedOption2.getValue(options));

        // nested value unset
        options = new OptionValues(null, Parent2, false);
        assertFalse(Parent2.getValue(options));
        assertFalse(NestedOption2.getValue(options));

        // set false
        options = new OptionValues(null, Parent2, false, NestedOption2, false);
        assertFalse(Parent2.getValue(options));
        assertFalse(NestedOption2.getValue(options));

        options = new OptionValues(null, Parent2, true, NestedOption2, false);
        assertTrue(Parent2.getValue(options));
        assertFalse(NestedOption2.getValue(options));

        // set true
        options = new OptionValues(null, Parent2, false, NestedOption2, true);
        assertFalse(Parent2.getValue(options));
        assertTrue(NestedOption2.getValue(options));

        options = new OptionValues(null, Parent2, true, NestedOption2, true);
        assertTrue(Parent2.getValue(options));
        assertTrue(NestedOption2.getValue(options));
    }
}
