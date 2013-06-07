/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler;

import java.util.*;
import java.util.regex.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;

/**
 * Implements the filter specified by the {@link GraalDebugConfig#Dump},
 * {@link GraalDebugConfig#Log}, {@link GraalDebugConfig#Meter} and {@link GraalDebugConfig#Time}
 * options.
 * <p>
 * These options enable the associated debug facility if their filter matches the
 * {@linkplain DebugScope#getQualifiedName() name} of the {@linkplain Debug#currentScope() current
 * scope}.
 * <p>
 * A filter is a list of comma-separated terms. Each term is interpreted as a glob pattern if it
 * contains a "*" or "?" character. Otherwise, it is interpreted as a substring. If a term starts
 * with "~", then it is an positive term. An input is matched by a filter if any of its positive
 * terms match the input (or it has no positive terms) AND none of its negative terms match the
 * input (or it has no negative terms).
 * <p>
 * Examples of filters include:
 * <p>
 * <ul>
 * <li>
 * 
 * <pre>
 * &quot;&quot;
 * </pre>
 * 
 * Matches any scope.</li>
 * <li>
 * 
 * <pre>
 * &quot;*&quot;
 * </pre>
 * 
 * Matches any scope.</li>
 * <li>
 * 
 * <pre>
 * &quot;CodeGen,CodeInstall&quot;
 * </pre>
 * 
 * Matches a scope whose name contains "CodeGen" or "CodeInstall".</li>
 * <li>
 * 
 * <pre>
 * &quot;Code*&quot;
 * </pre>
 * 
 * Matches a scope whose name starts with "Code".</li>
 * <li>
 * 
 * <pre>
 * &quot;Code,&tilde;Dead&quot;
 * </pre>
 * 
 * Matches a scope whose name contains "Code" but does not contain "Dead".</li>
 * </ul>
 */
class DebugFilter {

    public static DebugFilter parse(String spec) {
        if (spec == null) {
            return null;
        }
        return new DebugFilter(spec.split(","));
    }

    final Term[] positive;
    final Term[] negative;

    DebugFilter(String[] terms) {
        List<Term> pos = new ArrayList<>(terms.length);
        List<Term> neg = new ArrayList<>(terms.length);
        for (int i = 0; i < terms.length; i++) {
            String t = terms[i];
            if (t.startsWith("~")) {
                neg.add(new Term(t.substring(1)));
            } else {
                pos.add(new Term(t));
            }
        }
        this.positive = pos.isEmpty() ? null : pos.toArray(new Term[pos.size()]);
        this.negative = neg.isEmpty() ? null : neg.toArray(new Term[neg.size()]);
    }

    /**
     * Determines if a given input is matched by this filter.
     */
    public boolean matches(String input) {
        boolean match = true;
        if (positive != null) {
            match = false;
            for (Term t : positive) {
                if (t.matches(input)) {
                    match = true;
                    break;
                }
            }
        }
        if (match && negative != null) {
            for (Term t : negative) {
                if (t.matches(input)) {
                    match = false;
                    break;
                }
            }
        }
        return match;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("DebugFilter[");
        String sep = "";
        if (positive != null) {
            buf.append(sep).append("pos=").append(Arrays.toString(positive));
            sep = ", ";
        }
        if (negative != null) {
            buf.append(sep).append("neg=").append(Arrays.toString(negative));
            sep = ", ";
        }
        return buf.append("]").toString();
    }

    static class Term {

        final Pattern pattern;

        public Term(String filter) {
            if (filter.isEmpty()) {
                this.pattern = null;
            } else if (filter.contains("*") || filter.contains("?")) {
                this.pattern = Pattern.compile(MethodFilter.createGlobString(filter));
            } else {
                this.pattern = Pattern.compile(".*" + MethodFilter.createGlobString(filter) + ".*");
            }
        }

        /**
         * Determines if a given input is matched by this filter.
         */
        public boolean matches(String input) {
            return pattern == null || pattern.matcher(input).matches();
        }

        @Override
        public String toString() {
            return pattern == null ? ".*" : pattern.toString();
        }
    }
}
