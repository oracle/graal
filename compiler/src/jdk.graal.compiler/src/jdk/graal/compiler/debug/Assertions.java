/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug;

import java.util.Arrays;

import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;

/**
 * Utility for query whether assertions are enabled.
 */
public class Assertions {
    /**
     * Determines if assertions are enabled. Strictly speaking, this may only be true for the
     * {@link Assertions} class but we assume assertions are enabled/disabled for Graal as a whole.
     */
    public static boolean assertionsEnabled() {
        boolean enabled = false;
        assert (enabled = true) == true : "Enabling assertions";
        return enabled;
    }

    /**
     * Determines if detailed assertions are enabled. This requires that the normal assertions are
     * also enabled.
     *
     * @param values the current OptionValues that might define a value for DetailAsserts.
     */
    public static boolean detailedAssertionsEnabled(OptionValues values) {
        return assertionsEnabled() && Options.DetailedAsserts.getValue(values);
    }

    // @formatter:off
    public static class Options {

        @Option(help = "Enable expensive assertions if normal assertions (i.e. -ea or -esa) are enabled.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DetailedAsserts = new OptionKey<>(false);

    }
    // @formatter:on

    private static String formatAssertionContextArgV(Object... args) {
        StringBuilder sb = new StringBuilder();
        assert args.length % 2 == 0 : "Must have an equal nr of entries to format context: " + Arrays.toString(args);
        int entries = args.length / 2;
        int entriesDone = 0;
        for (int i = 0; i < args.length; i += 2) {
            Object val = args[i + 1];
            sb.append(args[i]).append("=");
            sb.append(decorateObjectErrorContext(val));
            if (++entriesDone < entries) {
                sb.append(";");
            }
        }
        return sb.toString();
    }

    /**
     * Returns a string representation of the given object, enhancing it with additional contextual
     * information if such data is available.
     */
    public static Object decorateObjectErrorContext(Object o) {
        try {
            if (o instanceof HIRBlock b) {
                return b.toString(Verbosity.All);
            } else if (o instanceof ValueNode v) {
                return v.toString(Verbosity.All);
            } else {
                return o;
            }
        } catch (Throwable e) {
            return "Error calling toString on object " + e.getMessage();
        }
    }

    /**
     * Returns an enhanced error message for the supplied string and object. It considers arguments
     * one by one. Each argument is represented by its {@code toString} representation enhanced with
     * {@code decorateObjectErrorContext(arg[i])}. It uses
     * {@link #decorateObjectErrorContext(Object)} for the {@code toString} of the values.
     *
     * For example the following code
     *
     * <pre>
     * LoopBeginNode loopBegin = getLoopBegin();
     * Assertions.errorMessage("Message", loopBeginNode);
     * </pre>
     *
     * would be formatted to (for an arbitrary loop begin node with {@code id=6}, output is cropped
     * for brevity):
     *
     * {@code [0]=Message;[1]=6|LoopBegin { guestLoopEndsSafepointState...}}.
     *
     */
    public static String errorMessage(Object... args) {
        StringBuilder sb = new StringBuilder();
        final int entries = args.length;
        int entriesDone = 0;
        for (int i = 0; i < args.length; i++) {
            Object val = args[i];
            sb.append("[").append(i).append("]=");
            sb.append(decorateObjectErrorContext(val));
            if (++entriesDone < entries) {
                sb.append(";");
            }
        }
        return sb.toString();
    }

    /**
     * Returns an enhanced error message for the supplied string and object. It considers arguments
     * pair wise in a key - value fashion. Each pair is then represented in its {@code toString}
     * representation of the form {@code key=decorateObjectErrorContext(value)}. It uses
     * {@link #decorateObjectErrorContext(Object)} for the {@code toString} of the values.
     *
     * For example the following code
     *
     * <pre>
     * LoopBeginNode loopBegin = getLoopBegin();
     * Assertions.errorMessageContext("Message", loopBeginNode);
     * </pre>
     *
     * would be formatted to (for an arbitrary loop begin node with {@code id=6}, output is cropped
     * for brevity):
     *
     * {@code Message=6|LoopBegin { guestLoopEndsSafepointState=ENABLED, splits=0,
     * cloneFromNodeId=-1}...}.
     *
     */
    public static String errorMessageContext(String s1, Object o1) {
        return formatAssertionContextArgV(s1, o1);
    }

    /**
     * See {@link #errorMessageContext(String, Object)}.
     */
    public static String errorMessageContext(String s1, Object o1, String s2, Object o2) {
        return formatAssertionContextArgV(s1, o1, s2, o2);
    }

    /**
     * See {@link #errorMessageContext(String, Object)}.
     */
    public static String errorMessageContext(String s1, Object o1, String s2, Object o2, String s3, Object o3) {
        return formatAssertionContextArgV(s1, o1, s2, o2, s3, o3);
    }

    /**
     * See {@link #errorMessageContext(String, Object)}.
     */
    public static String errorMessageContext(String s1, Object o1, String s2, Object o2, String s3, Object o3, String s4, Object o4) {
        return formatAssertionContextArgV(s1, o1, s2, o2, s3, o3, s4, o4);
    }

    /**
     * See {@link #errorMessageContext(String, Object)}.
     */
    public static String errorMessageContext(String s1, Object o1, String s2, Object o2, String s3, Object o3, String s4, Object o4, String s5, Object o5) {
        return formatAssertionContextArgV(s1, o1, s2, o2, s3, o3, s4, o4, s5, o5);
    }

    /**
     * See {@link #errorMessageContext(String, Object)}.
     */
    public static String errorMessageContext(String s1, Object o1, String s2, Object o2, String s3, Object o3, String s4, Object o4, String s5, Object o5, String s6, Object o6) {
        return formatAssertionContextArgV(s1, o1, s2, o2, s3, o3, s4, o4, s5, o5, s6, o6);
    }

    /**
     * See {@link #errorMessageContext(String, Object)}.
     */
    public static String errorMessageContext(String s1, Object o1, String s2, Object o2, String s3, Object o3, String s4, Object o4, String s5, Object o5, String s6, Object o6, String s7,
                    Object o7) {
        return formatAssertionContextArgV(s1, o1, s2, o2, s3, o3, s4, o4, s5, o5, s6, o6, s7, o7);
    }

}
