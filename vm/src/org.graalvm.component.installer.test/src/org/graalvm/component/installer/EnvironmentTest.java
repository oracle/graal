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
package org.graalvm.component.installer;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import org.junit.AfterClass;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests basic functionality of the Environment class.
 * 
 * @author sdedic
 */
public class EnvironmentTest {
    @Rule public ExpectedException exception = ExpectedException.none();

    private static ResourceBundle B1;
    private static Locale saveLocale;
    private Environment env;

    private Map<String, String> initOptions = new HashMap<>();

    @BeforeClass
    public static void setUp() {
        // establish well-known locale, so we may check I18ned strings
        saveLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
        B1 = ResourceBundle.getBundle("org.graalvm.component.installer.Bundle", Locale.US);
    }

    @AfterClass
    public static void tearDown() {
        Locale.setDefault(saveLocale);
    }

    class FailInputStream extends FilterInputStream {
        FailInputStream() {
            super(System.in);
        }

        @Override
        public int read() throws IOException {
            Assert.fail("Unexpected read");
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            Assert.fail("Unexpected read");
            return super.read(b, off, len);
        }
    }

    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

    @Test
    public void testNonInteractiveLineFails() {
        setupEmptyEnv();
        env.setNonInteractive(true);
        env.setIn(new FailInputStream());
        exception.expect(NonInteractiveException.class);
        assertNull(env.acceptLine(false));
    }

    /**
     * Non-interactive mode + auto-yes, and confirmation line. Should succeed.
     */
    @Test
    public void testNonInteractiveYesLineOK() {
        setupEmptyEnv();
        env.setNonInteractive(true);
        env.setAutoYesEnabled(true);
        env.setIn(new FailInputStream());
        assertSame(Feedback.AUTO_YES, env.acceptLine(true));
    }

    /**
     * Non-interactive mode + auto-yes, but regular, not a confirmation line. Should fail.
     */
    @Test
    public void testNonInteractiveNormalLineFails() {
        setupEmptyEnv();
        env.setNonInteractive(true);
        env.setAutoYesEnabled(true);
        env.setIn(new FailInputStream());
        exception.expect(NonInteractiveException.class);
        exception.expectMessage(B1.getString("ERROR_NoninteractiveInput"));
        assertSame(Feedback.AUTO_YES, env.acceptLine(false));
    }

    /**
     * Regular + auto-yes. Confirmation line should not read the input.
     */
    @Test
    public void testAutoConfirmSkipsInput() {
        setupEmptyEnv();
        env.setAutoYesEnabled(true);
        env.setIn(new FailInputStream());
        assertSame(Feedback.AUTO_YES, env.acceptLine(true));
    }

    @Test
    public void testNonInteractivePasswordFails() {
        setupEmptyEnv();
        env.setNonInteractive(true);
        env.setIn(new FailInputStream());
        exception.expect(NonInteractiveException.class);
        assertNull(env.acceptPassword());
    }

    void setupEmptyEnv() {
        List<String> parameters = Arrays.asList("param");
        env = new Environment("test", "org.graalvm.component.installer", parameters, initOptions);
    }

    /**
     * Checks that an error will be printed without stacktrace.
     */
    @Test
    public void testErrorMessagePlain() throws Exception {
        setupEmptyEnv();
        env.setErr(new PrintStream(errBuffer));
        env.error("ERROR_UserInput", new ClassCastException(), "Foobar");
        String s = new String(errBuffer.toByteArray(), "UTF-8");
        assertEquals(B1.getString("ERROR_UserInput").replace("{0}", "Foobar") + "\n", s);
    }

    /**
     * Checks that an error will be printed together with the stacktrace.
     */
    @Test
    public void testErrorMessageWithException() throws Exception {
        initOptions.put(Commands.OPTION_DEBUG, "");
        setupEmptyEnv();
        env.setErr(new PrintStream(errBuffer));
        env.error("ERROR_UserInput", new ClassCastException(), "Foobar");
        String all = new String(errBuffer.toByteArray(), "UTF-8");
        String[] lines = all.split("\n");
        assertEquals(B1.getString("ERROR_UserInput").replace("{0}", "Foobar"), lines[0]);
        assertTrue(lines[1].contains("ClassCastException"));
    }

    @Test
    public void testFailureOperation() throws Exception {
        setupEmptyEnv();

        Throwable t = env.failure("URL_InvalidDownloadURL", new MalformedURLException("Foo"), "Bar", "Baz");
        assertTrue(t instanceof FailedOperationException);
        String s = MessageFormat.format(B1.getString("URL_InvalidDownloadURL"), "Bar", "Baz");
        assertEquals(t.getLocalizedMessage(), s);
        assertNotSame(t, t.getCause());
        assertEquals("Foo", t.getCause().getLocalizedMessage());
    }
}
