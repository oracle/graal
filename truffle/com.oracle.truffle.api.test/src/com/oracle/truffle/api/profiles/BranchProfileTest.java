/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.profiles;

import com.oracle.truffle.api.nodes.Node;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SeparateClassloaderTestRunner.class)
public class BranchProfileTest {

    @Test
    public void testEnter() {
        BranchProfile profile = BranchProfile.Enabled.create0();
        profile.enter();
        profile.enter();
    }

    @Test
    public void testToString() {
        BranchProfile profile = BranchProfile.Enabled.create0();
        assertTrue(profile.toString().contains(BranchProfile.class.getSimpleName()));
        assertTrue(profile.toString().contains("UNINITIALIZED"));
        assertTrue(profile.toString().contains(Integer.toHexString(profile.hashCode())));
        profile.enter();
        assertTrue(profile.toString().contains(BranchProfile.class.getSimpleName()));
        assertTrue(profile.toString().contains("VISITED"));
        assertTrue(profile.toString().contains(Integer.toHexString(profile.hashCode())));
    }

    // BEGIN: BranchProfileSample
    class SampleNode extends Node {
        final BranchProfile errorProfile = BranchProfile.create();

        int execute(int value) {
            if (value == Integer.MAX_VALUE) {
                errorProfile.enter();
                throw new Error("Invalid input value");
            }
            return value;
        }
    }
    // END: BranchProfileSample
}
