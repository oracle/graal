/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

import jdk.vm.ci.common.JVMCIError;

public class ReportUtils {
    static final String CONNECTING_INDENT = "\u2502   "; // "| "
    static final String EMPTY_INDENT = "    ";
    static final String CHILD = "\u251c\u2500\u2500 "; // "|-- "
    static final String LAST_CHILD = "\u2514\u2500\u2500 "; // "`-- "

    static final Comparator<AnalysisMethod> methodComparator = Comparator.comparing(m -> m.format("%H.%n(%p)"));
    static final Comparator<AnalysisField> fieldComparator = (f1, f2) -> f1.format("%H.%n").compareTo(f2.format("%H.%n"));
    static final Comparator<InvokeTypeFlow> invokeComparator = Comparator.comparing(i -> i.getTargetMethod().format("%H.%n(%p)"));

    /**
     * Print a report in the format: path/name_timeStamp.extension. The path is relative to the
     * working directory.
     * 
     * @param description the description of the report
     * @param path the path (relative to the working directory if the argument represents a relative
     *            path)
     * @param name the name of the report
     * @param extension the extension of the report
     * @param reporter a consumer that writes to a PrintWriter
     */
    public static void report(String description, String path, String name, String extension, Consumer<PrintWriter> reporter) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
            String timeStamp = LocalDateTime.now().format(formatter);
            Path reportDir = Files.createDirectories(Paths.get(path));
            Path file = reportDir.resolve(name + "_" + timeStamp + "." + extension);
            Files.deleteIfExists(file);

            try (FileWriter fw = new FileWriter(Files.createFile(file).toFile())) {
                try (PrintWriter writer = new PrintWriter(fw)) {
                    System.out.println("Printing " + description + " to " + file.toAbsolutePath());
                    reporter.accept(writer);
                }
            }

        } catch (IOException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

}
