/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;

import jdk.graal.compiler.util.CollectionsUtil;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.runtime.AbstractCompilationTask;

import jdk.vm.ci.meta.JavaConstant;

public class NodeDebugPropertiesTest extends TruffleCompilerImplTest {

    private static final AbstractCompilationTask TASK = new AbstractCompilationTask() {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isLastTier() {
            return true;
        }

        @Override
        public boolean hasNextTier() {
            return false;
        }
    };

    private static final class UntrustedProperty {
        private boolean toStringCalled;

        @Override
        public String toString() {
            toStringCalled = true;
            throw new AssertionError("Debug property toString() must not be called");
        }
    }

    private static final class DebugPropertyNode extends Node {
        private final Object property;

        DebugPropertyNode(Object property) {
            this.property = property;
        }

        @Override
        public Map<String, Object> getDebugProperties() {
            return CollectionsUtil.mapOf("customProperty", property);
        }
    }

    @Test
    public void testUntrustedToStringIsNotInvoked() {
        UntrustedProperty property = new UntrustedProperty();
        DebugPropertyNode node = new DebugPropertyNode(property);
        JavaConstant nodeConstant = getSnippetReflection().forObject(node);
        Map<String, Object> properties = TASK.getDebugProperties(nodeConstant);
        String expected = property.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(property));
        Assert.assertEquals(expected, properties.get("customProperty"));
        Assert.assertEquals(expected, properties.get("field.property"));
        Assert.assertFalse(property.toStringCalled);
    }
}
