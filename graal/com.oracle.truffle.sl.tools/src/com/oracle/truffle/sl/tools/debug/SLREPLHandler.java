/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.tools.debug;

import java.util.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.tools.debug.shell.*;
import com.oracle.truffle.tools.debug.shell.client.*;
import com.oracle.truffle.tools.debug.shell.server.*;

/**
 * Instantiation of the "server handler" part of the "REPL*" debugger for the simple language.
 * <p>
 * These handlers implement debugging commands that require language-specific support.
 *
 * @see SimpleREPLClient
 */
public abstract class SLREPLHandler extends REPLHandler {

    protected SLREPLHandler(String op) {
        super(op);
    }

    public static final SLREPLHandler INFO_HANDLER = new SLREPLHandler(REPLMessage.INFO) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServerContext serverContext) {
            final String topic = request.get(REPLMessage.TOPIC);

            if (topic == null || topic.isEmpty()) {
                final REPLMessage message = new REPLMessage(REPLMessage.OP, REPLMessage.INFO);
                return finishReplyFailed(message, "No info topic specified");
            }

            switch (topic) {

                case REPLMessage.LANGUAGE:
                    return createLanguageInfoReply();

                default:
                    final REPLMessage message = new REPLMessage(REPLMessage.OP, REPLMessage.INFO);
                    return finishReplyFailed(message, "No info about topic \"" + topic + "\"");
            }
        }
    };

    private static REPLMessage[] createLanguageInfoReply() {
        final ArrayList<REPLMessage> langMessages = new ArrayList<>();

        final REPLMessage msg1 = new REPLMessage(REPLMessage.OP, REPLMessage.INFO);
        msg1.put(REPLMessage.TOPIC, REPLMessage.LANGUAGE);
        msg1.put(REPLMessage.INFO_KEY, "Language");
        msg1.put(REPLMessage.INFO_VALUE, "Simple");
        msg1.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
        langMessages.add(msg1);

        return langMessages.toArray(new REPLMessage[0]);
    }

    public static final SLREPLHandler LOAD_RUN_SOURCE_HANDLER = new SLREPLHandler(REPLMessage.LOAD_RUN) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServerContext serverContext) {
            return loadHandler(request, serverContext, false);
        }
    };

    public static final SLREPLHandler LOAD_STEP_SOURCE_HANDLER = new SLREPLHandler(REPLMessage.LOAD_STEP) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServerContext serverContext) {
            return loadHandler(request, serverContext, true);
        }
    };

    /**
     * Runs a source, optionally stepping into a specified tag.
     */
    private static REPLMessage[] loadHandler(REPLMessage request, REPLServerContext serverContext, boolean stepInto) {
        final REPLMessage reply = new REPLMessage(REPLMessage.OP, REPLMessage.LOAD_RUN);
        final String fileName = request.get(REPLMessage.SOURCE_NAME);
        try {
            final Source source = Source.fromFileName(fileName, true);
            if (source == null) {
                return finishReplyFailed(reply, "can't find file \"" + fileName + "\"");
            }
            serverContext.getDebugEngine().run(source, stepInto);
            reply.put(REPLMessage.FILE_PATH, source.getPath());
            return finishReplySucceeded(reply, source.getName() + "  exited");
        } catch (QuitException ex) {
            throw ex;
        } catch (KillException ex) {
            return finishReplySucceeded(reply, fileName + " killed");
        } catch (Exception ex) {
            return finishReplyFailed(reply, "error loading file \"" + fileName + "\": " + ex.getMessage());
        }
    }
}
