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

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.NodeView;
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
            formatObjectContext(sb, val);
            if (++entriesDone < entries) {
                sb.append(";");
            }
        }
        return sb.toString();
    }

    private static void formatObjectContext(StringBuilder sb, Object val) {
        try {
            if (val instanceof HIRBlock b) {
                sb.append(b.toString(Verbosity.All));
            } else if (val instanceof ValueNode v) {
                sb.append(v.toString(Verbosity.All));
                sb.append("/");
                Stamp stamp = v.stamp(NodeView.DEFAULT);
                sb.append(stamp).append(" ");
                sb.append(v.getStackKind());
                sb.append("\\");
            } else {
                sb.append(val);
            }
        } catch (Throwable e) {
            sb.append("Error calling toString on object ").append(e.getMessage());
        }
    }

    public static String errorMessage(Object... args) {
        StringBuilder sb = new StringBuilder();
        int entries = args.length;
        int entriesDone = 0;
        for (int i = 0; i < args.length; i++) {
            Object val = args[i];
            sb.append("[").append(i).append("]=");
            formatObjectContext(sb, val);
            if (++entriesDone < entries) {
                sb.append(";");
            }
        }
        return sb.toString();
    }

    public static String errorMessageContext(String s1, Object o1) {
        return formatAssertionContextArgV(s1, o1);
    }

    public static String errorMessageContext(String s1, Object o1, String s2, Object o2) {
        return formatAssertionContextArgV(s1, o1, s2, o2);
    }

    public static String errorMessageContext(String s1, Object o1, String s2, Object o2, String s3, Object o3) {
        return formatAssertionContextArgV(s1, o1, s2, o2, s3, o3);
    }

    public static String errorMessageContext(String s1, Object o1, String s2, Object o2, String s3, Object o3, String s4, Object o4) {
        return formatAssertionContextArgV(s1, o1, s2, o2, s3, o3, s4, o4);
    }

    public static String errorMessageContext(String s1, Object o1, String s2, Object o2, String s3, Object o3, String s4, Object o4, String s5, Object o5) {
        return formatAssertionContextArgV(s1, o1, s2, o2, s3, o3, s4, o4, s5, o5);
    }

    public static String errorMessageContext(String s1, Object o1, String s2, Object o2, String s3, Object o3, String s4, Object o4, String s5, Object o5, String s6, Object o6) {
        return formatAssertionContextArgV(s1, o1, s2, o2, s3, o3, s4, o4, s5, o5, s6, o6);
    }

    public static String errorMessageContext(String s1, Object o1, String s2, Object o2, String s3, Object o3, String s4, Object o4, String s5, Object o5, String s6, Object o6, String s7,
                    Object o7) {
        return formatAssertionContextArgV(s1, o1, s2, o2, s3, o3, s4, o4, s5, o5, s6, o6, s7, o7);
    }

}
