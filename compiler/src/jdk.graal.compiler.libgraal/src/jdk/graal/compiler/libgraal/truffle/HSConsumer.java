/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.libgraal.truffle;

import com.oracle.truffle.compiler.OptimizedAssumptionDependency;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerAssumptionDependency;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.word.WordFactory;

import java.util.function.Consumer;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.ConsumeOptimizedAssumptionDependency;
import static org.graalvm.jniutils.JNIMethodScope.env;

final class HSConsumer extends HSObject implements Consumer<OptimizedAssumptionDependency> {

    private final TruffleFromLibGraalCalls calls;

    HSConsumer(JNIMethodScope scope, JObject handle, TruffleFromLibGraalCalls calls) {
        super(scope, handle);
        this.calls = calls;
    }

    @TruffleFromLibGraal(ConsumeOptimizedAssumptionDependency)
    @Override
    public void accept(OptimizedAssumptionDependency optimizedDependency) {
        TruffleCompilerAssumptionDependency dependency = (TruffleCompilerAssumptionDependency) optimizedDependency;
        JObject compilable;
        long installedCode;
        if (dependency == null) {
            compilable = WordFactory.nullPointer();
            installedCode = 0;
        } else {
            TruffleCompilable ast = dependency.getCompilable();
            if (ast == null) {
                /*
                 * Compilable may be null if the compilation was triggered by a libgraal host
                 * compilation.
                 */
                compilable = WordFactory.nullPointer();
            } else {
                compilable = ((HSTruffleCompilable) dependency.getCompilable()).getHandle();
            }
            installedCode = HotSpotJVMCIRuntime.runtime().translate(dependency.getInstalledCode());
        }
        HSConsumerGen.callConsumeOptimizedAssumptionDependency(calls, env(), getHandle(), compilable, installedCode);
    }
}
