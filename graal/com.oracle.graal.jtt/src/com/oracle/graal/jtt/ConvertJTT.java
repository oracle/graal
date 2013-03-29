/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
/*
 */
package com.oracle.graal.jtt;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/**
 * Simple Utility to convert java tester tests from the proprietary test format into JUnit - tests.
 */
public class ConvertJTT {

    public static void main(String[] args) throws IOException {
        String targetPath = "graalvm/graal/com.oracle.graal.tests/src/com/oracle/max/graal/jtt";
        String sourcePath = "maxine/com.oracle.max.vm/test/jtt";

        File target = new File(targetPath);
        for (File dir : new File(sourcePath).listFiles()) {
            if (dir.isDirectory()) {
                String packageName = dir.getName();
                if (packageName.equals("exbytecode") || packageName.equals("max")) {
                    continue;
                }
                File targetDir = new File(target, packageName);
                for (File file : dir.listFiles()) {
                    if (file.getName().endsWith(".java")) {
                        targetDir.mkdirs();
                        try {
                            processFile(file.toPath(), new File(targetDir, file.getName()).toPath(), packageName);
                        } catch (RuntimeException e) {
                            throw new RuntimeException(String.format("Exception while processing file %s", file.getAbsolutePath()), e);
                        }
                    }
                }
            }
        }
    }

    public static class Run {

        public String input;
        public String output;

        public Run(String input, String output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public String toString() {
            return String.format("%16s = %s", input, output);
        }
    }

    private static void processFile(Path file, Path target, String packageName) throws IOException {
        List<String> lines = Files.readAllLines(file, Charset.forName("UTF-8"));
        Iterator<String> iter = lines.iterator();

        ArrayList<String> output = new ArrayList<>();
        ArrayList<Run> runs = new ArrayList<>();

        String line;
        boolean javaHarness = false;
        while (iter.hasNext()) {
            line = iter.next();
            if (line.startsWith(" * Copyright (c) ")) {
                output.add(" * Copyright (c) " + line.substring(17, 21) + ", 2012, Oracle and/or its affiliates. All rights reserved.");
            } else if (line.contains("@Runs:")) {
                line = line.substring(line.indexOf("@Runs:") + 6).trim();
                if (line.endsWith(";")) {
                    line = line.substring(0, line.length() - 1);
                }
                String[] runStrings;
                if (charCount(line, ';') == charCount(line, '=') - 1) {
                    runStrings = line.split(";");
                } else if (charCount(line, ',') == charCount(line, '=') - 1) {
                    runStrings = line.split(",");
                } else if (charCount(line, ',', ';') == charCount(line, '=') - 1) {
                    runStrings = line.split("[,;]");
                } else {
                    throw new RuntimeException("invalid run line: " + line);
                }
                for (String runString : runStrings) {
                    String[] split = runString.split("=");
                    if (split.length != 2) {
                        throw new RuntimeException("invalid run string: " + runString);
                    }
                    Run run = new Run(split[0].trim(), split[1].trim());
                    runs.add(run);
                }
            } else if (line.contains("@Harness:")) {
                if (line.contains("@Harness: java")) {
                    javaHarness = true;
                }
            } else if (line.startsWith("package jtt.")) {
                output.add("package com.oracle.graal.jtt." + packageName + ";");
                output.add("");
                output.add("import org.junit.*;");
            } else if (line.contains("@NEVER_INLINE")) {
                output.add("// " + line);
            } else if (line.startsWith("import com.sun.max.annotate.")) {
                // do nothing
            } else if (line.equals("}")) {
                if (runs != null) {
                    int n = 0;
                    for (Run run : runs) {
                        processRun(output, run, n++);
                    }
                    runs = null;
                }
                output.add(line);
            } else {
                // line = line.replace(oldClassName, newClassName);
                line = line.replace(" jtt.", " com.oracle.graal.jtt.");
                output.add(line);
            }
        }
        if (!javaHarness) {
            throw new RuntimeException("no java harness");
        }
        if (runs != null) {
            throw new RuntimeException("no ending brace found");
        }

        Files.write(target, output, Charset.forName("UTF-8"));
    }

    private static void processRun(ArrayList<String> output, Run run, int n) {
        if (run.output.startsWith("!")) {
            output.add("    @Test(expected = " + run.output.substring(1).replace("jtt.", "com.oracle.graal.jtt.").replace('$', '.') + ".class)");
            output.add("    public void run" + n + "() throws Throwable {");
            output.add("        test(" + parameters(run.input) + ");");
            output.add("    }");
            output.add("");
        } else {
            output.add("    @Test");
            output.add("    public void run" + n + "() throws Throwable {");
            String result = parameters(run.output);
            if (result.endsWith("f") || result.endsWith("d") || result.endsWith("F") || result.endsWith("D")) {
                output.add("        Assert.assertEquals(" + result + ", test(" + parameters(run.input) + "), 0);");
            } else {
                output.add("        Assert.assertEquals(" + result + ", test(" + parameters(run.input) + "));");
            }
            output.add("    }");
            output.add("");
        }
    }

    private static String parameters(String params) {
        if (params.startsWith("(")) {
            StringBuilder str = new StringBuilder();
            String[] split = params.substring(1, params.length() - 1).split(",");
            for (int i = 0; i < split.length; i++) {
                str.append(i == 0 ? "" : ", ").append(parameters(split[i].trim()));
            }
            return str.toString();
        } else if (params.startsWith("`")) {
            return params.substring(1);
        } else {
            if (params.length() <= 1) {
                return params;
            } else {
                if (params.endsWith("s")) {
                    return "((short) " + params.substring(0, params.length() - 1) + ")";
                } else if (params.endsWith("c")) {
                    return "((char) " + params.substring(0, params.length() - 1) + ")";
                } else if (params.endsWith("b")) {
                    return "((byte) " + params.substring(0, params.length() - 1) + ")";
                }
            }
            return params.replace("jtt.", "com.oracle.graal.jtt.");
        }
    }

    private static int charCount(String str, char ch1) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch1) {
                count++;
            }
        }
        return count;
    }

    private static int charCount(String str, char ch1, char ch2) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch1 || str.charAt(i) == ch2) {
                count++;
            }
        }
        return count;
    }

}
