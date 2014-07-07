/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.test;

import java.io.*;
import java.util.*;

import junit.runner.*;

import org.junit.internal.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;

public class GraalJUnitCore {

    /**
     * Run the tests contained in the classes named in the <code>args</code>. A single test method
     * can be specified by adding #method after the class name. Only a single test can be run in
     * this way. If all tests run successfully, exit with a status of 0. Otherwise exit with a
     * status of 1. Write feedback while tests are running and write stack traces for all failed
     * tests after the tests all complete.
     *
     * @param args names of classes in which to find tests to run
     */
    public static void main(String... args) {
        JUnitSystem system = new RealSystem();
        JUnitCore junitCore = new JUnitCore();
        system.out().println("GraalJUnitCore");
        system.out().println("JUnit version " + Version.id());
        List<Class<?>> classes = new ArrayList<>();
        String methodName = null;
        List<Failure> missingClasses = new ArrayList<>();
        boolean verbose = false;
        boolean enableTiming = false;
        boolean color = false;
        boolean eagerStackTrace = false;
        boolean gcAfterTest = false;
        for (String each : expandArgs(args)) {
            if (each.charAt(0) == '-') {
                // command line arguments
                if (each.contentEquals("-JUnitVerbose")) {
                    verbose = true;
                } else if (each.contentEquals("-JUnitEnableTiming")) {
                    enableTiming = true;
                } else if (each.contentEquals("-JUnitColor")) {
                    color = true;
                } else if (each.contentEquals("-JUnitEagerStackTrace")) {
                    eagerStackTrace = true;
                } else if (each.contentEquals("-JUnitGCAfterTest")) {
                    gcAfterTest = true;
                } else {
                    system.out().println("Unknown command line argument: " + each);
                }

            } else {
                /*
                 * Entries of the form class#method are handled specially. Only one can be specified
                 * on the command line as there's no obvious way to build a runner for multiple
                 * ones.
                 */
                if (methodName != null) {
                    system.out().println("Only a single class and method can be specified: " + each);
                    System.exit(1);
                } else if (each.contains("#")) {
                    String[] pair = each.split("#");
                    if (pair.length != 2) {
                        system.out().println("Malformed class and method request: " + each);
                        System.exit(1);
                    } else if (classes.size() != 0) {
                        system.out().println("Only a single class and method can be specified: " + each);
                        System.exit(1);
                    } else {
                        methodName = pair[1];
                        each = pair[0];
                    }
                }
                try {
                    classes.add(Class.forName(each));
                } catch (ClassNotFoundException e) {
                    system.out().println("Could not find class: " + each);
                    Description description = Description.createSuiteDescription(each);
                    Failure failure = new Failure(description, e);
                    missingClasses.add(failure);
                }
            }
        }
        GraalJUnitRunListener graalListener;
        if (!verbose) {
            graalListener = new GraalTextListener(system);
        } else {
            graalListener = new GraalVerboseTextListener(system);
        }
        if (enableTiming) {
            graalListener = new TimingDecorator(graalListener);
        }
        if (color) {
            graalListener = new AnsiTerminalDecorator(graalListener);
        }
        if (eagerStackTrace) {
            graalListener = new EagerStackTraceDecorator(graalListener);
        }
        if (gcAfterTest) {
            graalListener = new GCAfterTestDecorator(graalListener);
        }
        junitCore.addListener(GraalTextListener.createRunListener(graalListener));
        Request request;
        if (methodName == null) {
            request = Request.classes(classes.toArray(new Class[0]));
        } else {
            request = Request.method(classes.get(0), methodName);
        }
        Result result = junitCore.run(request);
        for (Failure each : missingClasses) {
            result.getFailures().add(each);
        }
        System.exit(result.wasSuccessful() ? 0 : 1);
    }

    /**
     * Expand any arguments starting with @ and return the resulting argument array.
     *
     * @param args
     * @return the expanded argument array
     */
    private static String[] expandArgs(String[] args) {
        List<String> result = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.length() > 0 && arg.charAt(0) == '@') {
                if (result == null) {
                    result = new ArrayList<>();
                    for (int j = 0; j < i; j++) {
                        result.add(args[j]);
                    }
                    expandArg(arg.substring(1), result);
                }
            } else if (result != null) {
                result.add(arg);
            }
        }
        return result != null ? result.toArray(new String[0]) : args;
    }

    /**
     * Add each line from {@code filename} to the list {@code args}.
     *
     * @param filename
     * @param args
     */
    private static void expandArg(String filename, List<String> args) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(filename));

            String buf;
            while ((buf = br.readLine()) != null) {
                args.add(buf);
            }
            br.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(2);
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
                System.exit(3);
            }
        }
    }
}
