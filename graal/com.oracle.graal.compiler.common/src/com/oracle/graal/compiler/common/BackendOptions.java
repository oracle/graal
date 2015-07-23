/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common;

import static com.oracle.graal.compiler.common.BackendOptions.UserOptions.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import jdk.internal.jvmci.options.*;
import jdk.internal.jvmci.options.DerivedOptionValue.*;

/**
 * Options to control the backend configuration.
 */
public final class BackendOptions {

    public static class UserOptions {
        // @formatter:off
        @Option(help = "Destruct SSA LIR eagerly (before other LIR phases).", type = OptionType.Debug)
        public static final OptionValue<Boolean> LIREagerSSADestruction = new OptionValue<>(false);
        // @formatter:on
    }

    /* Create SSA LIR during LIR generation. */
    public static final DerivedOptionValue<Boolean> ConstructionSSAlirDuringLirBuilding = new DerivedOptionValue<>(new OptionSupplier<Boolean>() {
        private static final long serialVersionUID = 7657622005438210681L;

        public Boolean get() {
            return SSA_LIR.getValue();
        }
    });

    public enum LSRAVariant {
        NONSSA_LSAR,
        SSA_LSRA
    }

    public static final DerivedOptionValue<LSRAVariant> LinearScanVariant = new DerivedOptionValue<>(new OptionSupplier<LSRAVariant>() {
        private static final long serialVersionUID = 364925071685235153L;

        public LSRAVariant get() {
            if (SSA_LIR.getValue() && !LIREagerSSADestruction.getValue()) {
                return LSRAVariant.SSA_LSRA;
            }
            return LSRAVariant.NONSSA_LSAR;
        }
    });

    /* Does the backend emit stack to stack moves?. */
    public static final DerivedOptionValue<Boolean> ShouldOptimizeStackToStackMoves = new DerivedOptionValue<>(new OptionSupplier<Boolean>() {
        private static final long serialVersionUID = 2366072840509944317L;

        public Boolean get() {
            switch (LinearScanVariant.getValue()) {
                case SSA_LSRA:
                    return true;
            }
            return false;
        }
    });
}
