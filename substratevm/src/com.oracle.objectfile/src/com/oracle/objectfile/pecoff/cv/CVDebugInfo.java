/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2024, Red Hat Inc. All rights reserved.
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

import jdk.vm.ci.code.Register;
import jdk.vm.ci.amd64.AMD64;

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
    /* See AMD64ReservedRegisters.java (which we can't import here). */
    public static final Register HEAP_BASE_REGISTER = AMD64.r14;
    public static final Register THREAD_REGISTER = AMD64.r15;
    public static final int POINTER_LENGTH = 8;

    public CVDebugInfo(PECoffMachine machine, ByteOrder byteOrder) {
        super(byteOrder);
        cvSymbolSection = new CVSymbolSectionImpl(this);
        cvTypeSection = new CVTypeSectionImpl(this);
        if (machine != PECoffMachine.X86_64) {
            /* room for future aach64 port */
            throw GraalError.shouldNotReachHere("Unsupported Windows architecture"); // ExcludeFromJacocoGeneratedReport
        }
    }

    public CVSymbolSectionImpl getCVSymbolSection() {
        return cvSymbolSection;
    }

    public CVTypeSectionImpl getCVTypeSection() {
        return cvTypeSection;
    }

    public byte getHeapbaseRegister() {
        return (byte) HEAP_BASE_REGISTER.number;
    }

    @SuppressWarnings("unused")
    public byte getThreadRegister() {
        return (byte) THREAD_REGISTER.number;
    }

    public DebugContext getDebugContext() {
        return debugContext;
    }

    void setDebugContext(DebugContext debugContext) {
        this.debugContext = debugContext;
    }
}
