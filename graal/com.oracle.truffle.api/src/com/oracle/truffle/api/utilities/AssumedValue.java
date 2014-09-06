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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.*;

/**
 * A {@link CompilationFinal} value combined with an {@link Assumption} to notify when it changes.
 * Note that you should be careful that modifications to this value do not cause deoptimization
 * loops. This could be by using a value that is monotonic.
 */
public class AssumedValue<T> {

    private final String name;

    @CompilationFinal private T value;
    @CompilationFinal private Assumption assumption;

    public AssumedValue(T initialValue) {
        this(null, initialValue);
    }

    public AssumedValue(String name, T initialValue) {
        this.name = name;
        value = initialValue;
        assumption = Truffle.getRuntime().createAssumption(name);
    }

    /**
     * Get the current value, updating it if it has been {@link #set}. The compiler may be able to
     * make this method return a constant value, but still accommodate mutation.
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
     */
    public void set(T newValue) {
        CompilerDirectives.transferToInterpreter();

        value = newValue;
        final Assumption oldAssumption = assumption;
        assumption = Truffle.getRuntime().createAssumption(name);
        oldAssumption.invalidate();
    }

}
