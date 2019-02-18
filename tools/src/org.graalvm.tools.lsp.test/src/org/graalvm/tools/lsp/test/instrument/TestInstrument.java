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
package org.graalvm.tools.lsp.test.instrument;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.tools.lsp.api.ContextAwareExecutorRegistry;
import org.graalvm.tools.lsp.api.VirtualLanguageServerFileProvider;
import org.graalvm.tools.lsp.instrument.LSOptions;
import org.graalvm.tools.lsp.server.TruffleAdapter;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

@Registration(id = "lspTestInstrument", name = "LspTestInstrument", version = "0.1", services = {VirtualLanguageServerFileProvider.class, ContextAwareExecutorRegistry.class,
                TruffleAdapterProvider.class})
public class TestInstrument extends TruffleInstrument implements TruffleAdapterProvider {

    private TruffleAdapter truffleAdapter;

    @com.oracle.truffle.api.Option(name = "", help = "", category = OptionCategory.USER) //
    static final OptionKey<Boolean> LspTestInstrument = new OptionKey<>(true);

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Env env) {
        truffleAdapter = new TruffleAdapter(env);
        env.getOptions().set(LSOptions.DeveloperMode, true);
        env.registerService(truffleAdapter);
        env.registerService(this);
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new TestInstrumentOptionDescriptors();
    }

    public TruffleAdapter getTruffleAdapter() {
        return truffleAdapter;
    }

}
