/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@EspressoSubstitutions
public final class Target_sun_launcher_LauncherHelper {
    private static final String helpMessage = "Additional Java-on-Truffle commands:\n" +
                    "    --polyglot    Run with all other guest languages accessible.\n" +
                    "    --native      Run using the native launcher with limited access to Java libraries (default).\n" +
                    "    --jvm         Run on the Java Virtual Machine with access to Java libraries (Unsupported).\n" +
                    "    --vm.[option] Pass options to the host VM. To see available options, use '--help:vm'.\n" +
                    "    --log.file=<String>\n" +
                    "                  Redirect guest languages logging into a given file.\n" +
                    "    --log.[logger].level=<String>\n" +
                    "                  Set language log level to OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST or ALL.\n" +
                    "    --version:graalvm\n" +
                    "                  Print GraalVM version information and exit.\n" +
                    "    --show-version:graalvm\n" +
                    "                  Print GraalVM version information and continue execution.\n" +
                    "    --help:vm     Print options for the host VM.\n" +
                    "    --help:languages\n" +
                    "                  Print options for all installed languages.\n" +
                    "    --help:tools  Print options for all installed tools.\n" +
                    "    --help:engine Print options for the Truffle engine.\n" +
                    "    --help:expert Print additional options for experts.\n" +
                    "    --help:internal\n" +
                    "                  Print internal options for debugging language implementations and tools.";

    @Substitution
    abstract static class PrintHelpMessage extends SubstitutionNode {
        abstract void execute(boolean printToStderr);

        @Specialization
        void doCached(boolean printToStderr,
                        @Bind("getMeta()") Meta meta,
                        @Cached("create(meta.sun_launcher_LauncherHelper_printHelpMessage.getCallTargetNoSubstitution())") DirectCallNode originalPrintHelpMessage,
                        @Cached("create(meta.java_io_PrintStream_println.getCallTarget())") DirectCallNode println) {
            // Init output stream and print original help message
            originalPrintHelpMessage.call(printToStderr);

            // Append espresso specific help
            StaticObject stream = meta.sun_launcher_LauncherHelper_ostream.getObject(meta.sun_launcher_LauncherHelper.tryInitializeAndGetStatics());
            if (!StaticObject.isNull(stream)) {
                println.call(stream, meta.toGuestString(helpMessage));
            }
        }
    }
}
