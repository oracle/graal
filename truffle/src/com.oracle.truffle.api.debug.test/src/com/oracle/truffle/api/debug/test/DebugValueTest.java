/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DebugValueTest extends AbstractDebugTest {

    @Test
    public void testNumValue() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  VARIABLE(a, 42), \n" +
                        "  VARIABLE(inf, infinity), \n" +
                        "  STATEMENT()\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                DebugValue value42 = frame.getScope().getDeclaredValue("a");

                assertEquals("a", value42.getName());
                assertFalse(value42.isArray());
                assertNull(value42.getArray());
                assertNull(value42.getProperties());
                assertEquals("Integer", value42.getMetaObject().as(String.class));
                assertEquals("Infinity", frame.getScope().getDeclaredValue("inf").getMetaObject().as(String.class));
                SourceSection integerSS = value42.getSourceLocation();
                assertEquals("source integer", integerSS.getCharacters());
                SourceSection infinitySS = frame.getScope().getDeclaredValue("inf").getSourceLocation();
                assertEquals("source infinity", infinitySS.getCharacters());
            });

            expectDone();
        }
    }

    /**
     * Test of {@link DebugValue#isReadable()}, {@link DebugValue#isWritable()} and
     * {@link DebugValue#isInternal()}.
     */
    @Test
    public void testValueAttributes() throws Throwable {
        final Source source = testSource("DEFINE(function, ROOT(\n" +
                        "  ARGUMENT(a), \n" +
                        "  STATEMENT()\n" +
                        "))\n");
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();
        engine.eval(source);
        PolyglotEngine.Value functionValue = engine.findGlobalSymbol("function");
        assertNotNull(functionValue);
        Debugger debugger = Debugger.find(engine);

        // Test of the default attribute values:
        NoAttributesTruffleObject nao = new NoAttributesTruffleObject();
        boolean[] suspended = new boolean[]{false};
        DebuggerSession session = debugger.startSession((SuspendedEvent event) -> {
            assertFalse(suspended[0]);
            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("a");
            assertNotNull(value);
            DebugValue attributesTOValue = value.getProperties().iterator().next();
            assertEquals("property", attributesTOValue.getName());
            // Property is readable by default
            assertTrue(attributesTOValue.isReadable());
            // Property is writable by default
            assertTrue(attributesTOValue.isWritable());
            // Property is not internal by default
            assertFalse(attributesTOValue.isInternal());
            event.prepareContinue();
            suspended[0] = true;
        });
        session.install(Breakpoint.newBuilder(source).lineIs(3).build());
        functionValue.execute(nao);
        session.close();
        assertTrue(suspended[0]);

        // Test of the modified attribute values:
        suspended[0] = false;
        final ModifiableAttributesTruffleObject mao = new ModifiableAttributesTruffleObject();
        session = debugger.startSession((SuspendedEvent event) -> {
            assertFalse(suspended[0]);
            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("a");
            assertNotNull(value);
            DebugValue attributesTOValue = value.getProperties().iterator().next();
            assertEquals("property", attributesTOValue.getName());
            // All false initially
            assertFalse(attributesTOValue.isReadable());
            assertFalse(attributesTOValue.isWritable());
            assertFalse(attributesTOValue.isInternal());
            mao.setIsReadable(true);
            attributesTOValue = value.getProperties().iterator().next();
            assertTrue(attributesTOValue.isReadable());
            mao.setIsWritable(true);
            attributesTOValue = value.getProperties().iterator().next();
            assertTrue(attributesTOValue.isWritable());
            mao.setIsInternal(true);
            attributesTOValue = value.getProperties().iterator().next();
            assertTrue(attributesTOValue.isInternal());
            event.prepareContinue();
            suspended[0] = true;
        });
        session.install(Breakpoint.newBuilder(source).lineIs(3).build());
        functionValue.execute(mao);
        session.close();
        assertTrue(suspended[0]);
    }

    static final class NoAttributesTruffleObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return NoAttributesMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof NoAttributesTruffleObject;
        }

        @MessageResolution(receiverType = NoAttributesTruffleObject.class)
        static final class NoAttributesMessageResolution {

            @Resolve(message = "KEYS")
            abstract static class NoAttributesKeysNode extends Node {

                @SuppressWarnings("unused")
                public Object access(NoAttributesTruffleObject ato) {
                    return new PropertyKeysTruffleObject();
                }
            }

            @Resolve(message = "READ")
            abstract static class NoAttributesReadNode extends Node {

                @SuppressWarnings("unused")
                public Object access(NoAttributesTruffleObject ato, String name) {
                    return "propertyValue";
                }
            }
        }
    }

    static final class ModifiableAttributesTruffleObject implements TruffleObject {

        private boolean isReadable;
        private boolean isWritable;
        private boolean isInternal;

        @Override
        public ForeignAccess getForeignAccess() {
            return ModifiableAttributesMessageResolutionForeign.ACCESS;
        }

        public void setIsReadable(boolean isReadable) {
            this.isReadable = isReadable;
        }

        public void setIsWritable(boolean isWritable) {
            this.isWritable = isWritable;
        }

        public void setIsInternal(boolean isInternal) {
            this.isInternal = isInternal;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof ModifiableAttributesTruffleObject;
        }

        @MessageResolution(receiverType = ModifiableAttributesTruffleObject.class)
        static final class ModifiableAttributesMessageResolution {

            @Resolve(message = "KEYS")
            abstract static class ModifiableAttributesKeysNode extends Node {

                public Object access(ModifiableAttributesTruffleObject ato, boolean internal) {
                    if (internal || ato.isInternal == internal) {
                        return new PropertyKeysTruffleObject();
                    } else {
                        return JavaInterop.asTruffleObject(new Object[]{});
                    }
                }
            }

            @Resolve(message = "READ")
            abstract static class ModifiableAttributesReadNode extends Node {

                @SuppressWarnings("unused")
                public Object access(ModifiableAttributesTruffleObject ato, String name) {
                    return "propertyValue";
                }
            }

            @Resolve(message = "KEY_INFO")
            abstract static class ModifiableAttributesKeyInfoNode extends Node {

                @SuppressWarnings("unused")
                public int access(ModifiableAttributesTruffleObject ato, String propName) {
                    return KeyInfo.newBuilder().setReadable(ato.isReadable).setWritable(ato.isWritable).setInternal(ato.isInternal).build();
                }
            }
        }
    }

    /**
     * Truffle object representing property keys and having one property named "property".
     */
    static final class PropertyKeysTruffleObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return PropertyKeysMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof PropertyKeysTruffleObject;
        }

        @MessageResolution(receiverType = PropertyKeysTruffleObject.class)
        static final class PropertyKeysMessageResolution {

            @Resolve(message = "HAS_SIZE")
            abstract static class PropertyKeysHasSizeNode extends Node {

                @SuppressWarnings("unused")
                public boolean access(PropertyKeysTruffleObject ato) {
                    return true;
                }
            }

            @Resolve(message = "GET_SIZE")
            abstract static class PropertyKeysGetSizeNode extends Node {

                @SuppressWarnings("unused")
                public int access(PropertyKeysTruffleObject ato) {
                    return 1;
                }
            }

            @Resolve(message = "READ")
            abstract static class PropertyKeysReadNode extends Node {

                @SuppressWarnings("unused")
                public Object access(PropertyKeysTruffleObject ato, int index) {
                    return "property";
                }
            }
        }
    }

}
