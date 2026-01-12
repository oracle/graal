/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.modelimpl.cfg;

import at.ssw.visualizer.model.cfg.IRInstruction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public class IRInstructionImpl implements IRInstruction {

    private final String[] keys;
    private final String[] values;

    public IRInstructionImpl(LinkedHashMap<String, String> data) {
        String[] skeys = new String[data.size()];
        String[] svalues = new String[data.size()];
        int index = 0;
        for (Entry<String, String> e : data.entrySet()) {
            skeys[index] = e.getKey().intern();
            svalues[index++] = e.getValue().intern();
        }
        if (Arrays.equals(skeys, twoKeys)) {
            keys = twoKeys;
        } else {
            keys = skeys;
        }
        values = svalues;
    }

    public IRInstructionImpl(String pinned, int bci, int useCount, String name, String text, String operand) {
        final String p = checkIntern(pinned);
        final String b = Integer.toString(bci).intern();
        final String u = Integer.toString(useCount).intern();
        final String n = checkIntern(name);
        final String i = checkIntern(text);
        if (operand != null) {
            keys = withOperandKeys;
            values = new String[]{p, b, u, n, checkIntern(operand), i};
        } else {
            keys = noOperandKeys;
            values = new String[]{p, b, u, n, i};
        }
    }

    public IRInstructionImpl(int number, String text) {
        keys = twoKeys;
        values = new String[]{Integer.toString(number).intern(), checkIntern(text)};
    }

    static final String[] twoKeys = new String[]{LIR_NUMBER, LIR_TEXT};
    static final String[] noOperandKeys = new String[]{"p", "bci", "use", HIR_NAME, HIR_TEXT};
    static final String[] withOperandKeys = new String[]{"p", "bci", "use", HIR_NAME, HIR_OPERAND, HIR_TEXT};

    public Collection<String> getNames() {
        return Collections.unmodifiableList(Arrays.asList(keys));
    }

    public String getValue(String name) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(name)) {
                return values[i];
            }
        }
        return null;
    }

    private String checkIntern(String s) {
        if (s != s.intern()) {
            throw new InternalError("non-interned String passed to IRInstructionImpl constructor");
        }
        return s;
    }
}
