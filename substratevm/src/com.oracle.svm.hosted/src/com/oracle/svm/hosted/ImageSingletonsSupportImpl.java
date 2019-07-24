/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import com.oracle.svm.core.util.UserError;

public final class ImageSingletonsSupportImpl extends ImageSingletonsSupport {

    @Override
    public <T> void add(Class<T> key, T value) {
        HostedManagement.getAndAssertExists().doAdd(key, value);
    }

    @Override
    public <T> T lookup(Class<T> key) {
        return HostedManagement.getAndAssertExists().doLookup(key);
    }

    @Override
    public boolean contains(Class<?> key) {
        HostedManagement hm = HostedManagement.get();
        if (hm == null) {
            return false;
        } else {
            return hm.doContains(key);
        }
    }

    /**
     * Management of the {@link ImageSingletons} registry during image generation.
     */
    public static final class HostedManagement {

        static {
            ImageSingletonsSupport.installSupport(new ImageSingletonsSupportImpl());
        }

        /**
         * Multiple image generators can run simultaneously in one hosting VM. Therefore, the
         * {@link ImageSingletons} is not a singleton during image generation, i.e., we cannot store
         * the {@link ImageSingletons} in a static field. Instead, we store it in a thread local
         * variable. The image generator is responsible to {@link #installInThread install} the same
         * configuration in all worker threads, and {@link #clearInThread clear} it when it is no
         * longer needed.
         */
        private static final ThreadLocal<HostedManagement> hostedVMConfig = new ThreadLocal<>();

        public static HostedManagement getAndAssertExists() {
            HostedManagement result = get();
            assert result != null;
            return result;
        }

        public static HostedManagement get() {
            return hostedVMConfig.get();
        }

        public static void installInThread(HostedManagement vmConfig) {
            assert hostedVMConfig.get() == null;
            hostedVMConfig.set(vmConfig);
        }

        public static void clearInThread() {
            assert hostedVMConfig.get() != null;
            hostedVMConfig.set(null);
        }

        private final Map<Class<?>, Object> configObjects;

        public HostedManagement() {
            this.configObjects = new ConcurrentHashMap<>();
        }

        <T> void doAdd(Class<T> key, T value) {
            checkKey(key);
            if (value == null) {
                throw UserError.abort("ImageSingletons do not allow null value for key " + key.getTypeName());
            }

            Object prevValue = configObjects.putIfAbsent(key, value);

            if (prevValue != null) {
                throw UserError.abort("ImageSingletons.add must not overwrite existing key " + key.getTypeName() + "\nExisting value: " + prevValue + "\nNew value: " + value);
            }
        }

        <T> T doLookup(Class<T> key) {
            checkKey(key);
            Object result = configObjects.get(key);
            if (result == null) {
                throw UserError.abort("ImageSingletons do not contain key " + key.getTypeName());
            }
            return key.cast(result);
        }

        boolean doContains(Class<?> key) {
            checkKey(key);
            return configObjects.containsKey(key);
        }

        private static void checkKey(Class<?> key) {
            if (key == null) {
                throw UserError.abort("ImageSingletons do not allow null keys");
            }
        }
    }
}
