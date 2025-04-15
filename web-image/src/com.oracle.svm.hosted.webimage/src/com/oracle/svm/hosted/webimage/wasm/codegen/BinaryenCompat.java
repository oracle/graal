/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import java.util.Objects;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.webimage.wasm.ast.visitors.WasmPrinter;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.options.Option;
import jdk.vm.ci.code.site.Reference;

/**
 * Compatibility class to work around issues with binaryen's text format.
 *
 * TODO GR-46987
 */
public class BinaryenCompat {

    public static class Options {
        @Option(help = "Use Binaryen (wasm-as) to assemble the final Wasm binary")//
        public static final HostedOptionKey<Boolean> UseBinaryen = new HostedOptionKey<>(false);
    }

    public static boolean usesBinaryen() {
        return Options.UseBinaryen.getValue();
    }

    /**
     * A relocatable "reference" for binaryen's explicit non-standard pop instruction.
     * <p>
     * There is some special handling in {@link WasmPrinter} to emit the proper code for this. This
     * is done to avoid introducing a completely new instruction for this.
     */
    public static final class Pop extends Reference {
        public final WasmValType type;

        public Pop(WasmValType type) {
            this.type = type;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(type);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Pop pop = (Pop) o;
            return Objects.equals(type, pop.type);
        }
    }
}
