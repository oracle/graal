/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.api.interop.ForeignAccess.sendExecute;
import static com.oracle.truffle.api.interop.ForeignAccess.sendIsExecutable;
import static com.oracle.truffle.api.interop.ForeignAccess.sendIsInstantiable;
import static com.oracle.truffle.api.interop.ForeignAccess.sendNew;

import java.lang.reflect.Type;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValuesNode;

final class PolyglotExecuteNode extends Node {

    private static final Object[] EMPTY = new Object[0];

    @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();
    @Child private Node isInstantiable = Message.IS_INSTANTIABLE.createNode();
    @Child private Node execute = Message.EXECUTE.createNode();
    @Child private Node instantiate = Message.NEW.createNode();
    private final ToGuestValuesNode toGuests = ToGuestValuesNode.create();
    private final ConditionProfile condition = ConditionProfile.createBinaryProfile();
    @Child private ToHostNode toHost = ToHostNode.create();

    public Object execute(PolyglotLanguageContext languageContext, TruffleObject function, Object functionArgsObject,
                    Class<?> resultClass, Type resultType) {
        Object[] argsArray;
        if (functionArgsObject instanceof Object[]) {
            argsArray = (Object[]) functionArgsObject;
        } else {
            if (functionArgsObject == null) {
                argsArray = EMPTY;
            } else {
                argsArray = new Object[]{functionArgsObject};
            }
        }
        Object[] functionArgs = toGuests.apply(languageContext, argsArray);
        Object result;
        boolean executable = condition.profile(sendIsExecutable(isExecutable, function));
        try {
            if (executable) {
                result = sendExecute(execute, function, functionArgs);
            } else if (sendIsInstantiable(isInstantiable, function)) {
                result = sendNew(instantiate, function, functionArgs);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw HostInteropErrors.executeUnsupported(languageContext, function);
            }
        } catch (UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreter();
            if (executable) {
                throw HostInteropErrors.invalidExecuteArgumentType(languageContext, function, functionArgs);
            } else {
                throw HostInteropErrors.invalidInstantiateArgumentType(languageContext, function, functionArgs);
            }
        } catch (ArityException e) {
            CompilerDirectives.transferToInterpreter();
            if (executable) {
                throw HostInteropErrors.invalidExecuteArity(languageContext, function, functionArgs, e.getExpectedArity(), e.getActualArity());
            } else {
                throw HostInteropErrors.invalidInstantiateArity(languageContext, function, functionArgs, e.getExpectedArity(), e.getActualArity());
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw HostInteropErrors.executeUnsupported(languageContext, function);
        }
        return toHost.execute(result, resultClass, resultType, languageContext);
    }

}
