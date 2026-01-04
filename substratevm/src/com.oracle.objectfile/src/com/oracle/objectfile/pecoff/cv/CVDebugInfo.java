/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.pecoff.cv;

import java.nio.ByteOrder;

import com.oracle.objectfile.debugentry.DebugInfoBase;
import com.oracle.objectfile.pecoff.PECoffMachine;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;

/**
 * CVDebugInfo is a container class for all the CodeView sections to be emitted in the object file.
 * Currently, those are.debug$S (CVSymbolSectionImpl) and .debug$T (CVTypeSectionImpl).
 */
public final class CVDebugInfo extends DebugInfoBase {

    private final CVSymbolSectionImpl cvSymbolSection;
    private final CVTypeSectionImpl cvTypeSection;
    private DebugContext debugContext;

    /* Register constants for Windows x86_64 */
    /* See AMD64ReservedRegisters.java. */
    public static final byte RHEAPBASE_X86 = (byte) 14;
    public static final byte RTHREAD_X86 = (byte) 15;

    private final byte heapbaseRegister;
    private final byte threadRegister;

    public CVDebugInfo(PECoffMachine machine, ByteOrder byteOrder) {
        super(byteOrder);
        cvSymbolSection = new CVSymbolSectionImpl(this);
        cvTypeSection = new CVTypeSectionImpl(this);
        if (machine == PECoffMachine.X86_64) {
            this.heapbaseRegister = RHEAPBASE_X86;
            this.threadRegister = RTHREAD_X86;
        } else {
            /* room for future aach64 port */
            throw GraalError.shouldNotReachHere("Unsupported architecture on Windows"); // ExcludeFromJacocoGeneratedReport
        }
    }

    public CVSymbolSectionImpl getCVSymbolSection() {
        return cvSymbolSection;
    }

    public CVTypeSectionImpl getCVTypeSection() {
        return cvTypeSection;
    }

    public byte getHeapbaseRegister() {
        return heapbaseRegister;
    }

    @SuppressWarnings("unused")
    public byte getThreadRegister() {
        return threadRegister;
    }

    public DebugContext getDebugContext() {
        return debugContext;
    }

    void setDebugContext(DebugContext debugContext) {
        this.debugContext = debugContext;
    }
}
