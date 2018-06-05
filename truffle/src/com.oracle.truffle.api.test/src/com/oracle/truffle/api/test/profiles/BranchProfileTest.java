/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.profiles;

import static com.oracle.truffle.api.test.ReflectionUtils.invokeStatic;
import static com.oracle.truffle.api.test.ReflectionUtils.loadRelative;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.profiles.BranchProfile;

public class BranchProfileTest {

    @Test
    public void testEnter() {
        BranchProfile profile = (BranchProfile) invokeStatic(loadRelative(CountingConditionProfileTest.class, "BranchProfile$Enabled"), "create0");
        profile.enter();
        profile.enter();
    }

    @Test
    public void testToString() {
        BranchProfile profile = (BranchProfile) invokeStatic(loadRelative(CountingConditionProfileTest.class, "BranchProfile$Enabled"), "create0");
        assertTrue(profile.toString().contains(BranchProfile.class.getSimpleName()));
        assertTrue(profile.toString().contains("UNINITIALIZED"));
        assertTrue(profile.toString().contains(Integer.toHexString(profile.hashCode())));
        profile.enter();
        assertTrue(profile.toString().contains(BranchProfile.class.getSimpleName()));
        assertTrue(profile.toString().contains("VISITED"));
        assertTrue(profile.toString().contains(Integer.toHexString(profile.hashCode())));
    }
}
