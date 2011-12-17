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
package com.sun.max.ide;

import junit.framework.*;

/**
 * To pass command line arguments to a JUnit test, set the system property "max.test.arguments" on the VM command line or
 * where ever VM arguments are specified in the IDE. Multiple command line arguments must be separated with a space or
 * a colon. For example, to pass these arguments to a JUnit test:
 * <p><pre>
 *     -vmclasses
 *     -jcklist=test/test/com/sun/max/vm/verifier/jck.classes.txt
 * </pre><p>
 * requires defining the system property on the VM command line as follows:
 * <p><pre>
 *     -Dmax.test.arguments=-vmclasses -jcklist=test/test/com/sun/max/vm/verifier/jck.classes.txt
 * </pre><p>
 * or:
 * <p><pre>
 *     -Dmax.test.arguments=-vmclasses:-jcklist=test/test/com/sun/max/vm/verifier/jck.classes.txt
 * </pre><p>
 * If using the latter (colon-separated) form, then colons in an argument must be escaped with a backslash.
 */
public abstract class MaxTestCase extends TestCase {

    public MaxTestCase() {
        this(null);
        setName(getClass().getName());
    }

    public MaxTestCase(String name) {
        super(name);
    }

    public static final String PROGRAM_ARGUMENTS_SYSTEM_PROPERTY = "max.test.arguments";

    private static String[] programArguments;

    public static String[] getProgramArguments() {
        if (programArguments == null) {
            String args = System.getProperty(PROGRAM_ARGUMENTS_SYSTEM_PROPERTY);
            if (args != null) {
                args = args.replace("\\:", "\u0000");
                programArguments = args.split("[\\s:]+");
                for (int i = 0; i != programArguments.length; ++i) {
                    programArguments[i] = programArguments[i].replace("\u0000", ":");
                }
            } else {
                programArguments = new String[0];
            }
        }
        return programArguments;
    }

    public static void setProgramArguments(String[] args) {
        programArguments = args.clone();
    }
}
