/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;

public class LanguageContextFreedTest extends TestWithSynchronousCompiling {

    @Test
    public void testContexConstantFreed() {
        final int compilationThreshold = 10;
        AtomicReference<OptimizedCallTarget> currentTarget = new AtomicReference<>();
        AtomicReference<ProxyLanguage.LanguageContext> currentLangContext = new AtomicReference<>();
        try (Context ctx = setupContext("engine.CompilationThreshold", String.valueOf(compilationThreshold))) {
            ProxyLanguage.setDelegate(new ProxyLanguage() {
                @Override
                protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                    currentLangContext.set(ProxyLanguage.getCurrentContext());
                    TruffleRuntime runtime = Truffle.getRuntime();
                    OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(languageInstance) {
                        @Override
                        public Object execute(VirtualFrame frame) {
                            lookupContextReference(ProxyLanguage.class).get();
                            return 0;
                        }
                    });
                    assertEquals(compilationThreshold, (int) target.getOptionValue(PolyglotCompilerOptions.CompilationThreshold));
                    currentTarget.set(target);
                    return target;
                }
            });
            for (int i = 0; i < compilationThreshold; i++) {
                ctx.eval(Source.create(ProxyLanguage.ID, ""));
            }
            assertNotNull(currentTarget.get());
            assertTrue(currentTarget.get().isValid());
            ctx.eval(Source.create(ProxyLanguage.ID, ""));
        }
        assertNotNull(currentLangContext.get());
        Reference<?> langContextRef = new WeakReference<>(currentLangContext.getAndSet(null));
        GCUtils.assertGc("Language context should be freed when polyglot Context is closed.", langContextRef);
    }
}
