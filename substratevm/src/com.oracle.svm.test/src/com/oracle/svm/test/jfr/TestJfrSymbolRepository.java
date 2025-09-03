/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import jdk.jfr.Recording;
import org.junit.Test;
import com.oracle.svm.core.jfr.JfrSymbolRepository;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.Uninterruptible;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TestJfrSymbolRepository extends JfrRecordingTest {
    @Test
    public void test() throws Throwable {
        // Ensure JFR is created in case this is the first test to run
        String[] events = new String[]{};
        Recording recording = startRecording(events);
        stopRecording(recording, null);

        String str1 = "string1";
        String str1copy = "string1";
        String str2 = "string2";

        JfrSymbolRepository repo = SubstrateJVM.getSymbolRepository();

        long id1 = getSymbolId(repo, str1);
        long id2 = getSymbolId(repo, str2);
        long id1copy = getSymbolId(repo, str1copy);
        assertEquals(id1, id1copy);
        assertNotEquals(id1, id2);
    }

    @Uninterruptible(reason = "Needed for JfrSymbolRepository.getSymbolId().")
    private long getSymbolId(JfrSymbolRepository repo, String str) {
        return repo.getSymbolId(str, false);
    }
}
