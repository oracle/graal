/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import com.oracle.truffle.compiler.ConstantFieldInfo;

import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Constant field provider used for parsing into the graph cache on HotSpot in
 * {@link CachingPEGraphDecoder}.
 */
final class TruffleCachingConstantFieldProvider implements ConstantFieldProvider {

    private final PartialEvaluator partialEvaluator;
    private final ConstantFieldProvider delegate;

    TruffleCachingConstantFieldProvider(PartialEvaluator partialEvaluator, ConstantFieldProvider delegate) {
        this.partialEvaluator = partialEvaluator;
        this.delegate = delegate;
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        boolean isStaticField = field.isStatic();
        if (!isStaticField && tool.getReceiver().isNull()) {
            return null;
        }
        ConstantFieldInfo info = partialEvaluator.getConstantFieldInfo(field);
        if (info != null) {
            /*
             * Non-null info means the field is annotated by one of the
             * annotations @CompilationFinal, @Child or @Children. We are not folding such fields
             * for the code cache on HotSpot. Not even static final fields. We delay this to the
             * partial evaluation phase to ensure we are not losing the annotation information and
             * are not reading values that are not yet stable when creating the graph for the cache.
             * For example, a static final array annotated by '@CompilationFinal(dimensions = 1)':
             * We cannot fold the array with 'stableDimension=1', because for the cache, the first
             * dimension is not stable. We cannot fold it with 'stableDimension=0' either, because
             * we would lose the information about the first dimension being stable for the partial
             * evaluation phase.
             **/
            return null;
        } else {
            // otherwise do regular constant folding.
            return delegate.readConstantField(field, tool);
        }
    }

    @Override
    public boolean maybeFinal(ResolvedJavaField field) {
        return delegate.maybeFinal(field);
    }

    @Override
    public boolean isTrustedFinal(CanonicalizerTool tool, ResolvedJavaField field) {
        return delegate.isTrustedFinal(tool, field);
    }

}
