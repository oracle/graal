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
package jdk.graal.compiler.debug.test;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.options.OptionValues;
import org.graalvm.collections.EconomicMap;

/// Checks that the public current-scope context iterator follows Iterator's exhaustion contract.
public class ScopeContextIteratorContractTest {
    @Test
    public void exhaustedContextIteratorThrowsNoSuchElementException() throws Throwable {
        OptionValues options = new OptionValues(EconomicMap.create());
        options = new OptionValues(options, DebugOptions.DumpOnError, true);
        try (DebugContext debug = new Builder(options).build();
                        DebugContext.Scope scope = debug.scope("ScopeContextIteratorContractTest", new Object[]{"context"})) {
            Iterator<Object> iterator = scope.getCurrentContext().iterator();
            Assert.assertEquals("context", iterator.next());
            while (iterator.hasNext()) {
                iterator.next();
            }
            Assert.assertFalse(iterator.hasNext());
            Assert.assertThrows(NoSuchElementException.class, iterator::next);
        }
    }
}
