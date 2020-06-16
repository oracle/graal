/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SimpleGetoptTest extends TestBase {
    SimpleGetopt getopt;
    String errorKey;
    Object[] errorParams;

    @Rule public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() {
        Map<String, String> g = new HashMap<>(ComponentInstaller.globalOptions);
        g.put("8", "=C");
        g.put("long-user", "U");
        g.put("U", "s");
        getopt = new SimpleGetopt(g) {
            @Override
            public RuntimeException err(String messageKey, Object... args) {
                errorKey = messageKey;
                errorParams = args;
                throw new FailedOperationException(messageKey);
            }
        };
        for (String s : ComponentInstaller.commands.keySet()) {
            getopt.addCommandOptions(s, ComponentInstaller.commands.get(s).supportedOptions());
        }
    }

    private void setParams(String p) {
        getopt.setParameters(new LinkedList<>(Arrays.asList(p.split(" +"))));
    }

    @Test
    public void testMissingCommand() {
        setParams("");
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_MissingCommand");
        getopt.process();
    }

    @Test
    public void testUnknownCommand() {
        setParams("foo");
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_UnknownCommand");
        getopt.process();
    }

    @Test
    public void testUnknownCommandWithOptions() {
        setParams("-e -v foo -h");
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_UnknownCommand");
        getopt.process();
    }

    @Test
    public void testSpecificOptionsPrecedeCommand() {
        setParams("-s info -h");
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_UnsupportedGlobalOption");
        getopt.process();
    }

    @Test
    public void testUnknownOption() {
        setParams("install -S -h");
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_UnsupportedOption");
        getopt.process();
    }

    @Test
    public void testUnknownOptionIgnoredBecauseOfHelp() {
        setParams("install -h -s");
        getopt.process();

        assertNotNull(getopt.getOptValues().get("h"));
        // not processex
        assertNull(getopt.getOptValues().get("s"));
    }

    @Test
    public void testCommandWithSeparateOptions() {
        setParams("-e -v install -h");

        getopt.process();

        String cmd = getopt.getCommand();
        Map<String, String> opts = getopt.getOptValues();

        assertNotNull(opts.get("e"));
        assertNotNull(opts.get("v"));
        assertNotNull(opts.get("h"));
        assertEquals("install", cmd);
    }

    @Test
    public void testGlobalOptionAfterCommand() {
        setParams("-e install -v -h");

        getopt.process();

        String cmd = getopt.getCommand();
        Map<String, String> opts = getopt.getOptValues();

        assertNotNull(opts.get("e"));
        assertNotNull(opts.get("v"));
        assertNotNull(opts.get("h"));
        assertEquals("install", cmd);
    }

    @Test
    public void testPositonalParamsMixedWithOptions() {
        setParams("-e -v install param1 -f param2 -r param3");

        getopt.process();

        Map<String, String> opts = getopt.getOptValues();
        assertNotNull(opts.get("e"));
        assertNotNull(opts.get("f"));
        assertNotNull(opts.get("r"));
        assertNull(opts.get("h"));

        assertEquals(Arrays.asList("param1", "param2", "param3"), getopt.getPositionalParameters());
    }

    @Test
    public void testParametrizedOption() {
        setParams("-C catalog -v install -h");
        getopt.process();

        String cmd = getopt.getCommand();
        Map<String, String> opts = getopt.getOptValues();

        assertNotNull(opts.get("C"));
        assertNotNull(opts.get("v"));
        assertNotNull(opts.get("h"));
        assertEquals("install", cmd);
        assertEquals("catalog", opts.get("C"));
    }

    @Test
    public void testInterleavedParametrizedOptions() {
        setParams("-e install param1 -C catalog param2 -r param3");
        getopt.process();

        Map<String, String> opts = getopt.getOptValues();
        assertNotNull(opts.get("e"));
        assertNotNull(opts.get("C"));
        assertNotNull(opts.get("r"));
        assertEquals("catalog", opts.get("C"));

        assertEquals(Arrays.asList("param1", "param2", "param3"), getopt.getPositionalParameters());
    }

    @Test
    public void testMaskedOutOption() {
        setParams("-u list param1");
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_UnsupportedOption");
        getopt.process();
    }

    @Test
    public void testMaskedOutOption2() {
        setParams("list -u param1");
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_UnsupportedOption");
        getopt.process();
    }

    @Test
    public void testMergedOptions() {
        setParams("list param1 -vel param2");
        getopt.process();

        Map<String, String> opts = getopt.getOptValues();
        assertNotNull(opts.get("e"));
        assertNotNull(opts.get("v"));
        assertNotNull(opts.get("l"));
        assertEquals(Arrays.asList("param1", "param2"), getopt.getPositionalParameters());
    }

    @Test
    public void testOptionWithImmediateParameter() {
        setParams("list param1 -veCcatalog param2");
        getopt.process();

        Map<String, String> opts = getopt.getOptValues();
        assertNotNull(opts.get("e"));
        assertNotNull(opts.get("v"));
        assertNotNull(opts.get("C"));
        assertEquals("catalog", opts.get("C"));
        assertEquals(Arrays.asList("param1", "param2"), getopt.getPositionalParameters());
    }

    @Test
    public void testOptionsTerminated() {
        setParams("-e install param1 -C catalog -- param2 -r param3");
        getopt.process();

        Map<String, String> opts = getopt.getOptValues();
        assertNotNull(opts.get("e"));
        assertNotNull(opts.get("C"));
        assertNull(opts.get("r"));
        assertEquals("catalog", opts.get("C"));

        assertEquals(Arrays.asList("param1", "param2", "-r", "param3"), getopt.getPositionalParameters());
    }

    @Test
    public void testAmbiguousCommand() {
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_AmbiguousCommand");
        setParams("in");
        getopt.process();
    }

    @Test
    public void testEmptyCommand() {
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_MissingCommand");
        getopt.setParameters(new LinkedList<>(Arrays.asList("")));
        getopt.process();
    }

    @Test
    public void testEmptyParameter() {
        getopt.setParameters(new LinkedList<>(Arrays.asList("install", "")));
        getopt.process();
        assertEquals(Arrays.asList(""), getopt.getPositionalParameters());
    }

    @Test
    public void testDoubleDashOption() {
        setParams("--e --v install --x param1");

        getopt.process();

        String cmd = getopt.getCommand();
        Map<String, String> opts = getopt.getOptValues();

        assertNotNull(opts.get("e"));
        assertNotNull(opts.get("v"));
        assertNotNull(opts.get("x"));
        assertEquals("install", cmd);
        assertEquals(Arrays.asList("param1"), getopt.getPositionalParameters());
    }

    @Test
    public void testDoubleDashParamOption() {
        setParams("--e --v install --C catalog --x param1");

        getopt.process();

        String cmd = getopt.getCommand();
        Map<String, String> opts = getopt.getOptValues();

        assertNotNull(opts.get("e"));
        assertNotNull(opts.get("v"));
        assertNotNull(opts.get("x"));
        assertNotNull(opts.get("C"));
        assertEquals("catalog", opts.get("C"));

        assertEquals("install", cmd);
        assertEquals(Arrays.asList("param1"), getopt.getPositionalParameters());
    }

    @Test
    public void testLongOption() {
        setParams("--help");
        getopt.process();

        Map<String, String> opts = getopt.getOptValues();
        assertNotNull(opts.get("h"));

        // should not be interpreted as a series of single options:
        assertNull(opts.get("e"));
        assertNull(opts.get("l"));
        assertNull(opts.get("p"));
    }

    @Test
    public void testLongOptionAppendedParameter() {
        setParams("--catalogbubu");

        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_UnsupportedGlobalOption");
        getopt.process();
    }

    @Test
    public void testLongOptionWithParameterBeforeCommand() {
        setParams("--custom-catalog bubu install");

        getopt.process();

        Map<String, String> opts = getopt.getOptValues();
        assertEquals("bubu", opts.get("C"));
    }

    @Test
    public void testLongOptionWithParameterAfterCommand() {
        setParams("install --custom-catalog bubu ");

        getopt.process();
        Map<String, String> opts = getopt.getOptValues();
        assertEquals("bubu", opts.get("C"));
    }

    @Test
    public void testComputeAbbreviations() {
        setParams("install --user bubu");
        Map<String, String> abbrevs = getopt.computeAbbreviations(Arrays.asList("list", "list-files", "file-list", "force", "replace", "rewrite", "verify", "signature"));

        assertEquals(null, abbrevs.get("list"));
        assertEquals("list-files", abbrevs.get("list-"));
        assertEquals("list-files", abbrevs.get("list-file"));

        assertEquals("force", abbrevs.get("fo"));

        assertEquals(null, abbrevs.get("re"));
        assertEquals("rewrite", abbrevs.get("rew"));
        assertEquals("replace", abbrevs.get("rep"));
    }

    @Test
    public void testLongOptionAbbreviation() {
        setParams("install --long-user bubu");

        getopt.process();
        Map<String, String> opts = getopt.getOptValues();
        assertEquals("bubu", opts.get("U"));
    }

    @Test
    public void testOptionAliasesNoParam() {
        setParams("install -F bubu");
        getopt.process();
        Map<String, String> opts = getopt.getOptValues();
        assertEquals("", opts.get("L"));
    }

    @Test
    public void testOptionAliasesParamsCommand() {
        setParams("install -9 bubu");
        getopt.addCommandOption("install", "9", "=C");
        getopt.process();
        Map<String, String> opts = getopt.getOptValues();
        assertEquals("bubu", opts.get("C"));
    }

    @Test
    public void testOptionAliasesParamsGlobal() {
        setParams("install -8 bubu");
        getopt.process();
        Map<String, String> opts = getopt.getOptValues();
        assertEquals("bubu", opts.get("C"));
    }

    @Test
    public void testIgnoreUnknownCommands() {
        setParams("-v bubak 1");
        getopt.ignoreUnknownCommands(true);
        getopt.process();
        Map<String, String> opts = getopt.getOptValues();
        assertEquals(1, opts.size());
        assertNotNull(opts.get("v"));

        assertEquals(2, getopt.getPositionalParameters().size());
        assertEquals("bubak", getopt.getPositionalParameters().get(0));
    }
}
