/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.graph;

import static com.sun.cri.bytecode.Bytecodes.*;

import com.sun.c1x.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ri.*;

/**
 * A temporary placeholder to bring in profile information to Graal, until we have a definitive interface.
 * That allows us to mark dependencies in code that will need fixup in the future.
 *
 * TODO this class and the signature of its methods i temporary
 */
public class ProfileInformationStub {

    public static boolean trappedFrequently(RiMethod method, int bci) {

        // TODO: Currently, the runtime system does not support deoptimization when a call trhows an exception,
        // so mark is a trapping so that explicit exception handler edges are generated.
        switch (Bytes.beU1(method.code(), bci)) {
            case INVOKESTATIC:
            case INVOKESPECIAL:
            case INVOKEVIRTUAL:
            case INVOKEINTERFACE: {
                return true;
            }
        }

        if (C1XOptions.StressImplicitExceptions && canTrap(method.code()[bci])) {
            return true;
        }

        return false;
    }

}
