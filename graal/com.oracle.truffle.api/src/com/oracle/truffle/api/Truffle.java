/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.lang.reflect.*;
import java.security.*;

import com.oracle.truffle.api.impl.*;

/**
 * Class for obtaining the Truffle runtime singleton object of this virtual machine.
 */
public class Truffle {

    private static final TruffleRuntime RUNTIME = initRuntime();

    /**
     * Gets the singleton {@link TruffleRuntime} object.
     */
    public static TruffleRuntime getRuntime() {
        return RUNTIME;
    }

    private static TruffleRuntime initRuntime() {
        if (TruffleOptions.ForceInterpreter) {
            /*
             * Force Truffle to run in interpreter mode even if we have a specialized implementation
             * of TruffleRuntime available.
             */
            return new DefaultTruffleRuntime();
        }

        return AccessController.doPrivileged(new PrivilegedAction<TruffleRuntime>() {
            public TruffleRuntime run() {
                TruffleRuntimeAccess access = null;
                Class<?> servicesClass = null;
                try {
                    servicesClass = Class.forName("com.oracle.jvmci.service.Services");
                } catch (ClassNotFoundException e) {
                    // JVMCI is unavailable
                }
                if (servicesClass != null) {
                    try {
                        Method m = servicesClass.getDeclaredMethod("loadSingle", Class.class, boolean.class);
                        access = (TruffleRuntimeAccess) m.invoke(null, TruffleRuntimeAccess.class, false);
                    } catch (Throwable e) {
                        // Fail fast for other errors
                        throw (InternalError) new InternalError().initCause(e);
                    }
                }
                // TODO: try standard ServiceLoader?
                if (access != null) {
                    return access.getRuntime();
                }
                return new DefaultTruffleRuntime();
            }
        });
    }
}
