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
package com.oracle.truffle.espresso.libs.libjava.impl;

import java.time.Duration;
import java.time.Instant;

import com.oracle.truffle.espresso.libs.InformationLeak;
import com.oracle.truffle.espresso.libs.LibsMeta;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions(type = "Ljava/lang/ProcessHandleImpl$Info;", group = LibJava.class)
public final class Target_java_lang_ProcessHandleImpl_Info {
    @Substitution
    public static void initIDs() {
        // nop
    }

    @Substitution(hasReceiver = true)
    public static void info0(@JavaType(internalName = "Ljava/lang/ProcessHandleImpl$Info;") StaticObject self, long pid, @Inject InformationLeak iL, @Inject LibsMeta libsMeta,
                    @Inject LibsState libsState) {
        libsState.checkCreateProcessAllowed();
        ProcessHandle.Info info = iL.getProcessHandleInfo(pid);
        if (info != null) {
            Meta meta = libsMeta.getMeta();
            String command = info.command().orElse("");
            libsMeta.java_lang_ProcessHandleImpl$Info_command.setObject(self, meta.toGuestString(command));

            String commandLine = info.commandLine().orElse("");
            libsMeta.java_lang_ProcessHandleImpl$Info_commandLine.setObject(self, meta.toGuestString(commandLine));

            String[] arguments = info.arguments().orElse(new String[0]);
            StaticObject[] arr = new StaticObject[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                arr[i] = meta.toGuestString(arguments[i]);
            }
            libsMeta.java_lang_ProcessHandleImpl$Info_arguments.setObject(self, libsMeta.getContext().getAllocator().wrapArrayAs(meta.java_lang_String_array, arr));

            long startTime = info.startInstant().orElse(Instant.EPOCH).toEpochMilli();
            libsMeta.java_lang_ProcessHandleImpl$Info_startTime.setLong(self, startTime);

            long totalTime = info.totalCpuDuration().orElse(Duration.ZERO).toMillis();
            libsMeta.java_lang_ProcessHandleImpl$Info_totalTime.setLong(self, totalTime);

            String user = info.user().orElse("");
            libsMeta.java_lang_ProcessHandleImpl$Info_user.setObject(self, meta.toGuestString(user));
        }
    }

}
