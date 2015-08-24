/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.tools.debug;

import java.io.*;
import java.util.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.*;
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

    // TODO (mlvdv) re-implement stepInto when vm support replaced
    /**
     * Runs a source, optionally stepping into a specified tag.
     */
    private static REPLMessage[] loadHandler(REPLMessage request, REPLServerContext serverContext, @SuppressWarnings("unused") boolean stepInto) {
        final REPLMessage reply = new REPLMessage(REPLMessage.OP, REPLMessage.LOAD_RUN);
        final String fileName = request.get(REPLMessage.SOURCE_NAME);
        try {
            final File file = new File(fileName);
            if (!file.canRead()) {
                return finishReplyFailed(reply, "can't find file \"" + fileName + "\"");
            }
            final TruffleVM vm = serverContext.vm();
            vm.eval(Source.fromFileName(file.getPath()));
            TruffleVM.Symbol main = vm.findGlobalSymbol("main");
            if (main != null) {
                main.invoke(null);
            }
            final String path = file.getCanonicalPath();
            reply.put(REPLMessage.FILE_PATH, path);
            return finishReplySucceeded(reply, fileName + "  exited");
        } catch (QuitException ex) {
            throw ex;
        } catch (KillException ex) {
            return finishReplySucceeded(reply, fileName + " killed");
        } catch (Exception ex) {
            return finishReplyFailed(reply, "error loading file \"" + fileName + "\": " + ex.getMessage());
        }
    }
}
