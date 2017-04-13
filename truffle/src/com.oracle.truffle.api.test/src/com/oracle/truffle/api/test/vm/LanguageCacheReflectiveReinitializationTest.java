/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.test.ReflectionUtils;

public class LanguageCacheReflectiveReinitializationTest {

    @Test
    @SuppressWarnings("unchecked")
    public void canReinitializeLanguages() throws Exception {
        Class<?> languageCacheClass = Class.forName("com.oracle.truffle.api.vm.LanguageCache");
        Method initMethod = languageCacheClass.getDeclaredMethod("initializeLanguages", ClassLoader.class);
        ReflectionUtils.setAccessible(initMethod, true);
        Map<String, Object> languages = (Map<String, Object>) initMethod.invoke(null, Thread.currentThread().getContextClassLoader());
        Method getMimeTypes = languageCacheClass.getDeclaredMethod("getMimeTypes");
        ReflectionUtils.setAccessible(getMimeTypes, true);
        for (Object lang : languages.values()) {
            Set<String> mimeTypes = (Set<String>) getMimeTypes.invoke(lang);
            if (mimeTypes.contains("application/x-test-hash")) {
                return;
            }
        }

        Assert.fail("Re-initialized languages must contain application/x-test language after reflective initialization");
    }
}
