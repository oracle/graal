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

import java.nio.ByteBuffer;
import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.code.DataSection;
import jdk.graal.compiler.code.DataSection.Data;
import jdk.graal.compiler.code.DataSection.RawData;
import jdk.vm.ci.code.site.DataSectionReference;

/// Checks that closing a data section preserves its computed layout and emitted contents.
public class DataSectionTest extends GraalCompilerTest {
    @Test
    public void retainedAlignmentUpdateAfterClose() {
        DataSection section = new DataSection();
        section.insertData(new RawData(new byte[]{1}, 1));
        Data data = new RawData(new byte[]{2}, 1);
        DataSectionReference reference = section.insertData(data);
        section.close(getInitialOptions(), 1);

        Assert.assertThrows(IllegalStateException.class, () -> data.updateAlignment(8));
        Assert.assertEquals(1, data.getAlignment());
        Assert.assertEquals(1, reference.getOffset());
        Assert.assertEquals(1, section.getSectionAlignment());
        Assert.assertEquals(2, section.getSectionSize());
        ByteBuffer buffer = ByteBuffer.allocate(2);
        section.buildDataSection(buffer, (position, constant) -> {
        });
        Assert.assertArrayEquals(new byte[]{1, 2}, buffer.array());
    }

    @Test
    public void iteratorRemovalAfterClose() {
        checkIteratorRemoval(false);
    }

    @Test
    public void retainedIteratorRemovalAfterClose() {
        checkIteratorRemoval(true);
    }

    private void checkIteratorRemoval(boolean retainBeforeClose) {
        DataSection section = new DataSection();
        section.insertData(new RawData(new byte[]{7}, 1));
        Iterator<Data> iterator = null;
        if (retainBeforeClose) {
            iterator = section.iterator();
            iterator.next();
        }
        section.close(getInitialOptions(), 1);
        if (iterator == null) {
            iterator = section.iterator();
            iterator.next();
        }
        Assert.assertThrows(UnsupportedOperationException.class, iterator::remove);
        Assert.assertTrue(section.iterator().hasNext());
        Assert.assertEquals(1, section.getSectionSize());
        ByteBuffer buffer = ByteBuffer.allocate(1);
        section.buildDataSection(buffer, (position, constant) -> {
        });
        Assert.assertEquals(7, buffer.get(0));
    }

    @Test
    public void alignmentUpdateBeforeCloseAndAfterTransfer() {
        DataSection source = new DataSection();
        source.insertData(new RawData(new byte[]{1}, 1));
        Data data = new RawData(new byte[]{2}, 1);
        DataSectionReference reference = source.insertData(data);
        DataSection section = new DataSection();
        section.addAll(source);
        data.updateAlignment(8);
        section.close(getInitialOptions(), 1);

        Assert.assertFalse(source.iterator().hasNext());
        Assert.assertEquals(8, data.getAlignment());
        Assert.assertEquals(8, reference.getOffset());
        Assert.assertEquals(8, section.getSectionAlignment());
        Assert.assertEquals(9, section.getSectionSize());
        Assert.assertThrows(IllegalStateException.class, () -> data.updateAlignment(16));
    }
}
