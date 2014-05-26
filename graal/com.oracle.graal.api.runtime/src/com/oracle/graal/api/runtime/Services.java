/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.runtime;

import static java.lang.String.*;

import java.util.*;

/**
 * A mechanism on top of the standard {@link ServiceLoader} that enables a runtime to efficiently
 * load services marked by {@link Service}. This may be important for services loaded early in the
 * runtime initialization process.
 */
public class Services {

    private static final ClassValue<List<Service>> cache = new ClassValue<List<Service>>() {
        @Override
        protected List<Service> computeValue(Class<?> type) {
            Service[] names = getServiceImpls(type);
            if (names == null || names.length == 0) {
                throw new InternalError(format("No implementations for %s found (ensure %s extends %s)", type.getSimpleName(), type.getSimpleName(), Service.class));
            }
            return Arrays.asList(names);
        }
    };

    /**
     * Gets an {@link Iterable} of the implementations available for a given service.
     */
    @SuppressWarnings("unchecked")
    public static <S> Iterable<S> load(Class<S> service) {
        if (Service.class.isAssignableFrom(service)) {
            try {
                return (Iterable<S>) cache.get(service);
            } catch (UnsatisfiedLinkError e) {
                // Fall back to standard SerivceLoader
            }
        }
        return ServiceLoader.loadInstalled(service);
    }

    private static native <S> S[] getServiceImpls(Class<?> service);
}
