/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.JavaVersion;

public abstract class JavaSubstitution extends SubstitutionProfiler {

    public abstract static class Factory {
        public abstract JavaSubstitution create(Meta meta);

        private final String[] methodName;
        private final String[] substitutionClassName;
        private final String returnType;
        private final String[] parameterTypes;
        private final boolean hasReceiver;

        public Factory(String methodName, String substitutionClassName, String returnType, String[] parameterTypes, boolean hasReceiver) {
            this.methodName = new String[]{methodName};
            this.substitutionClassName = new String[]{substitutionClassName};
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
            this.hasReceiver = hasReceiver;
        }

        public String[] getMethodNames() {
            return methodName;
        }

        public String[] substitutionClassNames() {
            return substitutionClassName;
        }

        public String returnType() {
            return returnType;
        }

        public String[] parameterTypes() {
            return parameterTypes;
        }

        public boolean hasReceiver() {
            return hasReceiver;
        }

        public boolean isValidFor(@SuppressWarnings("unused") JavaVersion version) {
            return true;
        }
    }

    JavaSubstitution() {
    }

    public abstract Object invoke(Object[] args);

    final EspressoContext getContext() {
        return EspressoContext.get(this);
    }

    @Override
    public JavaSubstitution split() {
        throw EspressoError.shouldNotReachHere();
    }
}
