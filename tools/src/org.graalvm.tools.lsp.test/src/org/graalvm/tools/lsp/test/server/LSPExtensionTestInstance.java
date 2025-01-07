/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.test.server;

import java.util.Arrays;
import java.util.List;

import org.graalvm.tools.api.lsp.LSPCommand;
import org.graalvm.tools.api.lsp.LSPExtension;
import org.graalvm.tools.api.lsp.LSPServerAccessor;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

@Registration(id = "lsp-extension-test", name = "LSPExtension Test Instance", version = "0.1", services = LSPExtension.class)
public class LSPExtensionTestInstance extends TruffleInstrument implements LSPExtension {
    protected static final String COMMAND_SIMPLE = "lsp_test_extension_simple_command";
    protected static final String COMMAND_TIMEOUT = "lsp_test_extension_timeout_command";

    public List<LSPCommand> getCommands() {
        return Arrays.asList(new LSPExtensionTestSimpleCommand(), new LSPExtensionTestTimeoutCommand());
    }

    @Override
    protected void onCreate(Env env) {
        env.registerService(this);
    }

    private static final class LSPExtensionTestSimpleCommand implements LSPCommand {

        public String getName() {
            return COMMAND_SIMPLE;
        }

        public Object execute(LSPServerAccessor server, Env env, List<Object> arguments) {
            return arguments.size();
        }
    }

    private static final class LSPExtensionTestTimeoutCommand implements LSPCommand {

        public String getName() {
            return COMMAND_TIMEOUT;
        }

        public Object execute(LSPServerAccessor server, Env env, List<Object> arguments) {
            try {
                Thread.sleep(getTimeoutMillis() * 2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return -1;
        }

        public int getTimeoutMillis() {
            return 200;
        }

        public Object onTimeout(List<Object> arguments) {
            return arguments.get(0);
        }
    }
}
