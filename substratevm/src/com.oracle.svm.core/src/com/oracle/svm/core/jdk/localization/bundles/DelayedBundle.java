/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.localization.bundles;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import com.oracle.svm.util.ReflectionUtil;

public class DelayedBundle implements StoredBundle {

    private final Method getContents;

    public DelayedBundle(Class<?> clazz) throws ReflectiveOperationException {
        getContents = findGetContentsMethod(clazz);
    }

    private static Method findGetContentsMethod(Class<?> clazz) throws ReflectiveOperationException {
        /* The `getContents` method can be declared in a super class, so we search the hierarchy. */
        for (Class<?> c = clazz; ResourceBundle.class.isAssignableFrom(c); c = c.getSuperclass()) {
            Method method = ReflectionUtil.lookupMethod(true, c, "getContents");
            if (method != null) {
                return method;
            }
        }
        throw new ReflectiveOperationException("Failed to find method `getContents` in " + clazz);
    }

    @Override
    public Map<String, Object> getContent(Object bundle) {
        try {
            Object[][] content = (Object[][]) getContents.invoke(bundle);
            return asMap(content);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> asMap(Object[][] content) {
        Map<String, Object> res = new HashMap<>();
        for (Object[] entry : content) {
            String key = (String) entry[0];
            Object value = entry[1];
            if (key == null || value == null) {
                throw new NullPointerException();
            }
            res.put(key, value);
        }
        return res;
    }
}
