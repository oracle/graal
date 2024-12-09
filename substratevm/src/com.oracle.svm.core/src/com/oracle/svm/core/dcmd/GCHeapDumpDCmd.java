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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.heap.dump.HeapDumping;
import com.oracle.svm.core.util.BasedOnJDKFile;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+27/src/hotspot/share/services/diagnosticCommand.hpp#L262-L283")
public class GCHeapDumpDCmd extends AbstractDCmd {
    private static final DCmdOption<String> FILENAME = new DCmdOption<>(String.class, "filename", "File path of where to put the heap dump", true, null);
    private static final DCmdOption<Boolean> DUMP_ALL = new DCmdOption<>(Boolean.class, "-all", "Dump all objects, including unreachable objects", false, false);
    private static final DCmdOption<Boolean> OVERWRITE = new DCmdOption<>(Boolean.class, "-overwrite", "If specified, the dump file will be overwritten if it exists", false, false);

    @Platforms(Platform.HOSTED_ONLY.class)
    public GCHeapDumpDCmd() {
        super("GC.heap_dump", "Generate a HPROF format dump of the Java heap.", Impact.High, new DCmdOption<?>[]{FILENAME}, new DCmdOption<?>[]{DUMP_ALL, OVERWRITE});
    }

    @Override
    public String execute(DCmdArguments args) throws Throwable {
        String path = args.get(FILENAME);
        boolean gcBefore = !args.get(DUMP_ALL);
        boolean overwrite = args.get(OVERWRITE);

        HeapDumping.singleton().dumpHeap(path, gcBefore, overwrite);
        return "Dumped to: " + path;
    }
}
