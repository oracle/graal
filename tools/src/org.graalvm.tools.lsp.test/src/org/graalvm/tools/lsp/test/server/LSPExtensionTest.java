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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.tools.lsp.server.types.ExecuteCommandParams;
import org.junit.Test;

public class LSPExtensionTest extends TruffleLSPTest {

    @Test
    public void getExtensionCommandNamesTest() {
        Collection<String> names = truffleAdapter.getExtensionCommandNames();
        assertEquals(names.size(), new LSPExtensionTestInstance().getCommands().size());
        assertTrue(names.contains(LSPExtensionTestInstance.COMMAND_SIMPLE));
        assertTrue(names.contains(LSPExtensionTestInstance.COMMAND_TIMEOUT));
    }

    @Test
    public void simpleCommandTest() {
        List<Object> dummyArguments = createDummyArguments();
        ExecuteCommandParams params = createParameters(LSPExtensionTestInstance.COMMAND_SIMPLE, dummyArguments);
        Future<?> future = truffleAdapter.createExtensionCommand(params);
        try {
            Object result = future.get(2, TimeUnit.SECONDS);
            assertTrue(result instanceof Integer && ((int) result) == dummyArguments.size());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("" + e);
        }
    }

    @Test
    public void timeoutCommandTest() {
        List<Object> dummyArguments = createDummyArguments();
        ExecuteCommandParams params = createParameters(LSPExtensionTestInstance.COMMAND_TIMEOUT, dummyArguments);
        Future<?> future = truffleAdapter.createExtensionCommand(params);
        try {
            Object result = future.get(2, TimeUnit.SECONDS);
            assertTrue(result == dummyArguments.get(0));
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("" + e);
        }
    }

    private static List<Object> createDummyArguments() {
        return Arrays.asList(true, false, 42);
    }

    private static ExecuteCommandParams createParameters(String command, List<Object> arguments) {
        ExecuteCommandParams params = ExecuteCommandParams.create(command);
        params.setArguments(arguments);
        return params;
    }
}
