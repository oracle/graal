/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.meta;

import java.util.Arrays;

import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;

/**
 * Sorted lookup table of OSR backedge targets for one Ristretto method.
 *
 * The interpreter only considers bytecode indices reached by backward branches as OSR entries. This
 * table is built once from the method bytecodes, then queried on every interpreted backedge without
 * allocating or hashing.
 */
final class RistrettoOSRBackedgeTable {
    /**
     * Shared immutable table for methods that have no backward branches.
     */
    private static final RistrettoOSRBackedgeTable EMPTY = new RistrettoOSRBackedgeTable(new int[0], new RistrettoOSRBackedgeState[0]);

    /**
     * Sorted unique bytecode indices reached by backward branches.
     */
    private final int[] targetBCIs;

    /**
     * Mutable per-target OSR state. Entry {@code entries[i]} belongs to {@code targetBCIs[i]}.
     */
    private final RistrettoOSRBackedgeState[] entries;

    private RistrettoOSRBackedgeTable(int[] targetBCIs, RistrettoOSRBackedgeState[] entries) {
        this.targetBCIs = targetBCIs;
        this.entries = entries;
    }

    /**
     * Creates the per-method OSR table from bytecodes, or reuses the empty table when no loop target is
     * present.
     */
    static RistrettoOSRBackedgeTable create(byte[] code) {
        int[] targets = MethodProfile.collectBackedgeTargets(code);
        assert isStrictlySorted(targets);
        if (targets.length == 0) {
            return EMPTY;
        }
        RistrettoOSRBackedgeState[] entries = new RistrettoOSRBackedgeState[targets.length];
        for (int i = 0; i < targets.length; i++) {
            entries[i] = new RistrettoOSRBackedgeState(targets[i]);
        }
        return new RistrettoOSRBackedgeTable(targets, entries);
    }

    private static boolean isStrictlySorted(int[] targets) {
        for (int i = 1; i < targets.length; i++) {
            if (targets[i - 1] >= targets[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Looks up mutable OSR state for a target BCI.
     */
    RistrettoOSRBackedgeState lookup(int targetBCI) {
        int index = Arrays.binarySearch(targetBCIs, targetBCI);
        return index >= 0 ? entries[index] : null;
    }

    boolean hasBackedges() {
        return entries.length != 0;
    }

    /**
     * Invalidates every OSR installed-code entry in the table.
     */
    void invalidateAll() {
        for (RistrettoOSRBackedgeState entry : entries) {
            entry.invalidateInstalledCode();
        }
    }

    void resetCompilationStateForTesting() {
        for (RistrettoOSRBackedgeState entry : entries) {
            entry.resetCompilationStateForTesting();
        }
    }

    /**
     * Invalidates the single entry that still owns {@code expectedInstalledCode}.
     */
    int invalidateInstalledCode(SubstrateInstalledCodeImpl expectedInstalledCode) {
        for (RistrettoOSRBackedgeState entry : entries) {
            if (entry.invalidateInstalledCode(expectedInstalledCode)) {
                return entry.targetBCI();
            }
        }
        return -1;
    }
}
