/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;

import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * JDK 11+ version of {@link HotSpotTruffleRuntimeServices}.
 */
class HotSpotTruffleRuntimeServices {

    private static final Constructor<? extends SpeculationLog> sharedHotSpotSpeculationLogConstructor;

    static {
        Constructor<? extends SpeculationLog> constructor = null;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends SpeculationLog> theClass = (Class<? extends SpeculationLog>) Class.forName("jdk.vm.ci.hotspot.SharedHotSpotSpeculationLog");
            constructor = theClass.getDeclaredConstructor(HotSpotSpeculationLog.class);
        } catch (ClassNotFoundException e) {
        } catch (NoSuchMethodException e) {
            throw new InternalError("SharedHotSpotSpeculationLog exists but constructor is missing", e);
        }
        sharedHotSpotSpeculationLogConstructor = constructor;
    }

    /**
     * Gets a speculation log to be used for compiling {@code callTarget}.
     */
    public static SpeculationLog getCompilationSpeculationLog(OptimizedCallTarget callTarget) {
        if (sharedHotSpotSpeculationLogConstructor != null) {
            HotSpotSpeculationLog masterLog = (HotSpotSpeculationLog) callTarget.getSpeculationLog();
            try {
                SpeculationLog compilationSpeculationLog = sharedHotSpotSpeculationLogConstructor.newInstance(masterLog);
                compilationSpeculationLog.collectFailedSpeculations();
                return compilationSpeculationLog;
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new InternalError(e);
            }
        }
        SpeculationLog log = callTarget.getSpeculationLog();
        if (log != null) {
            log.collectFailedSpeculations();
        }
        return log;
    }
}
