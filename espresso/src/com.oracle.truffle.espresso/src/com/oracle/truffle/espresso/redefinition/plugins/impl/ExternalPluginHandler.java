/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.redefinition.plugins.impl;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.jdwp.impl.JDWP;
import com.oracle.truffle.espresso.runtime.StaticObject;

final class ExternalPluginHandler {

    private static final String RERUN_CLINIT = "shouldRerunClassInitializer";
    private static final String POST_HOTSWAP = "postHotSwap";

    private final InteropLibrary interopLibrary;
    private final StaticObject guestHandler;

    private ExternalPluginHandler(StaticObject handler, InteropLibrary library) {
        this.guestHandler = handler;
        this.interopLibrary = library;
    }

    public static ExternalPluginHandler create(StaticObject guestHandler) throws IllegalArgumentException {
        InteropLibrary library = InteropLibrary.getUncached(guestHandler);

        boolean invocable = library.isMemberInvocable(guestHandler, RERUN_CLINIT) &&
                        library.isMemberInvocable(guestHandler, POST_HOTSWAP);

        if (!invocable) {
            throw new IllegalArgumentException("guest handler does not implement expected API");
        }
        return new ExternalPluginHandler(guestHandler, library);
    }

    public boolean shouldRerunClassInitializer(Klass klass, boolean changed) {
        try {
            return (boolean) interopLibrary.invokeMember(guestHandler, RERUN_CLINIT, klass.mirror(), changed);
        } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException | ArityException e) {
            JDWP.LOGGER.throwing(ExternalPluginHandler.class.getName(), "shouldRerunClassInitializer", e);
        }
        return false;
    }

    public void postHotSwap(Klass[] changedKlasses) {
        try {
            StaticObject[] guestClasses = new StaticObject[changedKlasses.length];
            for (int i = 0; i < guestClasses.length; i++) {
                guestClasses[i] = changedKlasses[i].mirror();
            }
            StaticObject array = StaticObject.createArray(changedKlasses[0].getMeta().java_lang_Class_array, guestClasses);
            interopLibrary.invokeMember(guestHandler, POST_HOTSWAP, array);
        } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException | ArityException e) {
            JDWP.LOGGER.throwing(ExternalPluginHandler.class.getName(), "postHotSwap", e);
        }
    }
}
