/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.serialization.lazy;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import org.openide.util.RequestProcessor;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.BinarySource;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;

/**
 * @author sdedic
 */
public class LazySerDebugUtils {
    static {
        LoadSupport._testUseWeakRefs = true;
    }

    public static void init() {
    }

    public static void setLargeThreshold(int threshold) {
        SingleGroupBuilder.setLargeEntryThreshold(threshold);
    }

    public static GraphDocument loadResource(GraphDocument doc, File file) throws IOException {
        if (doc == null) {
            doc = new GraphDocument();
        }
        final GraphDocument fDoc = doc;
        FileContent fc = new FileContent(file.toPath(), FileChannel.open(file.toPath(), StandardOpenOption.READ));
        BinarySource scanSource = new BinarySource(null, fc);
        ScanningModelBuilder smb = new ScanningModelBuilder(
                scanSource, fc, (i, p, g) -> fDoc,
                null,
                RequestProcessor.getDefault());
        BinaryReader reader = new BinaryReader(scanSource, smb);
        reader.parse();
        return doc;
    }
}
