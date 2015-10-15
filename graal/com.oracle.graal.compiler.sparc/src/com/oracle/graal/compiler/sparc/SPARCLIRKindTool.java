/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.sparc;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.sparc.SPARCKind;

import com.oracle.graal.compiler.common.spi.LIRKindTool;

public class SPARCLIRKindTool implements LIRKindTool {

    public LIRKind getIntegerKind(int bits) {
        if (bits <= 8) {
            return LIRKind.value(SPARCKind.BYTE);
        } else if (bits <= 16) {
            return LIRKind.value(SPARCKind.HWORD);
        } else if (bits <= 32) {
            return LIRKind.value(SPARCKind.WORD);
        } else {
            assert bits <= 64;
            return LIRKind.value(SPARCKind.XWORD);
        }
    }

    public LIRKind getFloatingKind(int bits) {
        switch (bits) {
            case 32:
                return LIRKind.value(SPARCKind.SINGLE);
            case 64:
                return LIRKind.value(SPARCKind.DOUBLE);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public LIRKind getObjectKind() {
        return LIRKind.reference(SPARCKind.XWORD);
    }

    public LIRKind getWordKind() {
        return LIRKind.value(SPARCKind.XWORD);
    }
}
