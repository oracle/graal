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
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.site.DataSectionReference;

public class DataSectionAlignmentTest {

    /// Checks exact LCM results even when the product of the inputs exceeds the integer range.
    @Test
    public void testLcm() {
        assertEquals(24, DataSection.lcm(6, 8));
        assertEquals(65536, DataSection.lcm(32768, 65536));
        assertEquals(65536, DataSection.lcm(65536, 32768));
        assertEquals(Integer.MAX_VALUE, DataSection.lcm(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(8, DataSection.lcm(0, 8));
        assertEquals(8, DataSection.lcm(8, 0));
        assertEquals(0, DataSection.lcm(0, 0));
    }

    /// Checks that an unrepresentable LCM is rejected rather than wrapped.
    @Test
    public void testLcmOverflow() {
        assertThrows(ArithmeticException.class, () -> DataSection.lcm(46341, 46342));
    }

    /// Checks that updating an item preserves both alignment requirements.
    @Test
    public void testUpdateAlignment() {
        RawData data = new RawData(new byte[1], 32768);
        data.updateAlignment(65536);
        assertEquals(65536, data.getAlignment());

        RawData small = new RawData(new byte[1], 6);
        small.updateAlignment(8);
        assertEquals(24, small.getAlignment());
    }

    /// Checks the section alignment and offsets computed for small payloads with large alignments.
    @Test
    public void testSectionAlignment() {
        DataSection section = new DataSection();
        DataSectionReference first = section.insertData(new RawData(new byte[1], 32768));
        DataSectionReference second = section.insertData(new RawData(new byte[1], 65536));
        section.close(new OptionValues(OptionValues.newOptionMap()), 1);
        assertEquals(65536, section.getSectionAlignment());
        assertEquals(0, first.getOffset());
        assertEquals(65536, second.getOffset());
        assertEquals(65537, section.getSectionSize());
    }
}
