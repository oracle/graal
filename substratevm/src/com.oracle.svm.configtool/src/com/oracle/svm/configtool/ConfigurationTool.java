/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configtool;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;

import com.oracle.svm.configtool.json.JsonWriter;
import com.oracle.svm.configtool.trace.TraceProcessor;

public class ConfigurationTool {
    private static void printUsage() {
        System.err.println("Usage: --trace file [--reflect-output file] [--jni-output file] [--proxy-output file] [--resources-output file]");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("No arguments provided.");
            printUsage();
            return;
        }
        try {
            Iterator<String> argsIter = Arrays.asList(args).iterator();
            String first = argsIter.next();
            if (first.equals("--trace")) {
                processTrace(argsIter);
            } else {
                System.err.println("Unknown argument: " + first);
                printUsage();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processTrace(Iterator<String> argsIter) throws IOException {
        Path traceInputPath;
        Path reflectOutputPath = null;
        Path jniOutputPath = null;
        Path proxyOutputPath = null;
        Path resourcesOutputPath = null;
        try {
            traceInputPath = Paths.get(argsIter.next());
            while (argsIter.hasNext()) {
                String current = argsIter.next();
                switch (current) {
                    case "--reflect-output":
                        reflectOutputPath = Paths.get(argsIter.next());
                        break;
                    case "--jni-output":
                        jniOutputPath = Paths.get(argsIter.next());
                        break;
                    case "--proxy-output":
                        proxyOutputPath = Paths.get(argsIter.next());
                        break;

                    case "--resources-output":
                        resourcesOutputPath = Paths.get(argsIter.next());
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown argument: " + current);
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            printUsage();
            return;
        }

        TraceProcessor p = new TraceProcessor();
        p.process(Files.newBufferedReader(traceInputPath));
        if (reflectOutputPath != null) {
            try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(reflectOutputPath))) {
                p.getReflectionConfiguration().printJson(writer);
            }
        }
        if (jniOutputPath != null) {
            try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(jniOutputPath))) {
                p.getJniConfiguration().printJson(writer);
            }
        }
        if (proxyOutputPath != null) {
            try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(proxyOutputPath))) {
                p.getProxyConfiguration().printJson(writer);
            }
        }
        if (resourcesOutputPath != null) {
            try (Writer writer = Files.newBufferedWriter(resourcesOutputPath)) {
                p.getResourceConfiguration().write(writer);
            }
        }
    }
}
