/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.proxy;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

//JaCoCo Exclude

public final class SpeculationLogProxy extends CompilationProxyBase implements SpeculationLog {
    SpeculationLogProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(SpeculationLog.class, name, params);
    }

    private static final SymbolicMethod collectFailedSpeculationsMethod = method("collectFailedSpeculations");
    private static final InvokableMethod collectFailedSpeculationsInvokable = (receiver, args) -> {
        ((SpeculationLog) receiver).collectFailedSpeculations();
        return null;
    };

    @Override
    public void collectFailedSpeculations() {
        handle(collectFailedSpeculationsMethod, collectFailedSpeculationsInvokable);
    }

    public static final SymbolicMethod maySpeculateMethod = method("maySpeculate", SpeculationReason.class);
    private static final InvokableMethod maySpeculateInvokable = (receiver, args) -> ((SpeculationLog) receiver).maySpeculate((SpeculationReason) args[0]);

    @Override
    public boolean maySpeculate(SpeculationReason reason) {
        return (boolean) handle(maySpeculateMethod, maySpeculateInvokable, reason);
    }

    public static final SymbolicMethod speculateMethod = method("speculate", SpeculationReason.class);
    private static final InvokableMethod speculateInvokable = (receiver, args) -> ((SpeculationLog) receiver).speculate((SpeculationReason) args[0]);

    @Override
    public Speculation speculate(SpeculationReason reason) {
        return (Speculation) handle(speculateMethod, speculateInvokable, reason);
    }

    private static final SymbolicMethod hasSpeculationsMethod = method("hasSpeculations");
    private static final InvokableMethod hasSpeculationsInvokable = (receiver, args) -> ((SpeculationLog) receiver).hasSpeculations();

    @Override
    public boolean hasSpeculations() {
        return (boolean) handle(hasSpeculationsMethod, hasSpeculationsInvokable);
    }

    private static final SymbolicMethod lookupSpeculationMethod = method("lookupSpeculation", JavaConstant.class);
    private static final InvokableMethod lookupSpeculationInvokable = (receiver, args) -> ((SpeculationLog) receiver).lookupSpeculation((JavaConstant) args[0]);

    @Override
    public Speculation lookupSpeculation(JavaConstant constant) {
        return (Speculation) handle(lookupSpeculationMethod, lookupSpeculationInvokable, constant);
    }
}
