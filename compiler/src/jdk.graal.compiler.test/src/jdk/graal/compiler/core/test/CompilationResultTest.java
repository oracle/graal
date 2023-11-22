/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.code.CompilationResult.CodeComment;
import jdk.graal.compiler.code.CompilationResult.CodeMark;
import jdk.graal.compiler.code.CompilationResult.JumpTable;
import org.junit.Test;

public class CompilationResultTest {

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testCodeComment() {
        CodeComment comment = new CodeComment(0, "test");
        CodeComment commentSame = new CodeComment(0, "test");
        CodeComment commentDifferent = new CodeComment(1, "other");
        assertTrue(comment.equals(comment));
        assertTrue(comment.equals(commentSame));
        assertFalse(comment.equals(commentDifferent));
        assertFalse(comment.equals(this));
        assertTrue(comment.toString().length() > 0); // test for NPE
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testJumpTable() {
        JumpTable table = new JumpTable(0, 0, 8, JumpTable.EntryFormat.OFFSET_ONLY);
        JumpTable tableSame = new JumpTable(0, 0, 8, JumpTable.EntryFormat.OFFSET_ONLY);
        JumpTable tableDifferent = new JumpTable(0, 0, 8, JumpTable.EntryFormat.VALUE_AND_OFFSET);
        assertTrue(table.equals(table));
        assertTrue(table.equals(tableSame));
        assertFalse(table.equals(tableDifferent));
        assertFalse(table.equals(this));
        assertTrue(table.toString().length() > 0); // test for NPE
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testCompilationResult() {
        CompilationResult result = new CompilationResult("testresult");
        result.setEntryBCI(0);
        CompilationResult resultSimilar = new CompilationResult("testresult");
        resultSimilar.setEntryBCI(0);
        CompilationResult resultDifferent = new CompilationResult("differentResult");
        resultDifferent.setEntryBCI(1000);
        assertTrue(result.equals(result));
        assertFalse(result.equals(resultSimilar));
        assertFalse(result.equals(resultDifferent));
        assertFalse(result.equals(this));
        assertTrue(result.toString().length() > 0); // test for NPE
    }

    private static class TestMarkId implements CompilationResult.MarkId {
        @Override
        public String getName() {
            return "test";
        }

        public static final Object id = new Object();

        @Override
        public Object getId() {
            return id;
        }
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testCodeMark() {
        CompilationResult.MarkId id = new TestMarkId();
        CodeMark mark = new CodeMark(0, id);
        CodeMark markSame = new CodeMark(0, id);
        CodeMark markDifferent = new CodeMark(1000, new TestMarkId());
        assertTrue(mark.equals(mark));
        assertTrue(mark.equals(markSame));
        assertFalse(mark.equals(markDifferent));
        assertFalse(mark.equals(this));
        assertTrue(mark.toString().length() > 0); // test for NPE
    }
}
