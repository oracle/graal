/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Parameter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import sun.reflect.generics.repository.AbstractRepository;
import sun.reflect.generics.scope.AbstractScope;

public class ReflectionObjectReplacer implements Function<Object, Object> {

    static class Identity {
        private final Object wrapped;

        Identity(Object wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(wrapped);
        }

        @Override
        public boolean equals(Object obj) {
            return ((Identity) obj).wrapped == wrapped;
        }
    }

    private final Set<Identity> scanned = ConcurrentHashMap.newKeySet();

    @Override
    public Object apply(Object original) {
        if (original instanceof AccessibleObject || original instanceof Parameter ||
                        original instanceof AbstractRepository || original instanceof AbstractScope) {
            if (scanned.add(new Identity(original))) {
                scan(original);
            }
        }
        return original;
    }

    private static void scan(Object original) {
        if (original instanceof AbstractScope) {
            AbstractScope<?> abstractScope = (AbstractScope<?>) original;
            /*
             * Lookup a type variable in the scope to trigger creation of
             * sun.reflect.generics.scope.AbstractScope.enclosingScope. The looked-up value is not
             * important, we just want to trigger creation of lazy internal state. The same eager
             * initialization is triggered by
             * sun.reflect.generics.repository.MethodRepository.getReturnType() called above,
             * however if the AbstractScope is seen first by the heap scanner then a `null` value
             * will be snapshotted for the `enclosingScope`.
             */
            try {
                abstractScope.lookup("");
            } catch (LinkageError | InternalError e) {
                /* The lookup calls Class.getEnclosingClass() which may fail. */
            }
        }
    }
}
