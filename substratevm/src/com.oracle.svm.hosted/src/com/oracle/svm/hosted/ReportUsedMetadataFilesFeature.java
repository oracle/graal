/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.configure.ConfigurationParser;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import org.graalvm.collections.EconomicSet;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

@AutomaticallyRegisteredFeature
public class ReportUsedMetadataFilesFeature implements InternalFeature {

    private EconomicSet<String> usedMetadataFiles;
    private boolean completed = false;

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        // get the set in any case to clean up memory in the parser
        usedMetadataFiles = ConfigurationParser.getUsedMetadataFiles();
        if (SubstrateOptions.ReportUsedMetadataFiles.getValue() && !completed) {
            File file = ReportUtils.reportFile(SubstrateOptions.reportsPath(), "usedMetadataFiles", "txt");
            ReportUtils.report("usedMetadataFiles", file.toPath(), this::printMetadataFiles);
        }
    }

    private void printMetadataFiles(PrintWriter out) {
        if (usedMetadataFiles != null) {
            List<String> files = usedMetadataFiles.toList();
            Collections.sort(files);
            for (String file : files) {
                out.println("used metadata: " + file);
            }
        } else {
            out.println("no metadata file used");
        }
        completed = true;
    }
}
