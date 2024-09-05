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

package com.oracle.svm.core.heap.dump;

import com.oracle.svm.core.dcmd.AbstractDcmd;
import com.oracle.svm.core.dcmd.DcmdOption;

import java.io.IOException;
import com.oracle.svm.core.dcmd.DcmdParseException;

public class HeapDumpDcmd extends AbstractDcmd {

    public HeapDumpDcmd() {
        this.options = new DcmdOption[]{
                        new DcmdOption("filename", "File path of where to put the heap dump.", true, null)
        };

        this.name = "GC.heap_dump";
        this.description = "Generate a HPROF format dump of the heap.";
        this.impact = "medium";
    }

    @Override
    public String parseAndExecute(String[] arguments) throws DcmdParseException {
        String path = null;
        if (arguments.length != 2) {
            throw new DcmdParseException("Must specify file to dump to.");
        }
        if (arguments[1].contains("filename=")) {
            String[] pathArgumentSplit = arguments[1].split("=");
            if (pathArgumentSplit.length != 2) {
                throw new DcmdParseException("Must specify file to dump to.");
            }
            path = pathArgumentSplit[1];
        }

        if (path == null) {
            return "The argument 'filename' is mandatory.";
        }
        try {
            HeapDumping.singleton().dumpHeap(path, true);
        } catch (IOException e) {
            return "Could not dump heap: " + e;
        }
        return "Dumped to: " + path;
    }
}
