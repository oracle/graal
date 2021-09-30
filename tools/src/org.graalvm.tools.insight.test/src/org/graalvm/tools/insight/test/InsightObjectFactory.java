/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import org.graalvm.tools.insight.test.InsightAPI.SourceInfo;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import static org.junit.Assert.assertNotNull;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Predicate;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.tools.insight.Insight;
import org.junit.Assert;
import static org.junit.Assert.assertFalse;

public final class InsightObjectFactory extends ProxyLanguage {
    private InsightObjectFactory() {
    }

    static InsightAPI.OnConfig createConfig(
                    boolean expressions, boolean statements, boolean roots,
                    String rootNameFilter, Predicate<SourceInfo> sourceFilter) {
        InsightAPI.OnConfig config = new InsightAPI.OnConfig();
        config.expressions = expressions;
        config.statements = statements;
        config.roots = roots;
        config.rootNameFilter = rootNameFilter;
        config.sourceFilter = sourceFilter;
        return config;
    }

    static Context newContext() {
        return newContext(Context.newBuilder());
    }

    public static Context newContext(Context.Builder b) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        return newContext(b, os, os);
    }

    static Context newContext(Context.Builder b, ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return b.out(out).err(err).allowExperimentalOptions(true).allowHostAccess(HostAccess.ALL).build();
    }

    private Object readObject;

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        final Source source = request.getSource();
        String scriptName = source.getName();
        final AgentRootNode root = new AgentRootNode(ProxyLanguage.get(null), this, scriptName, source, request.getArgumentNames());
        return root.getCallTarget();
    }

    public static Value readInsight(Context context, Object[] interopValue) throws Exception {
        return readInsight(context, interopValue, new AutoCloseable[1]);
    }

    public static Value readInsight(Context context, Object[] interopValue, AutoCloseable[] handle) throws Exception {
        cleanAgentObject();
        final InsightObjectFactory langImpl = new InsightObjectFactory();
        Value value;
        try {
            handle[0] = Embedding.enableInsight(InsightObjectFactory.createAgentSource(Insight.ID), context);
            ProxyLanguage.setDelegate(langImpl);
            value = context.eval(ProxyLanguage.ID, "");
            assertNotNull("Agent object has been initialized", langImpl.readObject);
            if (interopValue != null) {
                interopValue[0] = langImpl.readObject;
            }
        } finally {
            ProxyLanguage.setDelegate(new InsightObjectFactory());
        }
        return value;
    }

    public static Value readObject(Context context, String name) throws Exception {
        return readObject(context, name, new AutoCloseable[1]);
    }

    private static Value readObject(Context context, String name, AutoCloseable[] handle) throws Exception {
        cleanAgentObject();

        Value value;
        try {
            handle[0] = Embedding.enableInsight(InsightObjectFactory.createAgentSource(name), context);
            final InsightObjectFactory langImpl = new InsightObjectFactory();
            ProxyLanguage.setDelegate(langImpl);
            value = context.eval(ProxyLanguage.ID, "");
            assertFalse("We got our object initialized", value.isNull());
            assertNotNull("Our object isn't null", langImpl.readObject);
        } finally {
            ProxyLanguage.setDelegate(new InsightObjectFactory());
        }
        return value;
    }

    static void cleanAgentObject() {
    }

    private static org.graalvm.polyglot.Source createAgentSource(String name) {
        return org.graalvm.polyglot.Source.newBuilder(ProxyLanguage.ID, name, "agent.px").buildLiteral();
    }

    private static class AgentRootNode extends RootNode {
        @Child private ValueNode node;
        private final String scriptName;
        private final Source source;
        private final List<String> argNames;
        private final InsightObjectFactory language;

        AgentRootNode(TruffleLanguage<?> tl, InsightObjectFactory language, String scriptName, Source source, List<String> argNames) {
            super(tl);
            this.language = language;
            this.scriptName = scriptName;
            this.source = source;
            this.node = new ValueNode();
            this.argNames = argNames;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if ("agent.px".equals(scriptName)) {
                for (int i = 0; i < Math.min(argNames.size(), frame.getArguments().length); i++) {
                    final String id = argNames.get(i);
                    final Object v = frame.getArguments()[i];
                    if (source.getCharacters().toString().equals(id)) {
                        Assert.assertNull("No observed object set yet", language.readObject);
                        language.readObject = v;
                        return v;
                    }
                }
                throw new IllegalStateException();
            } else {
                node.executeToFinishInitializationOfAgentObject(frame);
                return language.readObject == null ? 0 : language.readObject;
            }
        }

        @Override
        public SourceSection getSourceSection() {
            return source.createSection(0, source.getLength());
        }
    }

    @GenerateWrapper
    static class ValueNode extends Node implements InstrumentableNode {
        ValueNode() {
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public SourceSection getSourceSection() {
            return getRootNode().getSourceSection();
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.RootBodyTag.class == tag || StandardTags.RootTag.class == tag;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new ValueNodeWrapper(this, probe);
        }

        @SuppressWarnings("unused")
        protected void executeToFinishInitializationOfAgentObject(VirtualFrame frame) {
        }

    }
}
