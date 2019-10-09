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
package com.oracle.truffle.tools.agentscript.test;

import java.io.File;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Assume;
import org.junit.Test;

public class NoSuchFileTest {
    @Test
    public void noSuchFileDirectory() throws Exception {
        File nonExisting = File.createTempFile("missing", ".js");
        nonExisting.delete();
        Assume.assumeFalse("File is missing", nonExisting.exists());

        try (Context c = Context.newBuilder().allowIO(true).allowExperimentalOptions(true).option("agentscript", nonExisting.getAbsolutePath()).build()) {
            Object initializeTheAgent = AgentObjectFactory.createAgentObject(c);
            assertNotNull(initializeTheAgent);
            fail("Error: Expecting exception");
        } catch (PolyglotException t) {
            assertNotEquals(t.getMessage(), -1, t.getMessage().indexOf("No such file or directory"));
            assertNotEquals(t.getMessage(), -1, t.getMessage().indexOf(nonExisting.getName()));
            assertTrue("This is a fatal error", t.isExit());
            assertEquals("Error code 1", 1, t.getExitStatus());
        }
    }
}
