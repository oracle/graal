/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

/**
 * A value that the compiler can assume is constant, but can be changed by invalidation.
 * <p>
 * Compiled code that uses the value will be invalidated each time the value changes, so you should
 * take care to only change values infrequently, or to monitor the number of times the value has
 * changed and at some point to replace the value with something more generic so that it does not
 * have to be changed and code does not have to keep being recompiled.
 *
 * @since 0.8 or earlier
 */
public class AssumedValue<T> {

    private final String name;

    // value behaves as volatile by piggybacking on Assumption semantics
    @CompilationFinal private T value;
    @CompilationFinal private volatile Assumption assumption;

    @SuppressWarnings("rawtypes") private static final AtomicReferenceFieldUpdater<AssumedValue, Assumption> ASSUMPTION_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(AssumedValue.class, Assumption.class, "assumption");

    /** @since 0.8 or earlier */
    public AssumedValue(T initialValue) {
        this(null, initialValue);
    }

    /** @since 0.8 or earlier */
    public AssumedValue(String name, T initialValue) {
        this.name = name;
        value = initialValue;
        assumption = Truffle.getRuntime().createAssumption(name);
    }

    /**
     * Get the current value, updating it if it has been {@link #set}. The compiler may be able to
     * make this method return a constant value, but still accommodate mutation.
     *
     * @since 0.8 or earlier
     */
    public T get() {
        try {
            assumption.check();
        } catch (InvalidAssumptionException e) {
            // No need to rewrite anything - just pick up the new values
        }

        return value;
    }

    /**
     * Set a new value, which will be picked up the next time {@link #get} is called.
     *
     * @since 0.8 or earlier
     */
    public void set(T newValue) {
        CompilerDirectives.transferToInterpreter();
        value = newValue;

        Assumption newAssumption = Truffle.getRuntime().createAssumption(name);
        Assumption oldAssumption = ASSUMPTION_UPDATER.getAndSet(this, newAssumption);
        oldAssumption.invalidate();
    }

}
