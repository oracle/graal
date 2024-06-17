/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.JavaValue;

public abstract class ReservedRegisters {

    @Fold
    public static ReservedRegisters singleton() {
        return ImageSingletons.lookup(ReservedRegisters.class);
    }

    protected final Register frameRegister;
    protected final Register threadRegister;
    protected final Register heapBaseRegister;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected ReservedRegisters(Register frameRegister, Register threadRegister, Register heapBaseRegisterCandidate) {
        this.frameRegister = frameRegister;
        this.threadRegister = threadRegister;
        this.heapBaseRegister = SubstrateOptions.SpawnIsolates.getValue() ? heapBaseRegisterCandidate : null;
    }

    /**
     * Returns the register used as the frame pointer.
     */
    public Register getFrameRegister() {
        return frameRegister;
    }

    /**
     * Returns the register that contains the current {@link IsolateThread}; or null if no thread
     * register is used.
     */
    public Register getThreadRegister() {
        return threadRegister;
    }

    /**
     * Returns the register holding the heap base address for compressed pointers, i.e., the current
     * {@link Isolate}; or null if no heap base register is used.
     */
    public Register getHeapBaseRegister() {
        return heapBaseRegister;
    }

    /**
     * Returns true if the provided value is a {@link RegisterValue} for a reserved register that is
     * allowed to be in a frame state, i.e., for a reserved register that can be handled by
     * deoptimization.
     */
    public boolean isAllowedInFrameState(JavaValue value) {
        if (value instanceof RegisterValue) {
            Register register = ((RegisterValue) value).getRegister();
            if (register.equals(threadRegister) || register.equals(heapBaseRegister)) {
                return true;
            }
        }
        return false;
    }
}
