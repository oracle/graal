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

package org.graalvm.visualizer.search.quicksearch;

import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.serialization.BinaryReader;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.Builder;
import org.graalvm.visualizer.data.serialization.FileContent;
import org.graalvm.visualizer.data.serialization.lazy.ScanningModelBuilder;
import org.openide.util.Utilities;
import java.io.File;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 *
 * @author odouda
 */
public class ViewTestUtil {
    private ViewTestUtil() {
    }

    public static GraphDocument loadData(File f) throws Exception {
        ScheduledExecutorService srv = Executors.newSingleThreadScheduledExecutor();
        FileContent file = new FileContent(f.toPath(), null);
        GraphDocument checkDocument = new GraphDocument();
        BinarySource scanSource = new BinarySource(null, file);
        Builder b = new ScanningModelBuilder(scanSource, file, checkDocument, null, srv);
        BinaryReader reader = new BinaryReader(scanSource, b);
        reader.parse();
        return checkDocument;
    }

    public static GraphDocument loadMegaData() throws Exception {
        URL bigv = ViewTestUtil.class.getResource("mega2.bgv");
        File f = Utilities.toFile(bigv.toURI());
        return loadData(f);
    }
}
