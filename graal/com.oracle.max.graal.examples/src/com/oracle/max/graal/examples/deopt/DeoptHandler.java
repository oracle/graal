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
package com.oracle.max.graal.examples.deopt;

import java.lang.reflect.*;

import com.sun.cri.ri.*;


public class DeoptHandler {

    /**
     * Deoptimization handler method for methods with a void return parameter.
     */
    public void handle_void(RiMethod method, int bci, Object[] values, int numLocals, int numStack, int numLocks) {
        handle(method, bci, values, numLocals, numStack, numLocks);
    }

    /**
     * Deoptimization handler method for methods with an int return parameter.
     */
    public int handle_int(RiMethod method, int bci, Object[] values, int numLocals, int numStack, int numLocks) {
        handle(method, bci, values, numLocals, numStack, numLocks);
        return 123;
    }

    /**
     * Deoptimization handler method for methods with an object return parameter.
     */
    public Object handle_object(RiMethod method, int bci, Object[] values, int numLocals, int numStack, int numLocks) {
        handle(method, bci, values, numLocals, numStack, numLocks);
        return null;
    }

    /**
     * Deoptimization handler method: prints the current state of the method execution.
     */
    public int handle(RiMethod method, int bci, Object[] values, int numLocals, int numStack, int numLocks) {
        System.out.printf("Deoptimization: %s@%d", method.name(), bci);
        int p = 0;
        System.out.print("\nArguments: ");
        int argCount = method.signature().argumentCount(!Modifier.isStatic(method.accessFlags()));
        for (int i = 0; i < argCount; i++) {
            System.out.printf("%s ", values[p++]);
        }
        System.out.print("\nLocals: ");
        for (int i = argCount; i < numLocals; i++) {
            System.out.printf("%s ", values[p++]);
        }
        System.out.print("\nExpression stack: ");
        for (int i = 0; i < numStack; i++) {
            System.out.printf("%s ", values[p++]);
        }
        System.out.print("\nLocks: ");
        for (int i = 0; i < numLocks; i++) {
            System.out.printf("%s ", values[p++]);
        }
        System.out.println();
        return 2;
    }

}
