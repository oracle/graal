/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.sparc;

import com.oracle.graal.api.code.*;

public class SPARCScratchRegister implements AutoCloseable {
    private final ThreadLocal<Boolean> locked = new ThreadLocal<>();
    private final ThreadLocal<Exception> where = new ThreadLocal<>();
    private final Register register;
    private static final SPARCScratchRegister scratch1 = new SPARCScratchRegister(SPARC.g3);
    private static final SPARCScratchRegister scratch2 = new SPARCScratchRegister(SPARC.g1);

    private SPARCScratchRegister(Register register) {
        super();
        this.register = register;
    }

    public Register getRegister() {
        if (locked.get() == null) {
            locked.set(false);
        }
        boolean isLocked = locked.get();
        if (isLocked) {
            where.get().printStackTrace();
            throw new RuntimeException("Temp Register is already taken!");
        } else {
            where.set(new Exception());
            locked.set(true);
            return register;
        }
    }

    public void close() {
        boolean isLocked = locked.get();
        if (isLocked) {
            locked.set(false);
        } else {
            throw new RuntimeException("Temp Register is not taken!");
        }
    }

    public static SPARCScratchRegister get() {
        if (scratch1.isLocked()) {
            return scratch2;
        } else {
            return scratch1;
        }
    }

    public boolean isLocked() {
        Boolean isLocked = locked.get();
        if (isLocked == null) {
            return false;
        } else {
            return isLocked;
        }
    }
}
