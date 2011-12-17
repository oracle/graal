/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.cri.intrinsics;

import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Empty marker interface for intrinsic implementations.
 *
 * Note: It is not necessary that this class is in the CRI, it could also be part of the compiler
 * implementation.  However, it might be useful for different compilers, so it is here for now.
 */
public interface IntrinsicImpl {

    /**
     * Registry that maps intrinsic ID strings to implementation objects.
     * Intrinsic ID strings can either be explicitly defined as String constants, or inferred from the
     * fully qualified name and signature of a method.
     */
    public class Registry implements Iterable<Map.Entry<String, IntrinsicImpl>> {
        private Map<String, IntrinsicImpl> implRegistry = new ConcurrentHashMap<String, IntrinsicImpl>(100, 0.75f, 1);

        /**
         * Add an implementation object for an explicitly defined intrinsic ID string.
         */
        public void add(String intrinsicId, IntrinsicImpl impl) {
            assert !implRegistry.containsKey(intrinsicId);
            implRegistry.put(intrinsicId, impl);
        }

        /**
         * Add an implementation object for an intrinsic implicitly defined by its fully qualified name and signature.
         */
        public void add(String className, String methodName, String signature, IntrinsicImpl impl) {
            add(literalId(className, methodName, signature), impl);
        }

        /**
         * Gets the implementation object for a method. First, the {@link RiMethod#intrinsic() explicit ID string} of the
         * method is searched in the registry, then the implicit ID inferred from the method name and signature.
         * @return The intrinsic implementation object, or {@code null} if none is found.
         */
        public IntrinsicImpl get(RiResolvedMethod method) {
            String intrinsic = method.intrinsic();
            if (intrinsic != null) {
                IntrinsicImpl impl = implRegistry.get(intrinsic);
                if (impl != null) {
                    return impl;
                }
            }
            return implRegistry.get(literalId(method));
        }


        private static String literalId(String className, String methodName, String signature) {
            return CiUtil.toInternalName(className) + methodName + signature;
        }

        private static String literalId(RiMethod method) {
            return method.holder().name() + method.name() + method.signature().asString();
        }

        @Override
        public Iterator<Entry<String, IntrinsicImpl>> iterator() {
            return implRegistry.entrySet().iterator();
        }
    }
}
