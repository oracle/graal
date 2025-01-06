/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.core.dcmd;

import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.BasedOnJDKFile;

import jdk.internal.vm.ThreadDumper;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+27/src/hotspot/share/services/diagnosticCommand.hpp#L757-L777")
public class ThreadDumpToFileDCmd extends AbstractDCmd {
    private static final DCmdOption<String> FILEPATH = new DCmdOption<>(String.class, "filepath", "The file path to the output file", true, null);
    private static final DCmdOption<Boolean> OVERWRITE = new DCmdOption<>(Boolean.class, "-overwrite", "May overwrite existing file", false, false);
    private static final DCmdOption<String> FORMAT = new DCmdOption<>(String.class, "-format", "Output format (\"plain\" or \"json\")", false, "plain");

    @Platforms(Platform.HOSTED_ONLY.class)
    public ThreadDumpToFileDCmd() {
        super("Thread.dump_to_file", "Dump threads, with stack traces, to a file in plain text or JSON format.", Impact.Medium,
                        new DCmdOption<?>[]{FILEPATH},
                        new DCmdOption<?>[]{OVERWRITE, FORMAT},
                        new String[]{
                                        "$ jcmd <pid> Thread.dump_to_file /some/path/my_file.txt",
                                        "$ jcmd <pid> Thread.dump_to_file -format=json -overwrite=true /some/path/my_file.json"
                        });
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+27/src/hotspot/share/services/diagnosticCommand.cpp#L1062-L1106")
    public String execute(DCmdArguments args) throws Throwable {
        String path = args.get(FILEPATH);
        boolean overwrite = args.get(OVERWRITE);
        boolean useJson = "json".equals(args.get(FORMAT));
        byte[] reply = dumpThreads(useJson, path, overwrite);
        return new String(reply, StandardCharsets.UTF_8);
    }

    private static byte[] dumpThreads(boolean useJson, String path, boolean overwrite) {
        if (useJson) {
            return ThreadDumper.dumpThreadsToJson(path, overwrite);
        }
        return ThreadDumper.dumpThreads(path, overwrite);
    }
}
