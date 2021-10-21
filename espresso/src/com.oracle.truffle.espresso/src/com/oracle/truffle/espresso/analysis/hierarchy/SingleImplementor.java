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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.utilities.NeverValidAssumption;
import com.oracle.truffle.espresso.impl.ObjectKlass;

/**
 * Represents a single implementor of a class or an interface. Throughout its lifetime, an instance
 * of {@code SingleImplementor} undergoes up to 3 states in the following order:
 * <p>
 * 1) no implementor: {@code value == null}, {@code hasValue} is invalid
 * <p>
 * 2) exactly one implementor: {@code value == implementor}, {@code hasValue} is valid (reset to a
 * different assumption object than in state (1))
 * <p>
 * 3) multiple implementors: {@code value == null}, {@code hasValue} is invalid
 * <p>
 * {@code SingleImplementor} for concrete classes starts in state (2): the implementor is the class
 * itself; {@code SingleImplementor} for abstract classes and interfaces starts in state (1).
 */
public final class SingleImplementor {
    @CompilationFinal private volatile SingleImplementorSnapshot currentSnapshot;
    private static final AtomicReferenceFieldUpdater<SingleImplementor, SingleImplementorSnapshot> SNAPSHOT_UPDATER = AtomicReferenceFieldUpdater.newUpdater(SingleImplementor.class,
                    SingleImplementorSnapshot.class, "currentSnapshot");

    static SingleImplementorSnapshot NoImplementorsSnapshot = new SingleImplementorSnapshot(NeverValidAssumption.INSTANCE, null);
    static SingleImplementor Invalid = new SingleImplementor(NeverValidAssumption.INSTANCE, null);
    static SingleImplementorSnapshot MultipleImplementorsSnapshot = Invalid.read();

    // Used only to create the Invalid instance
    private SingleImplementor(Assumption assumption, ObjectKlass value) {
        this.currentSnapshot = new SingleImplementorSnapshot(assumption, value);
    }

    SingleImplementor() {
        this.currentSnapshot = NoImplementorsSnapshot;
    }

    SingleImplementor(ObjectKlass implementor) {
        this(Truffle.getRuntime().createAssumption("single implementor"), implementor);
    }

    void addImplementor(ObjectKlass implementor) {
        // Implementors are only added when the implementing class is loaded, which happens in the
        // interpreter. This allows to keep {@code value} and {@code hasValue} compilation final.
        CompilerAsserts.neverPartOfCompilation();

        if (currentSnapshot == MultipleImplementorsSnapshot) {
            return;
        }
        SingleImplementorSnapshot singleImplementor = new SingleImplementorSnapshot(Truffle.getRuntime().createAssumption("single implementor"), implementor);
        if (!SNAPSHOT_UPDATER.compareAndSet(this, NoImplementorsSnapshot, singleImplementor)) {
            // CAS failed, i.e. there already exists an implementor
            SingleImplementorSnapshot snapshot = currentSnapshot;
            // adding the same implementor repeatedly, so the class / interface still has a single
            // implementor
            if (snapshot.implementor == implementor) {
                return;
            }
            while (!SNAPSHOT_UPDATER.compareAndSet(this, snapshot, MultipleImplementorsSnapshot)) {
                snapshot = currentSnapshot;
            }
            // whoever executed the CAS successfully is responsible for invalidating the assumption
            snapshot.hasImplementor().invalidate();
        }
    }

    public SingleImplementorSnapshot read() {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(currentSnapshot);
        return currentSnapshot;
    }
}
