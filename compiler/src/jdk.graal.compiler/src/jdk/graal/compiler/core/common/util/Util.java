/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.util;

import java.util.List;

import jdk.graal.compiler.debug.TTY;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The {@code Util} class contains a motley collection of utility methods used throughout the
 * compiler.
 */
public class Util {

    /**
     * Statically cast an object to an arbitrary Object type. Dynamically checked.
     */
    @SuppressWarnings("unchecked")
    public static <T> T uncheckedCast(Object object) {
        return (T) object;
    }

    /**
     * Sets the element at a given position of a list and ensures that this position exists. If the
     * list is current shorter than the position, intermediate positions are filled with a given
     * value.
     *
     * @param list the list to put the element into
     * @param pos the position at which to insert the element
     * @param x the element that should be inserted
     * @param filler the filler element that is used for the intermediate positions in case the list
     *            is shorter than pos
     */
    public static <T> void atPutGrow(List<T> list, int pos, T x, T filler) {
        if (list.size() < pos + 1) {
            while (list.size() < pos + 1) {
                list.add(filler);
            }
            assert list.size() == pos + 1 : "Size " + list.size() + " must be pos+1, pos=" + pos;
        }

        assert list.size() >= pos + 1 : "Size " + list.size() + " must be pos+1, pos=" + pos;
        list.set(pos, x);
    }

    /**
     * Prepends the String {@code indentation} to every line in String {@code lines}, including a
     * possibly non-empty line following the final newline.
     */
    public static String indent(String lines, String indentation) {
        if (lines.length() == 0) {
            return lines;
        }
        final String newLine = "\n";
        if (lines.endsWith(newLine)) {
            return indentation + (lines.substring(0, lines.length() - 1)).replace(newLine, newLine + indentation) + newLine;
        }
        return indentation + lines.replace(newLine, newLine + indentation);
    }

    /**
     * Print a HotSpot-style inlining message to the console.
     */
    public static void printInlining(final ResolvedJavaMethod method, final int bci, final int inliningDepth, final boolean success, final String msg, final Object... args) {
        StringBuilder sb = new StringBuilder();
        // 1234567
        sb.append("        ");     // print timestamp
        // 1234
        sb.append("     ");        // print compilation number
        // % s ! b n
        sb.append(String.format("%c%c%c%c%c ", ' ', method.isSynchronized() ? 's' : ' ', ' ', ' ', method.isNative() ? 'n' : ' '));
        sb.append("     ");        // more indent
        sb.append("    ");         // initial inlining indent
        for (int i = 0; i < inliningDepth; i++) {
            sb.append("  ");
        }
        sb.append(String.format("@ %d  %s   %s%s", bci, methodName(method), success ? "" : "not inlining ", String.format(msg, args)));
        TTY.println(sb.toString());
    }

    private static String methodName(ResolvedJavaMethod method) {
        return method.format("%H.%n(%p):%r") + " (" + method.getCodeSize() + " bytes)";
    }

    /**
     * Formats the stack trace represented by {@code ste} to a string.
     */
    public static String toString(StackTraceElement[] ste) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : ste) {
            sb.append('\t').append(e).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
