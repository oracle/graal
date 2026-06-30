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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import jdk.graal.compiler.ide.ClassFilter;
import jdk.graal.compiler.ide.IDEReport;
import jdk.graal.compiler.ide.IDEReportCategory;

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
    public void reportInstancesHaveIndependentState() {
        IDEReport filteredReport = IDEReport.create("example");
        IDEReport unfilteredReport = IDEReport.create("");

        filteredReport.saveClassReport(IDEReportCategory.CLASS_INITIALIZATION, "example/Application.java", "example.Application", "included");
        filteredReport.saveClassReport(IDEReportCategory.CLASS_INITIALIZATION, "other/Application.java", "other.Application", "filtered out");

        assertEquals(1, filteredReport.snapshot().reports().size());
        assertTrue(unfilteredReport.snapshot().reports().isEmpty());

        unfilteredReport.saveClassReport(IDEReportCategory.CLASS_INITIALIZATION, "other/Application.java", "other.Application", "included");

        assertEquals(1, filteredReport.snapshot().reports().size());
        assertEquals(1, unfilteredReport.snapshot().reports().size());
    }

    @Test
    public void invalidFilterDoesNotAffectFutureReports() {
        assertThrows(IllegalArgumentException.class, () -> IDEReport.create("example,,other"));

        assertNotNull(IDEReport.create("example"));
    }
}
