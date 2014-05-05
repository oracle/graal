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

import java.util.*;

import junit.runner.*;

import org.junit.internal.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;

public class GraalJUnitCore {

    /**
     * Run the tests contained in the classes named in the <code>args</code>. If all tests run
     * successfully, exit with a status of 0. Otherwise exit with a status of 1. Write feedback
     * while tests are running and write stack traces for all failed tests after the tests all
     * complete.
     *
     * @param args names of classes in which to find tests to run
     */
    public static void main(String... args) {
        JUnitSystem system = new RealSystem();
        JUnitCore junitCore = new JUnitCore();
        system.out().println("GraalJUnitCore");
        system.out().println("JUnit version " + Version.id());
        List<Class<?>> classes = new ArrayList<>();
        List<Failure> missingClasses = new ArrayList<>();
        boolean verbose = false;
        boolean enableTiming = false;
        for (String each : args) {
            if (each.charAt(0) == '-') {
                // command line arguments
                if (each.contentEquals("-JUnitVerbose")) {
                    verbose = true;
                } else if (each.contentEquals("-JUnitEnableTiming")) {
                    enableTiming = true;
                } else {
                    system.out().println("Unknown command line argument: " + each);
                }

            } else {
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
        junitCore.addListener(GraalTextListener.createRunListener(graalListener));
        Result result = junitCore.run(classes.toArray(new Class[0]));
        for (Failure each : missingClasses) {
            result.getFailures().add(each);
        }
        System.exit(result.wasSuccessful() ? 0 : 1);
    }
}
