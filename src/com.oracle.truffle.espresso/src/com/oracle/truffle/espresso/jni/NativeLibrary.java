/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jni;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;

public class NativeLibrary {

    public static TruffleObject loadLibrary(String lib) {
        Source source = Source.newBuilder("nfi", String.format("with dlmopen load(RTLD_LAZY) '%s'", lib), "loadLibrary").build();
        CallTarget target = EspressoLanguage.getCurrentContext().getEnv().parse(source);
        return (TruffleObject) target.call();
    }

    public static TruffleObject lookup(TruffleObject library, String method) throws UnknownIdentifierException {
        try {
            return (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), library, method);
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot find " + method);
        }
    }

    public static TruffleObject bind(TruffleObject symbol, String signature) {
        try {
            return (TruffleObject) ForeignAccess.sendInvoke(Message.INVOKE.createNode(), symbol, "bind", signature);
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot bind " + signature);
        }
    }

    public static TruffleObject lookupAndBind(TruffleObject library, String method, String signature) throws UnknownIdentifierException {
        try {
            TruffleObject symbol = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), library, method);
            return (TruffleObject) ForeignAccess.sendInvoke(Message.INVOKE.createNode(), symbol, "bind", signature);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot bind " + method);
        }
    }
}
