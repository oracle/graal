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
package com.oracle.svm.core.thread;

import com.oracle.svm.core.dcmd.AbstractDcmd;
import com.oracle.svm.core.dcmd.DcmdParseException;
import com.oracle.svm.core.dcmd.DcmdOption;
import jdk.internal.vm.ThreadDumper;

public class ThreadDumpToFileDcmd extends AbstractDcmd {

    public ThreadDumpToFileDcmd() {
        this.options = new DcmdOption[]{
                        new DcmdOption("filepath", "The file path to the output file. (STRING)", true, null),
                        new DcmdOption("overwrite", "May overwrite existing file. (BOOLEAN)", false, "false"),
                        new DcmdOption("format", "Output format (\"plain\" or \"json\") (STRING)", false, "plain")

        };
        this.name = "Thread.dump_to_file";
        this.description = "Dumps thread stacks (including virtual) to a specified file.";
        this.impact = "High";
        this.examples = new String[]{
                        "$ jcmd <pid> Thread.dump_to_file filepath=/some/path/my_file.txt",
                        "$ jcmd <pid> Thread.dump_to_file format=json overwrite=true filepath=/some/path/my_file.json"};
    }

    @Override
    public String parseAndExecute(String[] arguments) throws DcmdParseException {

        String pathString = null;
        boolean overwrite = false;
        boolean useJson = false;
        for (String argument : arguments) {
            if (argument.contains("filepath=")) {
                pathString = extractArgument(argument);
            } else if (argument.contains("overwrite=")) {
                overwrite = Boolean.parseBoolean(extractArgument(argument));
            } else if (argument.contains("format=")) {
                String result = extractArgument(argument);
                if (result.equals("json")) {
                    useJson = true;
                } else if (!result.equals("plain")) {
                    throw new DcmdParseException("Format must be either json or plain, but provided: " + result);
                }
            }
        }

        if (pathString == null) {
            return "The argument 'filepath' is mandatory.";
        }

        if (useJson) {
            ThreadDumper.dumpThreadsToJson(pathString, overwrite);
        } else {
            ThreadDumper.dumpThreads(pathString, overwrite);
        }
        return "Created " + pathString;
    }

    private static String extractArgument(String input) throws DcmdParseException {
        String[] pathArgumentSplit = input.split("=");
        if (pathArgumentSplit.length != 2) {
            throw new DcmdParseException("Invalid command structure.");
        }
        return pathArgumentSplit[1];
    }
}
