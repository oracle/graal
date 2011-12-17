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
package com.sun.max.asm.dis;

import com.sun.max.asm.gen.*;

/**
 * A label for an absolute address.
 */
public class DisassembledLabel {

    private final DisassembledObject disassembledObject;
    private final ImmediateArgument address;

    public DisassembledLabel(Object addressOrDisassembledObject, String name) {
        if (addressOrDisassembledObject instanceof ImmediateArgument) {
            address = (ImmediateArgument) addressOrDisassembledObject;
            disassembledObject = null;
        } else {
            address = null;
            disassembledObject = (DisassembledObject) addressOrDisassembledObject;
        }
        this.name = name;
    }

    private final String name;

    public String name() {
        return name;
    }

    /**
     * Gets the disassembled object (if any) denoted by this label.
     */
    public DisassembledObject target() {
        return disassembledObject;
    }

    public ImmediateArgument address() {
        return disassembledObject != null ? disassembledObject.startAddress() : address;
    }
}
