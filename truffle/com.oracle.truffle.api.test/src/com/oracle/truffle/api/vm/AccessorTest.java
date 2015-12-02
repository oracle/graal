/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.source.Source;

import static com.oracle.truffle.api.vm.ImplicitExplicitExportTest.L1;

import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.ImplicitExplicitExportTest.ExportImportLanguage1;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

public class AccessorTest {
    public static Accessor API;

    @BeforeClass
    public static void initAccessors() throws Exception {
        Field f = Accessor.class.getDeclaredField("API");
        f.setAccessible(true);
        API = (Accessor) f.get(null);
    }

    @Test
    public void canGetAccessToOwnLanguageInstance() throws Exception {
        PolyglotEngine vm = PolyglotEngine.newBuilder().executor(Executors.newSingleThreadExecutor()).build();
        PolyglotEngine.Language language = vm.getLanguages().get(L1);
        assertNotNull("L1 language is defined", language);

        Source s = Source.fromText("return nothing", "nothing");
        Object ret = language.eval(s).get();
        assertNull("nothing is returned", ret);

        Object afterInitialization = findLanguageByClass(vm);
        assertNotNull("Language found", afterInitialization);
        assertTrue("Right instance: " + afterInitialization, afterInitialization instanceof ExportImportLanguage1);
    }

    Object findLanguageByClass(PolyglotEngine vm) throws IllegalAccessException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException, NoSuchFieldException {
        Method find = Accessor.class.getDeclaredMethod("findLanguage", Object.class, Class.class);
        find.setAccessible(true);
        TruffleLanguage.Env env = (TruffleLanguage.Env) find.invoke(API, vm, ExportImportLanguage1.class);
        Field f = env.getClass().getDeclaredField("langCtx");
        f.setAccessible(true);
        Object langCtx = f.get(env);
        f = langCtx.getClass().getDeclaredField("lang");
        f.setAccessible(true);
        return f.get(langCtx);
    }
}
