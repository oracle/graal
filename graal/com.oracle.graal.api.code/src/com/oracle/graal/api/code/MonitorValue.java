/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

import com.oracle.graal.api.meta.*;

/**
 * Represents lock information in the debug information.
 */
public final class MonitorValue extends Value {
    private static final long serialVersionUID = 8241681800464483691L;

    private Value owner;
    private final Value lockData;
    private final boolean eliminated;

    public MonitorValue(Value owner, Value lockData, boolean eliminated) {
        super(Kind.Illegal);
        this.owner = owner;
        this.lockData = lockData;
        this.eliminated = eliminated;
    }

    public Value getOwner() {
        return owner;
    }

    public void setOwner(Value newOwner) {
        this.owner = newOwner;
    }

    public Value getLockData() {
        return lockData;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    @Override
    public String toString() {
        return "monitor[" + owner + (lockData != null ? ", " + lockData : "") + (eliminated ? ", eliminated" : "") + "]";
    }
}
