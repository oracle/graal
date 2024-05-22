/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.FileSystems;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.junit.Assert;
import org.junit.Test;

public class OptionListsInProfilerDocsTest {
    private static final String[] instruments = new String[]{
                    "CPU Sampler", "cpu-sampler-options", "/docs/tools/profiling.md",
                    "CPU Tracer", "cpu-tracer-options", "/docs/tools/profiling.md",
                    "Memory Tracer", "mem-tracer-options", "/docs/tools/profiling.md"
    };
    private static final String HEAD = "<!-- ";
    private static final String BEGIN = "BEGIN: ";
    private static final String END = "END: ";
    private static final String TAIL = " -->";
    static int status = 0;

    @Test
    public void testOptionLists() throws FileNotFoundException {
        // Path to the graal repo root
        String graalPath = System.getProperty("user.dir") + FileSystems.getDefault().getSeparator() + ".." + FileSystems.getDefault().getSeparator() + ".." + FileSystems.getDefault().getSeparator() +
                        "graal";
        testInstruments(graalPath);
    }

    private static void testInstruments(String graalPath) throws FileNotFoundException {
        assert instruments.length % 3 == 0;
        for (int i = 0; i < instruments.length; i += 3) {
            final String instrumentName = instruments[i];
            final String tag = instruments[i + 1];
            final String documentPath = graalPath + instruments[i + 2];
            assertEquals(actual(documentPath, tag), expected(instrumentName), documentPath, instrumentName, tag);
        }
    }

    private static void assertEquals(List<String> actual, List<String> expected, String documentPath, String description, String tag) {
        if (!expected.equals(actual)) {
            StringBuilder errorBuilder = new StringBuilder();
            errorBuilder.append(System.lineSeparator());
            errorBuilder.append("ERROR: Documentation for " + description + " does not match expected.").append(System.lineSeparator());
            errorBuilder.append("Expected:").append(System.lineSeparator());
            for (String s : expected) {
                errorBuilder.append(s).append(System.lineSeparator());
            }
            errorBuilder.append(System.lineSeparator());

            final String docPath = documentPath.substring(documentPath.indexOf("graal"));
            errorBuilder.append("Actual (at " + docPath + "):").append(System.lineSeparator());
            for (String s : actual) {
                errorBuilder.append(s).append(System.lineSeparator());
            }
            errorBuilder.append(System.lineSeparator());
            errorBuilder.append("Please update the documentation between").append(System.lineSeparator());
            errorBuilder.append(begin(tag)).append(System.lineSeparator());
            errorBuilder.append("and").append(System.lineSeparator());
            errorBuilder.append(end(tag)).append(System.lineSeparator());
            errorBuilder.append("on " + docPath + " to match the expected!").append(System.lineSeparator());
            errorBuilder.append(System.lineSeparator());

            errorBuilder.append("Different lines: ").append(System.lineSeparator());
            for (int i = 0; i < Math.max(expected.size(), actual.size()); i++) {

                if (i >= expected.size()) {
                    errorBuilder.append("Line " + i).append(System.lineSeparator());
                    errorBuilder.append("Expected: no line").append(System.lineSeparator());
                    errorBuilder.append("  Actual: " + actual.get(i)).append(System.lineSeparator());
                } else if (i >= actual.size()) {
                    errorBuilder.append("Line " + i).append(System.lineSeparator());
                    errorBuilder.append("Expected: " + expected.get(i)).append(System.lineSeparator());
                    errorBuilder.append("  Actual: no line").append(System.lineSeparator());
                } else if (!actual.get(i).equals(expected.get(i))) {
                    errorBuilder.append("Line " + i).append(System.lineSeparator());
                    errorBuilder.append("Expected: " + expected.get(i)).append(System.lineSeparator());
                    errorBuilder.append("  Actual: " + actual.get(i)).append(System.lineSeparator());
                }
            }
            Assert.fail(errorBuilder.toString());
        }
    }

    private static List<String> expected(String instrumentName) {
        List<String> expected = new LinkedList<>();
        try (Engine engine = Engine.create()) {
            for (Instrument instrument : engine.getInstruments().values()) {
                if (instrument.getName().equals(instrumentName)) {
                    for (OptionDescriptor option : instrument.getOptions()) {
                        if (option.getCategory() == OptionCategory.USER || option.getCategory() == OptionCategory.EXPERT) {
                            Collections.addAll(expected, format(option));
                        }
                    }

                }
            }
        }
        return expected;
    }

    private static String[] format(OptionDescriptor option) {
        StringBuilder sb = new StringBuilder("- `--");
        sb.append(option.getName());
        if (option.isOptionMap()) {
            sb.append(".<key>");
        }
        String usageSyntax = option.getUsageSyntax();
        if (usageSyntax != null && !usageSyntax.isEmpty()) {
            sb.append("=");
            sb.append(usageSyntax);
        }
        sb.append("` : ");
        final String help = websiteReadyHelp(option.getHelp());
        sb.append(help);
        final String string = sb.toString();
        return string.split(System.lineSeparator());
    }

    private static String websiteReadyHelp(String help) {
        return help.replace("%n", System.lineSeparator() + "  ");
    }

    private static List<String> actual(String documentPath, String tag) throws FileNotFoundException {
        List<String> expected = new LinkedList<>();
        try (Scanner scanner = new Scanner(new File(documentPath))) {
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (line.startsWith("```")) {
                    continue; // ignore markdown code blocks
                } else {
                    expected.add(line);
                }
            }
        }
        final int begin = expected.indexOf(begin(tag));
        if (begin < 0) {
            throw new IllegalStateException("Missing " + begin(tag) + " in " + documentPath);
        }
        final int end = expected.indexOf(end(tag));
        if (end < 0) {
            throw new IllegalStateException("Missing " + end(tag) + " in " + documentPath);
        }
        return expected.subList(begin + 1, end);
    }

    private static String end(String tag) {
        return HEAD + END + tag + TAIL;
    }

    private static String begin(String tag) {
        return HEAD + BEGIN + tag + TAIL;
    }
}
