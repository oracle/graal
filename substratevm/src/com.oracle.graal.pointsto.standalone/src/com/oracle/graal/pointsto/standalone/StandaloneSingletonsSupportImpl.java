/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import com.oracle.graal.pointsto.util.AnalysisError;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StandaloneSingletonsSupportImpl extends ImageSingletonsSupport {

    static {
        ImageSingletonsSupport.installSupport(new StandaloneSingletonsSupportImpl());
    }

    private Map<Class<?>, Object> configObjects = new ConcurrentHashMap<>();

    public static ImageSingletonsSupport get() {
        return ImageSingletonsSupport.get();
    }

    @Override
    public <T> void add(Class<T> key, T value) {
        configObjects.put(key, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T lookup(Class<T> key) {
        Object ret = configObjects.get(key);
        if (ret == null) {
            throw AnalysisError.shouldNotReachHere(String.format("ImageSingletons do not contain key %s", key.getTypeName()));
        }
        return (T) ret;
    }

    @Override
    public boolean contains(Class<?> key) {
        return configObjects.containsKey(key);
    }
}
