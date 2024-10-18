/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import java.io.IOException;

import org.graalvm.tools.insight.Insight;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@SuppressWarnings({"unused", "static-method"})
@ExportLibrary(InteropLibrary.class)
final class AgentObject extends MembersObject.AbstractReader {

    private final InsightInstrument insight;
    private final InsightPerSource source;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private byte[] msg;

    enum Members {
        id,
        version
    }

    AgentObject(String msg, InsightInstrument insight, InsightPerSource source) {
        super(Members.class, Members.values());
        this.msg = msg == null ? null : msg.getBytes();
        this.insight = insight;
        this.source = source;
    }

    @Override
    Object readInsightMember(Enum<?> e) {
        warnMsg();
        switch ((Members) e) {
            case id:
                return Insight.ID;
            case version:
                return Insight.VERSION;
            default:
                throw CompilerDirectives.shouldNotReachHere(e.name());
        }
    }

    @ExportMessage
    static class IsMemberInvocable {

        // Only invocation via a String name. No member object exists for the on/off methods.

        @Specialization(guards = "interop.isString(memberObj)")
        static boolean isInvocable(AgentObject receiver, Object memberObj, @Cached.Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
            try {
                String member = interop.asString(memberObj);
                return "on".equals(member) || "off".equals(member);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Fallback
        static boolean isInvocable(AgentObject receiver, Object unknownObj) {
            return false;
        }
    }

    @ExportMessage
    static class InvokeMember {

        @Specialization(guards = "interop.isString(memberObj)")
        static Object invoke(AgentObject receiver, Object memberObj, Object[] args, @Cached.Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop)
                        throws UnknownMemberException, UnsupportedMessageException {
            String name = interop.asString(memberObj);
            return invokeStringMember(receiver, name, memberObj, args);
        }

        @Fallback
        static Object invoke(AgentObject receiver, Object unknownObj, Object[] args) throws UnknownMemberException {
            CompilerDirectives.transferToInterpreter();
            throw UnknownMemberException.create(unknownObj);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static Object invokeStringMember(AgentObject obj, String name, Object member, Object[] args) throws UnknownMemberException, UnsupportedMessageException {
        obj.warnMsg();
        final InsightPerContext perContext = obj.insight.findCtx();
        switch (name) {
            case "on": {
                AgentType type = AgentType.find(InteropLibrary.getUncached().asString(args[0]));
                switch (type) {
                    case SOURCE: {
                        InsightInstrument.Key b = obj.source.sourceBinding();
                        perContext.register(b, args[1]);
                        break;
                    }
                    case ENTER:
                    case RETURN: {
                        InsightFilter.Data data = InsightFilter.create(type, args);
                        obj.source.binding(data, (key) -> {
                            perContext.register(key, data);
                            return InsightHookNode.factory(obj.insight, key);
                        }, (key) -> {
                            perContext.register(key, data);
                        });
                        break;
                    }
                    case CLOSE: {
                        InsightInstrument.Key b = obj.source.closeBinding();
                        perContext.register(b, args[1]);
                        break;
                    }
                    default:
                        throw new IllegalStateException();
                }
                break;
            }
            case "off": {
                AgentType type = AgentType.find(InteropLibrary.getUncached().asString(args[0]));
                switch (type) {
                    case SOURCE: {
                        InsightInstrument.Key b = obj.source.sourceBinding();
                        perContext.unregister(b, args[1]);
                        break;
                    }
                    case CLOSE: {
                        InsightInstrument.Key b = obj.source.closeBinding();
                        perContext.unregister(b, args[1]);
                        break;
                    }
                    default:
                        perContext.unregister(null, args[1]);
                }
                break;
            }
            default:
                throw UnknownMemberException.create(member);
        }
        return obj;
    }

    private void warnMsg() {
        byte[] arr = msg;
        if (arr != null) {
            try {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                insight.env().err().write(arr);
                msg = null;
            } catch (IOException ex) {
                // go on
            }
        }
    }
}
