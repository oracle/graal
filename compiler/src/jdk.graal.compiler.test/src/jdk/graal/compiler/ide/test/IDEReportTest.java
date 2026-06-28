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
package jdk.graal.compiler.ide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import jdk.graal.compiler.ide.ClassFilter;
import jdk.graal.compiler.ide.IDEReport;

public class IDEReportTest {

    @Test
    public void classFiltersAreTrimmedAndEmptyElementsAreRejected() {
        ClassFilter all = ClassFilter.parseFilterDescr("");
        assertTrue(all.shouldBeReported("example.Application"));
        assertTrue(ClassFilter.parseFilterDescr("   ").shouldBeReported("example.Application"));

        ClassFilter selected = ClassFilter.parseFilterDescr("example.one, example.two ");
        assertTrue(selected.shouldBeReported("example.one.Application"));
        assertTrue(selected.shouldBeReported("example.two.Application"));
        assertFalse(selected.shouldBeReported("example.three.Application"));
        assertThrows(IllegalArgumentException.class, () -> ClassFilter.parseFilterDescr("example.one,,example.two"));
        assertThrows(IllegalArgumentException.class, () -> ClassFilter.parseFilterDescr(",example.one"));
        assertThrows(IllegalArgumentException.class, () -> ClassFilter.parseFilterDescr("example.one,"));
    }

    @Test
    public void reportStateIsScopedToOneBuild() {
        assertFalse(IDEReport.isEnabled());
        try (var enabledBuild = IDEReport.beginBuild(true, "example")) {
            assertTrue(IDEReport.isEnabled());
            assertNotNull(enabledBuild.report());
            assertThrows(IllegalStateException.class, () -> IDEReport.beginBuild(true, "example"));
        }
        assertFalse(IDEReport.isEnabled());

        try (var disabledBuild = IDEReport.beginBuild(false, "")) {
            assertFalse(IDEReport.isEnabled());
            assertNull(disabledBuild.report());
        }
        assertFalse(IDEReport.isEnabled());
    }

    @Test
    public void invalidFilterDoesNotLeaveBuildActive() {
        assertThrows(IllegalArgumentException.class, () -> IDEReport.beginBuild(true, "example,,other"));
        assertFalse(IDEReport.isEnabled());

        try (var build = IDEReport.beginBuild(true, "example")) {
            assertNotNull(build.report());
        }
        assertFalse(IDEReport.isEnabled());
    }
}
