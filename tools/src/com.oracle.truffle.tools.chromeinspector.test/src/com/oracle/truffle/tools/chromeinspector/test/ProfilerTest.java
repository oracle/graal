/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tools.utils.json.JSONObject;

public class ProfilerTest {

    @Test
    public void testNoSourceProfile() throws InterruptedException, IOException, ExecutionException {
        ProxyLanguage.setDelegate(new TestNoSourceLanguage());
        InspectorTester tester = InspectorTester.start(false);
        Source source = Source.newBuilder(ProxyLanguage.ID, "1", "NoSourceProfile").build();
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Profiler.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":3,\"method\":\"Profiler.setSamplingInterval\",\"params\":{\"interval\":1000}}");
        assertEquals("{\"result\":{},\"id\":3}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":4,\"method\":\"Profiler.start\"}");
        assertEquals("{\"result\":{},\"id\":4}", tester.getMessages(true).trim());
        tester.eval(source).get();
        tester.sendMessage("{\"id\":5,\"method\":\"Profiler.stop\"}");
        JSONObject json = new JSONObject(tester.getMessages(true).trim());
        assertNotNull(json);
        assertEquals(json.getInt("id"), 5);
        JSONObject jsonResult = json.getJSONObject("result");
        assertNotNull(jsonResult);
        JSONObject jsonProfile = jsonResult.getJSONObject("profile");
        assertNotNull(jsonProfile);
        tester.sendMessage("{\"id\":6,\"method\":\"Profiler.disable\"}");
        assertEquals("{\"result\":{},\"id\":6}", tester.getMessages(true).trim());
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }

    @Test
    public void testNoSourceCoverage() throws InterruptedException, IOException, ExecutionException {
        ProxyLanguage.setDelegate(new TestNoSourceLanguage());
        InspectorTester tester = InspectorTester.start(false);
        Source source = Source.newBuilder(ProxyLanguage.ID, "1", "NoSourceProfile").build();
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Profiler.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":3,\"method\":\"Profiler.startPreciseCoverage\"}");
        assertEquals("{\"result\":{},\"id\":3}", tester.getMessages(true).trim());
        tester.eval(source).get();
        tester.sendMessage("{\"id\":5,\"method\":\"Profiler.takePreciseCoverage\"}");
        // No results in case of no source section
        assertEquals("{\"result\":{\"result\":[]},\"id\":5}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":7,\"method\":\"Profiler.stopPreciseCoverage\"}");
        assertEquals("{\"result\":{},\"id\":7}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":6,\"method\":\"Profiler.disable\"}");
        assertEquals("{\"result\":{},\"id\":6}", tester.getMessages(true).trim());
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }

    private static class TestNoSourceLanguage extends ProxyLanguage {

        @Override
        protected final CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
            return new NoSourceRootNode(languageInstance).getCallTarget();
        }

        private final class NoSourceRootNode extends RootNode {

            @Node.Child private NoSourceStatementNode statement;

            NoSourceRootNode(TruffleLanguage<?> language) {
                super(language);
                statement = new NoSourceStatementNode();
                insert(statement);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return statement.execute(frame);
            }

            @Override
            public String getName() {
                return "1";
            }

        }

    }

    @GenerateWrapper
    static class NoSourceStatementNode extends Node implements InstrumentableNode {

        NoSourceStatementNode() {
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new NoSourceStatementNodeWrapper(this, probe);
        }

        public Object execute(VirtualFrame frame) {
            assert frame != null;
            TruffleSafepoint.poll(this);
            return 10;
        }

        @Override
        public SourceSection getSourceSection() {
            return null;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.StatementTag.class.equals(tag) || StandardTags.RootTag.class.equals(tag);
        }

    }
}
