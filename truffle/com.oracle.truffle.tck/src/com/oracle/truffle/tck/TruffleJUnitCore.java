/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import junit.runner.Version;
import org.junit.internal.JUnitSystem;
import org.junit.internal.RealSystem;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.RunnerScheduler;

final class TruffleJUnitCore {

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
        system.out().println("TruffleJUnitCore");
        system.out().println("JUnit version " + Version.id());
        List<Class<?>> classes = new ArrayList<>();
        String methodName = null;
        List<Failure> missingClasses = new ArrayList<>();
        boolean verbose = false;
        boolean enableTiming = false;
        boolean failFast = false;
        boolean eagerStackTrace = false;
        boolean gcAfterTest = false;

        String[] expandedArgs = expandArgs(args);
        for (int i = 0; i < expandedArgs.length; i++) {
            String each = expandedArgs[i];
            if (each.charAt(0) == '-') {
                // command line arguments
                if (each.contentEquals("-JUnitVerbose")) {
                    verbose = true;
                } else if (each.contentEquals("-JUnitFailFast")) {
                    failFast = true;
                } else if (each.contentEquals("-JUnitEnableTiming")) {
                    enableTiming = true;
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
                    Class<?> cls = Class.forName(each, false, TruffleJUnitCore.class.getClassLoader());
                    if ((cls.getModifiers() & Modifier.ABSTRACT) == 0) {
                        classes.add(cls);
                    }
                } catch (ClassNotFoundException e) {
                    system.out().println("Could not find class: " + each);
                    Description description = Description.createSuiteDescription(each);
                    Failure failure = new Failure(description, e);
                    missingClasses.add(failure);
                }
            }
        }
        final TruffleTextListener textListener;
        if (!verbose) {
            textListener = new TruffleTextListener(system);
        } else {
            textListener = new TruffleVerboseTextListener(system);
        }
        TruffleJUnitRunListener listener = textListener;
        if (enableTiming) {
            listener = new TimingDecorator(listener);
        }
        if (eagerStackTrace) {
            listener = new EagerStackTraceDecorator(listener);
        }
        if (gcAfterTest) {
            listener = new GCAfterTestDecorator(listener);
        }
        junitCore.addListener(TruffleTextListener.createRunListener(listener));
        Request request;
        if (methodName == null) {
            request = Request.classes(classes.toArray(new Class<?>[0]));
            if (failFast) {
                Runner runner = request.getRunner();
                if (runner instanceof ParentRunner) {
                    ParentRunner<?> parentRunner = (ParentRunner<?>) runner;
                    parentRunner.setScheduler(new RunnerScheduler() {
                        public void schedule(Runnable childStatement) {
                            if (textListener.getLastFailure() == null) {
                                childStatement.run();
                            }
                        }

                        public void finished() {
                        }
                    });
                } else {
                    system.out().println("Unexpected Runner subclass " + runner.getClass().getName() + " - fail fast not supported");
                }
            }
        } else {
            if (failFast) {
                system.out().println("Single method selected - fail fast not supported");
            }
            request = Request.method(classes.get(0), methodName);
        }
        Result result = junitCore.run(request);
        for (Failure each : missingClasses) {
            result.getFailures().add(each);
        }
        System.exit(result.wasSuccessful() ? 0 : 1);
    }

    /**
     * Gets the command line for the current process.
     *
     * @return the command line arguments for the current process or {@code null} if they are not
     *         available
     */
    public static List<String> getProcessCommandLine() {
        String processArgsFile = System.getenv().get("MX_SUBPROCESS_COMMAND_FILE");
        if (processArgsFile != null) {
            try {
                return Files.readAllLines(new File(processArgsFile).toPath(), Charset.forName("UTF-8"));
            } catch (IOException e) {
            }
        }
        return null;
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
