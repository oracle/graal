/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.bootstrap;

/**
 * Information about a bootstrap method on an invoke dynamic or constant dynamic call. This object
 * stores the CallSite or the constant linked to the call, allowing to execute the bootstrap method
 * only once.
 * <p>
 * As in Hotspot, there is no synchronization mechanism, meaning it is possible to have a bootstrap
 * method executed twice for the same call if multiple threads execute it at the same time.
 * <p>
 * In Hotspot, the CallSite or the constant is stored in the constant pool of the Class, but in
 * Native Image, we only store it using this object, which is attached to the call.
 */
public final class BootstrapMethodInfo {
    /**
     * All field accesses are manually generated in Graal graphs.
     */
    @SuppressWarnings("unused") private Object object;

    /**
     * Class used to wrap an exception and store it in {@link BootstrapMethodInfo#object} to
     * distinguish from a {@link Throwable} outputted by a constant dynamic.
     * <p>
     * If a {@link ExceptionWrapper} is stored in {@link BootstrapMethodInfo#object}, the
     * {@link ExceptionWrapper#throwable} is rethrown instead of calling the bootstrap method again.
     * <p>
     * This class is not implemented as a record as it might cause cycles since it would have
     * bootstrapped methods.
     */
    public static class ExceptionWrapper {
        public final Throwable throwable;

        public ExceptionWrapper(Throwable throwable) {
            this.throwable = throwable;
        }
    }
}
