/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source.impl;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.ServiceLoader;

public abstract class SourceAccessor {
    private static final SourceAccessor ACCESSOR;
    static {
        SourceAccessor accessor = null;

        if (accessor == null) {
            boolean jdk8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;
            if (!jdk8OrEarlier) {
                // As of JDK9, the JVMCI Services class should only be used for service
                // types
                // defined by JVMCI. Other services types should use ServiceLoader directly.
                Iterator<SourceAccessor> providers = ServiceLoader.load(SourceAccessor.class).iterator();
                if (providers.hasNext()) {
                    accessor = providers.next();
                    if (providers.hasNext()) {
                        throw new InternalError(String.format("Multiple %s providers found", SourceAccessor.class.getName()));
                    }
                }
            } else {
                Class<?> servicesClass = null;
                try {
                    servicesClass = Class.forName("jdk.vm.ci.services.Services");
                } catch (ClassNotFoundException e) {
                }
                if (servicesClass != null) {
                    try {
                        Method m = servicesClass.getDeclaredMethod("loadSingle", Class.class, boolean.class);
                        accessor = (SourceAccessor) m.invoke(null, SourceAccessor.class, false);
                    } catch (Throwable e) {
                        // Fail fast for other errors
                        throw (InternalError) new InternalError().initCause(e);
                    }
                }
            }
        }
        if (accessor == null) {
            Iterator<SourceAccessor> it = ServiceLoader.load(SourceAccessor.class).iterator();
            accessor = it.hasNext() ? it.next() : null;
        }
        ACCESSOR = accessor;
    }

    protected SourceAccessor() {
        if (!"com.oracle.truffle.api.impl.SourceAccessorImpl".equals(getClass().getName())) {
            throw new IllegalStateException();
        }
    }

    public static Collection<ClassLoader> allLoaders() {
        return ACCESSOR.loaders();
    }

    public static boolean isAOT() {
        return ACCESSOR.checkAOT();
    }

    public static void neverPartOfCompilation(String msg) {
        ACCESSOR.assertNeverPartOfCompilation(msg);
    }

    protected abstract Collection<ClassLoader> loaders();

    protected abstract boolean checkAOT();

    protected abstract void assertNeverPartOfCompilation(String msg);

}
