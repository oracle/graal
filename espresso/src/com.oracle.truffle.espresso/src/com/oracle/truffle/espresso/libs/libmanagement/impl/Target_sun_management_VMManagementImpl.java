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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.libs.InformationLeak;
import com.oracle.truffle.espresso.libs.LibsMeta;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libmanagement.LibManagement;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.vm.Management;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions(type = "Lsun/management/VMManagementImpl;", group = LibManagement.class)
public final class Target_sun_management_VMManagementImpl {

    @CompilerDirectives.TruffleBoundary
    @Substitution
    public static @JavaType(String.class) StaticObject getVersion0(@Inject LibsState libsState, @Inject InformationLeak iL, @Inject Meta meta) {
        Management management = libsState.checkAndGetManagement();
        int jmmVersion = iL.getManagementVersion(management);
        int major = (jmmVersion & 0x0FFF0000) >>> 16;
        int minor = (jmmVersion & 0x0000FF00) >>> 8;
        return meta.toGuestString(major + "." + minor);
    }

    @Substitution
    @CompilerDirectives.TruffleBoundary
    public static void initOptionalSupportFields(@Inject LibsState libsState, @Inject InformationLeak iL, @Inject LibsMeta libsMeta) {
        Management management = libsState.checkAndGetManagement();
        iL.initOptionalSupportFields(management, libsMeta);
    }

    @Substitution
    public static boolean isThreadCpuTimeEnabled(@Inject LibsState libsState, @Inject InformationLeak iL) {
        Management management = libsState.checkAndGetManagement();
        return iL.getBooleanManagementAttribute(management, Management.JMM_THREAD_CPU_TIME);
    }

    @Substitution
    public static boolean isThreadAllocatedMemoryEnabled(@Inject LibsState libsState, @Inject InformationLeak iL) {
        Management management = libsState.checkAndGetManagement();
        return iL.getBooleanManagementAttribute(management, Management.JMM_THREAD_ALLOCATED_MEMORY);
    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static long getStartupTime(@JavaType(internalName = "Lsun/management/VMManagementImpl;") StaticObject self, @Inject LibsState libsState, @Inject InformationLeak iL,
                    @Inject EspressoContext context) {
        Management management = libsState.checkAndGetManagement();
        return iL.getLongManagementAttribute(management, Management.JMM_JVM_INIT_DONE_TIME_MS);
    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static long getUptime0(@JavaType(internalName = "Lsun/management/VMManagementImpl;") StaticObject self, @Inject LibsState libsState, @Inject InformationLeak iL,
                    @Inject EspressoContext context) {
        Management management = libsState.checkAndGetManagement();
        return iL.getLongManagementAttribute(management, Management.JMM_JVM_UPTIME_MS);
    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static int getProcessId(@JavaType(internalName = "Lsun/management/VMManagementImpl;") StaticObject self, @Inject LibsState libsState, @Inject InformationLeak iL,
                    @Inject EspressoContext context) {
        Management management = libsState.checkAndGetManagement();
        // In the native code they cast without a check.
        return (int) iL.getLongManagementAttribute(management, Management.JMM_OS_PROCESS_ID);
    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static @JavaType(String[].class) StaticObject getVmArguments0(@JavaType(internalName = "Lsun/management/VMManagementImpl;") StaticObject unusedSelf, @Inject LibsState libsState,
                    @Inject EspressoLanguage lang,
                    @Inject VM vm) {
        libsState.checkManagement();
        return vm.JVM_GetVmArguments(lang);
    }
}
