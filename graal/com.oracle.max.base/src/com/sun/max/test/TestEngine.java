/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.test;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import com.sun.max.*;
import com.sun.max.program.*;
import com.sun.max.util.*;

/**
 * The {@code TestEngine} class implements the basic test engine for the testing framework, including loading of test
 * cases and organizing their output.
 */
public class TestEngine {

    protected int verbose = 2;

    protected final LinkedList<TestCase> allTests;
    protected final LinkedList<TestCase> failTests;
    protected final LinkedList<TestCase> passTests;
    protected final LinkedList<File> skipFiles;
    protected final Queue<TestCase> queue;
    protected final Registry<TestHarness> registry;
    protected int finished;
    protected ProgressPrinter progress;

    public TestEngine(Registry<TestHarness> registry) {
        allTests = new LinkedList<TestCase>();
        failTests = new LinkedList<TestCase>();
        passTests = new LinkedList<TestCase>();
        skipFiles = new LinkedList<File>();
        queue = new LinkedList<TestCase>();
        this.registry = registry;
    }

    public static void main(String[] args) {
        final TestEngine e = new TestEngine(new Registry<TestHarness>(TestHarness.class, true));
        e.parseAndRunTests(args);
        e.report(System.out);
    }

    public synchronized void addTest(TestCase testCase) {
        testCase.testNumber = allTests.size();
        allTests.add(testCase);
        queue.offer(testCase);
    }

    public synchronized void skipFile(File file) {
        skipFiles.add(file);
    }

    public void setVerboseLevel(int level) {
        verbose = level;
    }

    public void report(PrintStream stream) {
        progress.report();
        if (skipFiles.size() > 0) {
            stream.println(skipFiles.size() + " file(s) skipped");
            for (File f : skipFiles) {
                stream.println(f.getName());
            }
        }
    }

    public void parseAndRunTests(String[] args) {
        this.parseAndRunTests(args, null);
    }

    public void parseAndRunTests(String[] args, String filter) {
        parseTests(args, true, filter);
        progress = new ProgressPrinter(System.out, allTests.size(), verbose, false);
        for (TestCase tcase = queue.poll(); tcase != null; tcase = queue.poll()) {
            runTest(tcase);
        }
    }

    public void parseTests(String[] args, boolean sort) {
        this.parseTests(args, sort, null);
    }

    public void parseTests(String[] args, boolean sort, String filter) {
        for (String arg : args) {
            final File f = new File(arg);
            parseTests(f, registry, sort, filter);
        }
    }

    public Iterable<TestCase> getAllTests() {
        return allTests;
    }

    private synchronized TestCase dequeue() {
        return queue.remove();
    }

    private void runTest(TestCase testCase) {
        try {
            // run the test (records thrown exceptions internally)
            startTest(testCase);
            testCase.test();
            final Class<TestHarness<TestCase>> type = null;
            // evaluate the result of test
            final TestResult result = Utils.cast(type, testCase.harness).evaluateTest(this, testCase);
            testCase.result = result;
        } catch (Throwable t) {
            // there was an exception evaluating the result of the test
            testCase.result = new TestResult.UnexpectedException("Unexpected exception in test evaluation", t);
        } finally {
            finishTest(testCase);
        }
    }

    private synchronized void startTest(TestCase testCase) {
        progress.begin(testCase.file.toString());
    }

    private synchronized void finishTest(TestCase testCase) {
        final boolean passed = testCase.result.isSuccess();
        if (passed) {
            passTests.add(testCase);
            progress.pass();
        } else {
            failTests.add(testCase);
            progress.fail(testCase.result.failureMessage(testCase));
        }
    }

    private void parseTests(File file, Registry<TestHarness> reg, boolean sort, String filter) {
        if (!file.exists()) {
            throw new Error("file " + file + " not found.");
        }
        if (file.isDirectory()) {
            for (File dirFile : getFilesFromDirectory(file, sort)) {
                if (!dirFile.isDirectory()) {
                    parseFile(dirFile, reg, filter);
                }
            }
        } else {
            parseFile(file, reg, filter);
        }
    }

    private File[] getFilesFromDirectory(File dir, boolean sort) {
        final File[] list = dir.listFiles();
        if (sort) {
            Arrays.sort(list);
        }
        return list;
    }

    private void parseFile(File file, Registry<TestHarness> reg, String filter) {
        if (filter != null) {
            if (filter.startsWith("~")) {
                filter = filter.substring(1);
                if (!Pattern.compile(filter).matcher(file.getName()).find()) {
                    return;
                }
            } else {
                if (!file.getName().contains(filter)) {
                    return;
                }
            }
        }
        parseFile(file, reg);
    }

    private void parseFile(File file, Registry<TestHarness> reg) {
        try {
            final Properties props = parseTestProperties(file);
            final String hname = props.getProperty("Harness");

            if (hname != null) {
                // only try to create tests if a harness is specified.
                try {
                    final TestHarness harness = reg.getInstance(hname, false);
                    if (harness == null) {
                        throw ProgramError.unexpected("invalid harness: " + hname);
                    } else {
                        harness.parseTests(this, file, props);
                    }
                } catch (Throwable t) {
                    throw ProgramError.unexpected("unexpected exception while parsing " + file, t);
                }
            } else {
                skipFile(file);
            }
        } catch (FileNotFoundException e) {
            throw ProgramError.unexpected("file " + file + " not found.");
        } catch (IOException e) {
            throw ProgramError.unexpected(e);
        }
    }

    private Properties parseTestProperties(File file) throws FileNotFoundException, IOException {
        final BufferedReader reader = new BufferedReader(new FileReader(file));
        final Properties vars = new Properties();
        boolean lineFound = false;

        while (true) {
            // read any of the beginning lines that contain '@'
            final String line = reader.readLine();

            if (line == null) {
                break;
            }

            final int indx1 = line.indexOf('@');
            final int indx2 = line.indexOf(':');

            if (indx1 < 0 || indx2 < 0) {
                // this line does not match: break out if already matched
                if (lineFound) {
                    break;
                }
                continue;
            }
            lineFound = true;

            final String var = line.substring(indx1 + 1, indx2).trim();
            final String value = line.substring(indx2 + 1).trim();
            if (vars.get(var) != null) {
                // if there is already a value, append.
                vars.put(var, vars.get(var) + " " + value);
            } else {
                vars.put(var, value);
            }
        }
        reader.close();
        return vars;
    }

    private boolean loadingPackages;

    public boolean loadingPackages() {
        return loadingPackages;
    }

    public void setLoadingPackages(boolean loadingPackages) {
        this.loadingPackages = loadingPackages;
    }
}
