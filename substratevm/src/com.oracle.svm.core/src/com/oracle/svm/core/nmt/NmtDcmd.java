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

package com.oracle.svm.core.nmt;

import com.oracle.svm.core.dcmd.AbstractDcmd;
import com.oracle.svm.core.dcmd.DcmdOption;
import com.oracle.svm.core.dcmd.DcmdParseException;
import com.oracle.svm.core.util.BasedOnJDKFile;

public class NmtDcmd extends AbstractDcmd {

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24%2B2/src/hotspot/share/nmt/nmtDCmd.cpp#L34-L64") //
    public NmtDcmd() {
        this.options = new DcmdOption[]{new DcmdOption("summary",
                        "request runtime to report current memory summary," + " which includes total reserved and committed memory," + " along with memory usage summary by each subsystem. BOOLEAN.",
                        false, "false")};
        this.examples = new String[]{"$ jcmd <pid> VM.native_memory summary"};
        this.name = "VM.native_memory";
        this.description = "Print native memory usage";
        this.impact = "low";
    }

    @Override
    public String parseAndExecute(String[] arguments) throws DcmdParseException {
        if (arguments.length != 2) {
            throw new DcmdParseException("Exactly 1 argument should be specified. See $ jcmd <pid> help VM.native_memory");
        }
        if (arguments[1].equals("detailed")) {
            return "Detailed NMT mode is not supported yet.";
        } else if (arguments[1].equals("summary")) {
            return NativeMemoryTracking.singleton().generateReportString();
        }
        return null;
    }
}
