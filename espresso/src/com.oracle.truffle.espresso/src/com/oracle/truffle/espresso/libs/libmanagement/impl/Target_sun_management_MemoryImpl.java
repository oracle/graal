/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs.libmanagement.impl;

import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;

import com.oracle.truffle.espresso.libs.InformationLeak;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libmanagement.LibManagement;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.vm.Management;

@EspressoSubstitutions(group = LibManagement.class)
public final class Target_sun_management_MemoryImpl {

    @Substitution
    public static @JavaType(MemoryManagerMXBean[].class) StaticObject getMemoryManagers0(@Inject LibsState libsState, @Inject InformationLeak iL) {
        Management management = libsState.checkAndGetManagement();
        return iL.getMemoryManagers(management);
    }

    @Substitution
    public static @JavaType(MemoryPoolMXBean[].class) StaticObject getMemoryPools0(@Inject LibsState libsState, @Inject InformationLeak iL) {
        Management management = libsState.checkAndGetManagement();
        return iL.getMemoryPools(management);
    }
}
