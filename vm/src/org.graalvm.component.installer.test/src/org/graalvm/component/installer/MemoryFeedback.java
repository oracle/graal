/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

import static org.junit.Assert.assertEquals;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.graalvm.component.installer.MemoryFeedback.Memory;

/**
 *
 * @author odouda
 */
public final class MemoryFeedback implements Feedback, Iterable<Memory> {

    @Override
    public Iterator<Memory> iterator() {
        return new Iterator<>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < mem.size();
            }

            @Override
            public Memory next() {
                return mem.get(i++);
            }
        };
    }

    public enum Case {
        ERR("Error"),
        FLR("Failure"),
        MSG("Message"),
        FRM("Format"),
        INP("Input");

        final String msg;

        Case(String msg) {
            this.msg = msg;
        }

        @Override
        public String toString() {
            return msg;
        }

    }

    final List<Memory> mem = new ArrayList<>();
    boolean verb = true;
    boolean silent = false;
    Function<Boolean, String> lineAccept = null;

    public final class Memory {

        public final boolean silent;
        public final Case cas;
        public final String key;
        public final Object[] params;
        public final Throwable t;

        Memory(final Case cas, final String key, final Object[] params, Throwable t) {
            this.key = key;
            this.params = params;
            this.cas = cas;
            this.t = t;
            this.silent = MemoryFeedback.this.silent;
        }

        Memory(final Case cas, final String key, final Object[] params) {
            this(cas, key, params, null);
        }

        Memory(String response) {
            this(Case.INP, response, new Object[0]);
        }

        @Override
        public String toString() {
            return cas + ": " + key + ": " + Arrays.toString(params) + (t == null ? "" : ": " + t.getClass()) + "; silent=" + this.silent;
        }
    }

    public boolean isEmpty() {
        return mem.isEmpty();
    }

    public int size() {
        return mem.size();
    }

    private static void assEq(Object exp, Object obj, Supplier<String> msg) {
        try {
            assertEquals(exp, obj);
        } catch (AssertionError ae) {
            throw new AssertionError(msg.get() + " " + ae.getLocalizedMessage());
        }
    }

    private static Supplier<String> form(String format, Object... args) {
        return () -> String.format(format, args);
    }

    private static final String CASE = "Feedback type.";
    private static final String KEY = "Feedback type:\"%s\" key.";
    private static final String FEEDBACK = "Feedback type:\"%s\" key:\"%s\"";

    public Memory checkMem(int index, Case cas, String key) {
        return checkMem(index, cas, key, true);
    }

    private Memory checkMem(int index, Case cas, String key, boolean paramCheck) {
        Memory m = mem.get(index);
        assertEquals(CASE, cas, m.cas);
        assEq(key, m.key, form(KEY, cas));
        if (paramCheck && m.params.length > 0) {
            System.out.printf("Unchecked Feedback parameters:\n\t" + FEEDBACK + "\n\tParameters: %s\n", cas, key, Arrays.toString(m.params));
        }
        return m;
    }

    private static final String PARAMS_LENGTH = FEEDBACK + " parameter count.";
    private static final String PARAM = FEEDBACK + " parameter.";

    public Memory checkMem(int index, Case cas, String key, Object... params) {
        Memory m = checkMem(index, cas, key, false);
        assEq(params.length, m.params.length, form(PARAMS_LENGTH, cas, key));
        for (int i = 0; i < params.length; ++i) {
            assEq(params[i], m.params[i], form(PARAM, cas, key));
        }
        return m;
    }

    @Override
    public void error(String key, Throwable t, Object... params) {
        mem.add(new Memory(Case.ERR, key, params, t));
    }

    @Override
    public RuntimeException failure(String key, Throwable t, Object... params) {
        mem.add(new Memory(Case.FLR, key, params, t));
        return null;
    }

    @Override
    public String l10n(String key, Object... params) {
        mem.add(new Memory(Case.FRM, key, params));
        return null;
    }

    @Override
    public void message(String bundleKey, Object... params) {
        mem.add(new Memory(Case.MSG, bundleKey, params));
    }

    @Override
    public void output(String bundleKey, Object... params) {
        mem.add(new Memory(Case.MSG, bundleKey, params));
    }

    @Override
    public void outputPart(String bundleKey, Object... params) {
        mem.add(new Memory(Case.MSG, bundleKey, params));
    }

    @Override
    public String toString() {
        return "Feedback memory dump:\n\t" + mem.stream().sequential().map(m -> m.toString()).collect(Collectors.joining("\n\t"));
    }

    @Override
    public boolean verbosePart(String bundleKey, Object... params) {
        return verb;
    }

    @Override
    public boolean verboseOutput(String bundleKey, Object... params) {
        return verb;
    }

    @Override
    public <T> Feedback withBundle(Class<T> clazz) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean verbatimOut(String msg, boolean verbose) {
        return verbose;
    }

    @Override
    public boolean verbatimPart(String msg, boolean verbose) {
        return verbose;
    }

    @Override
    public boolean verbatimPart(String msg, boolean error, boolean verbose) {
        return verbose;
    }

    @Override
    public boolean backspace(int chars, boolean beVerbose) {
        return verb;
    }

    @Override
    public boolean isNonInteractive() {
        return verb;
    }

    @Override
    public String acceptLine(boolean autoYes) {
        String response = autoYes ? AUTO_YES : (lineAccept == null ? null : lineAccept.apply(autoYes));
        mem.add(new Memory(response));
        return response;
    }

    @Override
    public char[] acceptPassword() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void addLocalFileCache(URL location, Path local) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Path getLocalCache(URL location) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isSilent() {
        return this.silent;
    }

    @Override
    public boolean setSilent(boolean silent) {
        boolean wasSilent = this.silent;
        this.silent = silent;
        return wasSilent;
    }
}
