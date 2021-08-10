/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.reflect.Method;
import java.util.function.Function;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.CEntryPointLiteralCodePointer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.MethodPointer;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticFeature
public class CEntryPointLiteralFeature implements Feature {

    class CEntryPointLiteralObjectReplacer implements Function<Object, Object> {

        @Override
        public Object apply(Object source) {
            if (source instanceof CEntryPointLiteralCodePointer) {
                CEntryPointLiteralCodePointer original = (CEntryPointLiteralCodePointer) source;

                Method reflectionMethod;
                try {
                    reflectionMethod = original.definingClass.getDeclaredMethod(original.methodName, original.parameterTypes);
                } catch (NoSuchMethodException ex) {
                    throw shouldNotReachHere("Method not found: " + original.definingClass.getName() + "." + original.methodName);
                }

                ResolvedJavaMethod javaMethod = metaAccess.lookupJavaMethod(reflectionMethod);
                if (javaMethod instanceof AnalysisMethod) {
                    AnalysisMethod aMethod = (AnalysisMethod) javaMethod;
                    CEntryPoint annotation = aMethod.getAnnotation(CEntryPoint.class);
                    UserError.guarantee(annotation != null, "Method referenced by %s must be annotated with @%s: %s", CEntryPointLiteral.class.getSimpleName(),
                                    CEntryPoint.class.getSimpleName(), javaMethod);
                    CEntryPointCallStubSupport.singleton().registerStubForMethod(aMethod, () -> CEntryPointData.create(aMethod));
                } else if (javaMethod instanceof HostedMethod) {
                    HostedMethod hMethod = (HostedMethod) javaMethod;
                    AnalysisMethod aMethod = hMethod.getWrapped();
                    AnalysisMethod aStub = CEntryPointCallStubSupport.singleton().getStubForMethod(aMethod);
                    HostedMethod hStub = (HostedMethod) metaAccess.getUniverse().lookup(aStub);
                    assert hStub.wrapped.isEntryPoint();
                    assert hStub.isCompiled();
                    /*
                     * Only during compilation and native image writing, we do the actual
                     * replacement.
                     */
                    return MethodPointer.factory(hStub);
                }
            }
            return source;
        }
    }

    protected UniverseMetaAccess metaAccess;
    protected BigBang bb;

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl config = (DuringSetupAccessImpl) a;

        metaAccess = config.getMetaAccess();
        bb = config.getBigBang();
        config.registerObjectReplacer(new CEntryPointLiteralObjectReplacer());
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        CompilationAccessImpl config = (CompilationAccessImpl) a;

        metaAccess = config.getMetaAccess();
        bb = null;
    }
}
