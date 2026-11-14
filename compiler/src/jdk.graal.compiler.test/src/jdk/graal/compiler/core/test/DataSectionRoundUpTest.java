/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import jdk.graal.compiler.code.DataSection;
import jdk.graal.compiler.code.DataSection.RawData;
import jdk.graal.compiler.code.DataSection.ZeroData;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.site.DataSectionReference;

public class DataSectionRoundUpTest {

    /// Checks small alignments, including offsets that need padding.
    @Test
    public void testSmallAlignment() {
        assertEquals(0, DataSection.align(0, 3));
        assertEquals(6, DataSection.align(6, 3));
        assertEquals(9, DataSection.align(7, 3));
    }

    /// Checks representable results when the intermediate rounding addition exceeds the integer range.
    @Test
    public void testLargeAlignment() {
        assertEquals(2147483646, DataSection.align(2147483646, 3));
        assertEquals(2147483646, DataSection.align(2147483645, 3));
        assertEquals(Integer.MAX_VALUE, DataSection.align(Integer.MAX_VALUE, 1));
        assertEquals(Integer.MAX_VALUE, DataSection.align(1, Integer.MAX_VALUE));
    }

    /// Checks that an unrepresentable aligned offset is rejected rather than wrapped.
    @Test
    public void testAlignmentOverflow() {
        assertThrows(ArithmeticException.class, () -> DataSection.align(Integer.MAX_VALUE, 2));
    }

    /// Checks layout near the integer limit without allocating or emitting a large payload.
    @Test
    public void testSectionSize() {
        DataSection section = new DataSection();
        section.insertData(new ZeroData(1, 2147483646));
        DataSectionReference last = section.insertData(new RawData(new byte[1], 3));
        section.close(new OptionValues(OptionValues.newOptionMap()), 1);
        assertEquals(2147483646, last.getOffset());
        assertEquals(Integer.MAX_VALUE, section.getSectionSize());
        assertEquals(3, section.getSectionAlignment());
    }
}
