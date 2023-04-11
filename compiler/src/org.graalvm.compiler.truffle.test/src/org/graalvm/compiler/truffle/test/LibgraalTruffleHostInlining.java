/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.Truffle;

/**
 * See mx_vm_gate.py#.
 */
public class LibgraalTruffleHostInlining {

    public static void main(String[] args) {
        switch (args[0]) {
            case "initRuntime":
                Truffle.getRuntime();
                break;
            case "noRuntime":
                break;
            default:
                throw new IllegalArgumentException(args[0]);
        }

        byte[] bc = new byte[42];
        for (int i = 0; i < bc.length; i++) {
            bc[i] = (byte) (i % 7);
        }

        // we explicitly do not initialize the Truffle runtime here.
        for (int i = 0; i < 10000000; i++) {
            execute(bc);
        }
    }

    @BytecodeInterpreterSwitch
    public static void execute(byte[] ops) {
        int bci = 0;
        while (bci < ops.length) {
            switch (ops[bci++]) {
                case 0:
                    trivial();
                    break;
                case 1:
                    trivial();
                    break;
                case 2:
                    trivial();
                    break;
                case 3:
                    trivial();
                    break;
                case 4:
                    boundary();
                    // first level of recursion is inlined
                    break;
                case 5:
                    boundary();
                    // can be inlined is still monomorphic (with profile)
                    break;
                case 6:
                    trivial();
                    break;
                case 7:
                    trivial();
                    break;
            }
        }
    }

    private static void trivial() {

    }

    @TruffleBoundary
    private static void boundary() {

    }

}
