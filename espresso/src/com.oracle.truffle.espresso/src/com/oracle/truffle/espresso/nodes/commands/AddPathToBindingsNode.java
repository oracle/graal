/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.nodes.commands;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@GenerateUncached
@GenerateInline(value = false)
public abstract class AddPathToBindingsNode extends EspressoNode {

    public abstract void execute(Object[] path) throws UnsupportedTypeException, ArityException;

    @Specialization
    protected void addPath(Object[] args,
                    @Bind("getContext()") EspressoContext context,
                    @CachedLibrary(limit = "1") InteropLibrary lib) throws UnsupportedTypeException, ArityException {
        StaticObject guestPath = getGuestPath(args, context, lib);
        context.getLazyCaches().getAddPathToBindingsCache().execute(guestPath);
    }

    @ExportLibrary(InteropLibrary.class)
    public static class InvocableAddToBindings implements TruffleObject {
        @ExportMessage
        @SuppressWarnings("static-method")
        public boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public Object execute(Object[] args,
                        @Cached AddPathToBindingsNode addPath) throws ArityException, UnsupportedTypeException {
            // Args are checked in addPathToBindingsNode.
            addPath.execute(args);
            return StaticObject.NULL;
        }
    }

    private static StaticObject getGuestPath(Object[] args, EspressoContext context, InteropLibrary lib) throws ArityException, UnsupportedTypeException {
        if (args.length != 1) {
            throw ArityException.create(1, 1, args.length);
        }
        Object path = args[0];
        if (!lib.isString(path)) {
            throw UnsupportedTypeException.create(args);
        }
        StaticObject guestPath;
        try {
            guestPath = context.getMeta().toGuestString(lib.asString(path));
        } catch (UnsupportedMessageException e) {
            throw UnsupportedTypeException.create(args);
        }
        return guestPath;
    }
}
