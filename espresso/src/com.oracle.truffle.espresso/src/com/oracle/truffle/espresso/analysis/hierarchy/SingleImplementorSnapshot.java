/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.analysis.hierarchy;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.utilities.NeverValidAssumption;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;

public class SingleImplementorSnapshot {
    private final Assumption hasSingleImplementor;
    private final ObjectKlass implementor;

    static SingleImplementorSnapshot Invalid = new SingleImplementorSnapshot(NeverValidAssumption.INSTANCE, null);

    // package-private: only oracle can create snapshots
    SingleImplementorSnapshot(Assumption hasSingleImplementor, ObjectKlass implementor) {
        this.hasSingleImplementor = hasSingleImplementor;
        this.implementor = implementor;
    }

    public Assumption hasImplementor() {
        return hasSingleImplementor;
    }

    @CompilerDirectives.TruffleBoundary
    private static void reportInvalidValueAccess() {
        throw EspressoError.shouldNotReachHere("Accessed the value behind an invalid assumption");
    }

    public ObjectKlass getImplementor() {
        if (!hasSingleImplementor.isValid()) {
            reportInvalidValueAccess();
        }
        return implementor;
    }
}
