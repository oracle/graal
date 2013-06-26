/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import org.junit.*;

import com.oracle.graal.phases.common.*;

/**
 * Collection of tests for {@link ConditionalEliminationPhase} including those that triggered bugs
 * in this phase.
 */
public class ConditionalEliminationTest extends GraalCompilerTest {

    static class Entry {

        final String name;

        public Entry(String name) {
            this.name = name;
        }
    }

    static class EntryWithNext extends Entry {

        public EntryWithNext(String name, Entry next) {
            super(name);
            this.next = next;
        }

        final Entry next;
    }

    public static Entry search(Entry start, String name, Entry alternative) {
        Entry current = start;
        do {
            while (current instanceof EntryWithNext) {
                if (name != null && current.name == name) {
                    current = null;
                } else {
                    Entry next = ((EntryWithNext) current).next;
                    current = next;
                }
            }

            if (current != null) {
                if (current.name.equals(name)) {
                    return current;
                }
            }
            if (current == alternative) {
                return null;
            }
            current = alternative;

        } while (true);
    }

    /**
     * This test presents a code pattern that triggered a bug where a (non-eliminated) checkcast
     * caused an enclosing instanceof (for the same object and target type) to be incorrectly
     * eliminated.
     */
    @Test
    public void testReanchoringIssue() {
        Entry end = new Entry("end");
        EntryWithNext e1 = new EntryWithNext("e1", end);
        EntryWithNext e2 = new EntryWithNext("e2", e1);

        test("search", e2, "e3", new Entry("e4"));
    }
}
