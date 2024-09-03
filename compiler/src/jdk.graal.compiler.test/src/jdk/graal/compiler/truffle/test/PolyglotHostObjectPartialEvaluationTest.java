/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.nio.Buffer;

import jdk.graal.compiler.test.SubprocessUtil;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext;
import com.oracle.truffle.api.test.polyglot.ValueAPITest;

public class PolyglotHostObjectPartialEvaluationTest extends PartialEvaluationTest {

    @Before
    public void setup() {
        final HostAccess hostAccess = HostAccess.newBuilder().allowBufferAccess(true).build();
        final Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "true").allowHostAccess(hostAccess).build();
        setupContext(context);
    }

    public static Object constantTrue() {
        return true;
    }

    public static Object constantFalse() {
        return false;
    }

    @Test
    public void hasBufferElements() {
        Assume.assumeFalse("JaCoCo causes failure", SubprocessUtil.isJaCoCoAttached()); // GR-50672
        for (final Buffer buffer : ValueAPITest.makeTestBuffers()) {
            getContext().initialize(ProxyLanguage.ID);
            final Object bufferHostObject = LanguageContext.get(null).getEnv().asGuestValue(buffer);
            final RootNode node = new RootNode(null) {
                @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(1);

                @Override
                public Object execute(VirtualFrame frame) {
                    return interop.hasBufferElements(bufferHostObject);
                }
            };
            assertPartialEvalEquals(PolyglotHostObjectPartialEvaluationTest::constantTrue, node);
        }
    }

    @Test
    public void isBufferWritable() {
        Assume.assumeFalse("JaCoCo causes failure", SubprocessUtil.isJaCoCoAttached()); // GR-50672
        for (final Buffer buffer : ValueAPITest.makeTestBuffers()) {
            getContext().initialize(ProxyLanguage.ID);
            final Object bufferHostObject = LanguageContext.get(null).getEnv().asGuestValue(buffer);
            final RootNode node = new RootNode(null) {
                @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(1);

                @Override
                public Object execute(VirtualFrame frame) {
                    try {
                        return interop.isBufferWritable(bufferHostObject);
                    } catch (UnsupportedMessageException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            assertPartialEvalEquals(buffer.isReadOnly() ? PolyglotHostObjectPartialEvaluationTest::constantFalse : PolyglotHostObjectPartialEvaluationTest::constantTrue, node);
        }
    }

    @Test
    public void getBufferSize() {
        for (final Buffer buffer : ValueAPITest.makeTestBuffers()) {
            getContext().initialize(ProxyLanguage.ID);
            final Object bufferHostObject = LanguageContext.get(null).getEnv().asGuestValue(buffer);
            final RootNode node = new RootNode(null) {
                @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(1);

                @Override
                public Object execute(VirtualFrame frame) {
                    try {
                        return interop.getBufferSize(bufferHostObject);
                    } catch (UnsupportedMessageException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            assertPartialEvalNoInvokes(node);
        }
    }

    @Test
    public void readBufferByte() {
        for (final Buffer buffer : ValueAPITest.makeTestBuffers()) {
            getContext().initialize(ProxyLanguage.ID);
            final Object bufferHostObject = LanguageContext.get(null).getEnv().asGuestValue(buffer);
            final RootNode node = new RootNode(null) {
                @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(1);

                @Override
                public Object execute(VirtualFrame frame) {
                    try {
                        return interop.readBufferByte(bufferHostObject, 0);
                    } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            assertPartialEvalNoInvokes(node);
        }
    }

    @Test
    public void writeBufferByte() {
        for (final Buffer buffer : ValueAPITest.makeTestBuffers()) {
            if (buffer.isReadOnly()) {
                continue;
            }
            getContext().initialize(ProxyLanguage.ID);
            final Object bufferHostObject = LanguageContext.get(null).getEnv().asGuestValue(buffer);
            final RootNode node = new RootNode(null) {
                @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(1);

                @Override
                public Object execute(VirtualFrame frame) {
                    try {
                        interop.writeBufferByte(bufferHostObject, 0, (byte) 42);
                    } catch (UnsupportedMessageException | InvalidBufferOffsetException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                }
            };
            assertPartialEvalNoInvokes(node);
        }
    }

}
