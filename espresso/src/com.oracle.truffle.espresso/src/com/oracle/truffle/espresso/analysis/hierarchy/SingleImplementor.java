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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.impl.ObjectKlass;

/**
 * Represents a single implementor of a class or an interface. Throughout its lifetime,
 * {@code currentState} of {@code SingleImplementor} undergoes up to 3 states in the following
 * order:
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
    @CompilationFinal private volatile AssumptionGuardedValue<ObjectKlass> currentState;
    @SuppressWarnings("rawtypes") private static final AtomicReferenceFieldUpdater<SingleImplementor, AssumptionGuardedValue> STATE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(
                    SingleImplementor.class,
                    AssumptionGuardedValue.class, "currentState");

    static final AssumptionGuardedValue<ObjectKlass> NoImplementorsState = AssumptionGuardedValue.createInvalid();
    static final SingleImplementor MultipleImplementors = new SingleImplementor(AssumptionGuardedValue.createInvalid());
    static final AssumptionGuardedValue<ObjectKlass> MultipleImplementorsState = MultipleImplementors.read();

    // Used only to create MultipeImplementors instance
    private SingleImplementor(AssumptionGuardedValue<ObjectKlass> state) {
        this.currentState = state;
    }

    SingleImplementor() {
        this.currentState = NoImplementorsState;
    }

    SingleImplementor(ObjectKlass implementor) {
        this.currentState = AssumptionGuardedValue.create(implementor);
    }

    void addImplementor(ObjectKlass.KlassVersion implementor) {
        // Implementors are only added when the implementing class is loaded, which happens in the
        // interpreter. This allows to keep {@code value} and {@code hasValue} compilation final.
        CompilerAsserts.neverPartOfCompilation();

        if (currentState == MultipleImplementorsState) {
            return;
        }
        AssumptionGuardedValue<ObjectKlass> singleImplementor = AssumptionGuardedValue.create(implementor.getKlass());
        if (!STATE_UPDATER.compareAndSet(this, NoImplementorsState, singleImplementor)) {
            // CAS failed, i.e. there already exists an implementor
            AssumptionGuardedValue<ObjectKlass> state = currentState;
            // adding the same implementor repeatedly, so the class / interface still has a single
            // implementor
            if (state.value == implementor.getKlass()) {
                return;
            }
            while (!STATE_UPDATER.compareAndSet(this, state, MultipleImplementorsState)) {
                state = currentState;
            }
            // whoever executed the CAS successfully is responsible for invalidating the assumption
            state.hasValue().invalidate("Single implementor invalidated");
        }
    }

    public AssumptionGuardedValue<ObjectKlass> read() {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(currentState);
        return currentState;
    }
}
