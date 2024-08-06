/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.nodes.EspressoNode;

@GenerateUncached
@NodeInfo(shortName = "class initcheck")
public abstract class InitCheck extends EspressoNode {

    protected static final int LIMIT = 1;

    public abstract void execute(ObjectKlass klass);

    @Specialization(limit = "LIMIT", guards = "cachedKlass == klass")
    void doCached(ObjectKlass klass,
                    @Cached("klass") ObjectKlass cachedKlass) {
        if (!klass.isInitialized()) {
            // Deopt loop if class initialization fails.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            cachedKlass.safeInitialize();
        }
    }

    @Specialization(replaces = "doCached")
    void doGeneric(ObjectKlass klass) {
        if (CompilerDirectives.isPartialEvaluationConstant(klass)) {
            if (!klass.isInitialized()) {
                // Deopt loop if class initialization fails.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                klass.safeInitialize();
            }
        } else {
            initCheckBoundary(klass);
        }
    }

    @TruffleBoundary(allowInlining = true)
    static void initCheckBoundary(ObjectKlass klass) {
        if (!klass.isInitialized()) {
            klass.safeInitialize();
        }
    }
}
