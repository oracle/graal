/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

@GenerateUncached
public abstract class AddPathToBindingsNode extends Node {
    static final String NEW_FILE = "<init>/(Ljava/lang/String;)V";
    static final String TO_URI = "toURI/()Ljava/net/URI;";
    static final String TO_URL = "toURL/()Ljava/net/URL;";
    static final String ADD_URL = "addURL/(Ljava/net/URL;)V";
    static final String FILE_CLASSNAME = "java/io/File";

    public abstract void execute(StaticObject loader, Object path) throws UnsupportedTypeException, ArityException;

    @Specialization
    protected void addPath(StaticObject loader, Object[] args,
                    @Bind("getContext()") EspressoContext context,
                    @Cached LookupDeclaredMethod lookupNewFile,
                    @Cached LookupDeclaredMethod lookupToURI,
                    @Cached LookupDeclaredMethod lookupToURL,
                    @Cached LookupDeclaredMethod lookupAddURL,
                    @Cached InvokeEspressoNode invokeNewFile,
                    @Cached InvokeEspressoNode invokeToURI,
                    @Cached InvokeEspressoNode invokeToURL) throws UnsupportedTypeException, ArityException {
        argsCheck(args);
        Klass fileKlass = loadFileKlass(context);
        Method newFile = lookupMethod(lookupNewFile, fileKlass, NEW_FILE, context);
        Object path = args[0];
        StaticObject file = fileKlass.allocateInstance();
        // will throw if path is not a string.
        invokeNewFile.execute(newFile, file, new Object[]{path});

        Method toURI = lookupMethod(lookupToURI, fileKlass, TO_URI, context);
        StaticObject uri = (StaticObject) invokeToURI.execute(toURI, file, StaticObject.EMPTY_ARRAY);
        Method toURL = lookupMethod(lookupToURL, uri.getKlass(), TO_URL, context);
        StaticObject url = (StaticObject) invokeToURL.execute(toURL, uri, StaticObject.EMPTY_ARRAY);
        Method addURL = lookupMethod(lookupAddURL, loader.getKlass(), ADD_URL, context);
        // InvokeEspressoNode only supports invoking public methods.
        addURL.invokeDirect(loader, url);
    }

    EspressoContext getContext() {
        return EspressoContext.get(this);
    }

    Klass loadFileKlass(EspressoContext context) {
        return context.getMeta().loadKlassOrNull(context.getTypes().fromClassGetName(FILE_CLASSNAME), StaticObject.NULL, StaticObject.NULL);
    }

    Method lookupMethod(LookupDeclaredMethod lookup, Klass k, String member, EspressoContext context) {
        try {
            // we know that there are no overloads for these methods
            return lookup.execute(k, member, false, false, -1)[0];
        } catch (ArityException | NullPointerException e) {
            throw context.getMeta().throwException(context.getMeta().java_lang_IllegalArgumentException);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static class InvocableAddToBindings implements TruffleObject {
        private final StaticObject loader;

        public InvocableAddToBindings(StaticObject loader) {
            this.loader = loader;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        public boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public Object execute(Object[] args,
                        @Cached AddPathToBindingsNode addPath) throws ArityException, UnsupportedTypeException {
            argsCheck(args);
            addPath.execute(loader, args[0]);
            return StaticObject.NULL;
        }
    }

    private static void argsCheck(Object[] args) throws ArityException {
        if (args.length != 1) {
            throw ArityException.create(1, 1, args.length);
        }
    }
}
