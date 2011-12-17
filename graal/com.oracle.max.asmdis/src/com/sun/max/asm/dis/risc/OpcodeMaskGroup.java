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
package com.sun.max.asm.dis.risc;

import java.util.*;

import com.sun.max.asm.gen.risc.*;
import com.sun.max.collect.*;

/**
 * An opcode mask group is a collection of templates that share the same opcode mask.
 * Some templates in the group may also share the same opcode.
 */
public class OpcodeMaskGroup {

    private final int mask;

    public OpcodeMaskGroup(int mask) {
        this.mask = mask;
    }

    public int mask() {
        return mask;
    }

    private final Set<RiscTemplate> templates = new HashSet<RiscTemplate>();

    private final IntHashMap<List<RiscTemplate>> templatesForOpcodes = new IntHashMap<List<RiscTemplate>>();
    private final List<RiscTemplate> empty = new LinkedList<RiscTemplate>();

    public void add(RiscTemplate template) {
        assert template.opcodeMask() == mask;
        templates.add(template);
        List<RiscTemplate> templatesForOpcode = templatesForOpcodes.get(template.opcode());
        if (templatesForOpcode == null) {
            templatesForOpcode = new LinkedList<RiscTemplate>();
            templatesForOpcodes.put(template.opcode(), templatesForOpcode);
        }
        templatesForOpcode.add(template);
    }

    public List<RiscTemplate> templatesFor(int opcode) {
        final List<RiscTemplate> result = templatesForOpcodes.get(opcode);
        if (result == null) {
            return empty;
        }
        return result;
    }

    public Collection<RiscTemplate> templates() {
        return templates;
    }
}
