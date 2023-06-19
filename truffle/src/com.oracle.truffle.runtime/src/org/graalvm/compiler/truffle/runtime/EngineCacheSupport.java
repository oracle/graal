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
package org.graalvm.compiler.truffle.runtime;

import java.util.function.Function;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.TruffleLogger;

public interface EngineCacheSupport extends GraalRuntimeServiceProvider {

    void onEngineCreated(EngineData e);

    void onEnginePatch(EngineData e);

    boolean onEngineClosing(EngineData e);

    void onEngineClosed(EngineData e);

    boolean isStoreEnabled(OptionValues options);

    Object tryLoadingCachedEngine(OptionValues options, Function<String, TruffleLogger> loggerFactory);

    final class Disabled implements EngineCacheSupport {

        @Override
        public void onEngineCreated(EngineData e) {
        }

        @Override
        public void onEnginePatch(EngineData e) {
        }

        @Override
        public Object tryLoadingCachedEngine(OptionValues options, Function<String, TruffleLogger> loggerFactory) {
            return null;
        }

        @Override
        public boolean onEngineClosing(EngineData e) {
            return false;
        }

        @Override
        public boolean isStoreEnabled(OptionValues options) {
            return false;
        }

        @Override
        public void onEngineClosed(EngineData e) {
        }

        @Override
        public int getPriority() {
            // only last resort
            return Integer.MIN_VALUE;
        }

        @Override
        public OptionDescriptors getEngineOptions() {
            return OptionDescriptors.EMPTY;
        }

    }

    static EngineCacheSupport get() {
        return GraalTruffleRuntime.getRuntime().getEngineCacheSupport();
    }

}
