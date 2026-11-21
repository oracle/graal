/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.pgo;

import org.junit.Assert;
import org.junit.Test;

public class ProfileDumperTest {
    @Test
    public void leavesLiteralPathUnchanged() {
        Assert.assertEquals("profiles/run.iprof", ProfileDumper.expandDumpFilePattern("profiles/run.iprof"));
    }

    @Test
    public void expandsEveryProcessAndTimePlaceholder() {
        long before = System.currentTimeMillis();
        String expanded = ProfileDumper.expandDumpFilePattern("profiles/%p/%t-%p-%t.iprof");
        long after = System.currentTimeMillis();
        String process = Long.toString(ProcessHandle.current().pid());

        Assert.assertTrue(expanded, expanded.startsWith("profiles/" + process + "/"));
        String suffix = expanded.substring(("profiles/" + process + "/").length());
        String[] parts = suffix.split("-");
        Assert.assertEquals(expanded, 3, parts.length);
        Assert.assertEquals(process, parts[1]);
        Assert.assertEquals(parts[0] + ".iprof", parts[2]);
        long time = Long.parseLong(parts[0]);
        Assert.assertTrue(time >= before && time <= after);
    }

    @Test
    public void preservesUnknownPlaceholder() {
        String expanded = ProfileDumper.expandDumpFilePattern("%x-%p.iprof");
        Assert.assertEquals("%x-" + ProcessHandle.current().pid() + ".iprof", expanded);
    }
}
