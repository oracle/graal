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
package com.oracle.truffle.tools.chromeinspector.test;

import com.oracle.truffle.api.interop.TruffleObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test of the provided inspector TruffleObject.
 */
public class InspectorObjectTest {

    private static final String NL = System.lineSeparator();

    private ByteArrayOutputStream out;
    private Context context;
    private TruffleObject inspector;
    private int freePort;

    @Before
    public void setUp() {
        out = new ByteArrayOutputStream();
        context = Context.newBuilder().out(out).err(out).build();
        Instrument inspect = context.getEngine().getInstruments().get("inspect");
        inspector = inspect.lookup(TruffleObject.class);
        try (ServerSocket testSocket = new ServerSocket(0)) {
            testSocket.setReuseAddress(true);
            freePort = testSocket.getLocalPort();
        } catch (IOException ioex) {
            Assert.fail("Failed to find a free port.");
        }
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testOpen() {
        context.eval("sl", "function testOpen(inspector) {\n" +
                        "    inspector.open(" + freePort + ", \"localhost\");\n" +
                        "}");
        Value testOpen = context.getBindings("sl").getMember("testOpen");
        testOpen.execute(inspector);
        String output = out.toString();
        Assert.assertTrue(output, output.startsWith("Debugger listening "));
        Assert.assertTrue(output, output.indexOf("ws://") > 0);
        Assert.assertTrue(output, output.indexOf(":" + freePort + "/") > 0);
    }

    @Test
    public void testNoUrl() {
        context.eval("sl", "function testUrl(inspector) {\n" +
                        "    return inspector.url();\n" +
                        "}");
        Value testUrl = context.getBindings("sl").getMember("testUrl");
        Value url = testUrl.execute(inspector);
        Assert.assertTrue(url.toString(), url.isNull());
    }

    @Test
    public void testUrlAndClose() {
        context.eval("sl", "function testUrl(inspector) {\n" +
                        "    return inspector.url();\n" +
                        "}\n" +
                        "function doOpen(inspector) {\n" +
                        "    inspector.open(" + freePort + ", \"localhost\");\n" +
                        "}\n" +
                        "function testClose(inspector) {\n" +
                        "    inspector.close();\n" +
                        "}");
        Value testUrl = context.getBindings("sl").getMember("testUrl");
        Value testClose = context.getBindings("sl").getMember("testClose");
        Value doOpen = context.getBindings("sl").getMember("doOpen");
        Value url = testUrl.execute(inspector);
        Assert.assertTrue(url.toString(), url.isNull());
        doOpen.execute(inspector);
        url = testUrl.execute(inspector);
        Assert.assertTrue(url.toString(), url.isString());
        String urlStr = url.asString();
        testURL(urlStr);
        testClose.execute(inspector);
        url = testUrl.execute(inspector);
        Assert.assertTrue(url.toString(), url.isNull());
    }

    private void testURL(String url) {
        Assert.assertTrue(url, url.startsWith("ws://"));
        Assert.assertTrue(url, url.indexOf(":" + freePort + "/") > 0);
    }

    @Test
    public void testOpenCloseOpen() {
        context.eval("sl", "function testOpenCloseOpen(inspector) {\n" +
                        "    inspector.open(" + freePort + ", \"localhost\", false);\n" +
                        "    url = inspector.url();\n" +
                        "    inspector.close();\n" +
                        "    nourl = inspector.url();\n" +
                        "    inspector.open(" + freePort + ", \"localhost\", false);\n" +
                        "    url2 = inspector.url();\n" +
                        "    inspector.close();\n" +
                        "    return url + \",\" + nourl + \",\" + url2;\n" +
                        "}\n");
        Value testOpenCloseOpen = context.getBindings("sl").getMember("testOpenCloseOpen");
        Value urlsValue = testOpenCloseOpen.execute(inspector);
        String[] urls = urlsValue.toString().split(",");
        testURL(urls[0]);
        Assert.assertEquals(urls[1], "null");
        testURL(urls[2]);
    }

    @Test
    public void testSession() {
        context.eval("sl", "function testSession(inspector) {\n" +
                        "    is = inspector.Session;\n" +
                        "    s = new(is);\n" +
                        "    return s.emit(\"hello\");\n" +
                        "}");
        Value testSession = context.getBindings("sl").getMember("testSession");
        Value ret = testSession.execute(inspector);
        Assert.assertEquals("", out.toString());
        Assert.assertFalse(ret.asBoolean());
    }

    @Test
    public void testSessionListeners() {
        context.eval("sl", "function testSession(inspector) {\n" +
                        "    s = new(inspector.Session);\n" +
                        "    s.on(\"evt_a\", listener1);\n" + // three listeners
                        "    s.addListener(\"evt_a\", listener2);\n" +
                        "    s.prependListener(\"evt_a\", listener3);\n" +
                        "    println(s.listenerCount());\n" +
                        "    ret = s.emit(\"evt_a\", \"A\");\n" + // calls in proper order
                        "    s.once(\"evt_b\", listener1);\n" + // two one-time listeners
                        "    s.addListener(\"evt_b\", listener2);\n" +
                        "    s.prependOnceListener(\"evt_b\", listener3);\n" +
                        "    println(s.listenerCount());\n" +
                        "    evt_names = s.eventNames();\n" +
                        "    ret = ret && s.emit(\"evt_b\", \"B\");\n" +
                        "    println(s.listenerCount() + \" = \" + s.listenerCount(\"evt_a\") + \" + \" + s.listenerCount(\"evt_b\"));\n" +
                        "    ret = ret && s.emit(\"evt_b\", \"BB\");\n" + // one-time listeners gone
                        "    s.off(\"evt_b\", listener2);\n" +
                        "    noEmit = s.emit(\"evt_b\", \"BB\");\n" +
                        "    if (noEmit) {println(\"Error emit\");}\n" +
                        "    println(s.listenerCount());\n" +
                        "    return ret;\n" +
                        "}\n" +
                        "function listener1(arg) {\n" +
                        "    println(\"l1: \" + arg);\n" +
                        "}\n" +
                        "function listener2(arg) {\n" +
                        "    println(\"l2: \" + arg);\n" +
                        "}\n" +
                        "function listener3(arg) {\n" +
                        "    println(\"l3: \" + arg);\n" +
                        "}\n");
        Value testSession = context.getBindings("sl").getMember("testSession");
        Value ret = testSession.execute(inspector);
        Assert.assertEquals("3" + NL +
                        "l3: A" + NL +
                        "l1: A" + NL +
                        "l2: A" + NL +
                        "6" + NL +
                        "l3: B" + NL +
                        "l1: B" + NL +
                        "l2: B" + NL +
                        "4 = 3 + 1" + NL +
                        "l2: BB" + NL +
                        "3" + NL, out.toString());
        Assert.assertTrue(ret.asBoolean());
    }

    @Test
    public void testSessionEvents() {
        context.eval("sl", "function testSession(inspector) {\n" +
                        "    s = new(inspector.Session);\n" +
                        "    s.on(\"Debugger.paused\", listenerP);\n" +
                        "    s.on(\"inspectorNotification\", listenerAll);\n" +
                        "    s.connect();\n" +
                        "    s.post(\"Debugger.enable\");\n" +
                        "    debugger;\n" +
                        "    s.disconnect();\n" +
                        "    debugger;\n" +
                        "}\n" +
                        "function listenerP(arg) {\n" +
                        "    println(\"P: \" + arg.method);\n" +
                        "}\n" +
                        "function listenerAll(arg) {\n" +
                        "    if (\"Runtime.consoleAPICalled\" == arg.method) {\n" +
                        "        // print callback\n" +
                        "        return;\n" +
                        "    } else {\n" +
                        "        println(\"All: \" + arg.method);\n" +
                        "    }\n" +
                        "}\n");
        Value testSession = context.getBindings("sl").getMember("testSession");
        testSession.execute(inspector);
        Assert.assertEquals("All: Debugger.scriptParsed" + NL +
                        "All: Debugger.scriptParsed" + NL +
                        "P: Debugger.paused" + NL +
                        "All: Debugger.paused" + NL +
                        "All: Debugger.resumed" + NL, out.toString());
    }
}
