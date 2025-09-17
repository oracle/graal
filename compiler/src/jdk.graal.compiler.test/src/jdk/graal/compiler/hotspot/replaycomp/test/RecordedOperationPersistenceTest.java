/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.test;

import static jdk.vm.ci.runtime.JVMCICompiler.INVOCATION_ENTRY_BCI;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.Platform;
import jdk.graal.compiler.hotspot.replaycomp.CompilationProxyMapper;
import jdk.graal.compiler.hotspot.replaycomp.CompilerInterfaceDeclarations;
import jdk.graal.compiler.hotspot.replaycomp.OperationRecorder;
import jdk.graal.compiler.hotspot.replaycomp.RecordedForeignCallLinkages;
import jdk.graal.compiler.hotspot.replaycomp.RecordedOperationPersistence;
import jdk.graal.compiler.hotspot.replaycomp.SpecialResultMarker;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.json.JsonWriter;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Tests the JSON serialization and deserialization performed by
 * {@link RecordedOperationPersistence}.
 */
public class RecordedOperationPersistenceTest extends GraalCompilerTest {
    @Test
    public void dumpsAndLoads() throws IOException, RecordedOperationPersistence.DeserializationException {
        CompilerInterfaceDeclarations declarations = CompilerInterfaceDeclarations.build();
        RecordedOperationPersistence persistence = new RecordedOperationPersistence(declarations, Platform.ofCurrentHost(),
                        HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget());
        var compilationUnit = createRecordedCompilationUnit();
        ProxyFactory proxyFactory = new ProxyFactory(declarations);
        CompilationProxyMapper proxyMapper = new CompilationProxyMapper(declarations, proxyFactory::proxify);
        List<OperationRecorder.RecordedOperation> proxifiedOperations = compilationUnit.operations().stream().map(
                        (operation) -> new OperationRecorder.RecordedOperation(proxyMapper.proxifyRecursive(operation.receiver()), operation.method(),
                                        (Object[]) proxyMapper.proxifyRecursive(operation.args()), proxyMapper.proxifyRecursive(operation.resultOrMarker()))).toList();
        String json;
        try (StringWriter stringWriter = new StringWriter(); JsonWriter jsonWriter = new JsonWriter(stringWriter)) {
            persistence.dump(compilationUnit, jsonWriter);
            json = stringWriter.toString();
        }
        RecordedOperationPersistence.RecordedCompilationUnit parsedCompilationUnit;
        try (StringReader reader = new StringReader(json)) {
            parsedCompilationUnit = persistence.load(reader, proxyFactory);
        }
        for (var pair : CollectionsUtil.zipLongest(proxifiedOperations, parsedCompilationUnit.operations())) {
            Assert.assertEquals(pair.getLeft().receiver(), pair.getRight().receiver());
            Assert.assertTrue(Objects.deepEquals(pair.getLeft().args(), pair.getRight().args()));
            Object expectedResult = pair.getLeft().resultOrMarker();
            Object actualResult = pair.getRight().resultOrMarker();
            if (expectedResult instanceof SpecialResultMarker.ExceptionThrownMarker) {
                Assert.assertTrue(actualResult instanceof SpecialResultMarker.ExceptionThrownMarker);
            } else if (expectedResult instanceof Assumptions.AssumptionResult<?> expectedAssumptionResult) {
                if (actualResult instanceof Assumptions.AssumptionResult<?> actualAssumptionResult) {
                    Assert.assertEquals(expectedAssumptionResult.getResult(), actualAssumptionResult.getResult());
                    Assumptions expectedAssumptions = new Assumptions();
                    expectedAssumptionResult.recordTo(expectedAssumptions);
                    Assumptions actualAssumptions = new Assumptions();
                    actualAssumptionResult.recordTo(actualAssumptions);
                    Assert.assertEquals(expectedAssumptions, actualAssumptions);
                } else {
                    Assert.fail();
                }
            } else {
                Assert.assertTrue(Objects.deepEquals(expectedResult, actualResult));
            }
        }
    }

    public static Object dummyMethod() {
        try {
            throw new Exception();
        } catch (Exception e) {
            return null;
        }
    }

    private RecordedOperationPersistence.RecordedCompilationUnit createRecordedCompilationUnit() {
        HotSpotResolvedJavaMethod dummyMethod = (HotSpotResolvedJavaMethod) getResolvedJavaMethod("dummyMethod");

        CompilationProxy.SymbolicMethod constantGetCallSiteTarget = new CompilationProxy.SymbolicMethod(HotSpotObjectConstant.class, "getCallSiteTarget");
        CompilationProxy.SymbolicMethod symbolicReadArrayLength = new CompilationProxy.SymbolicMethod(ConstantReflectionProvider.class, "readArrayLength", JavaConstant.class);
        CompilationProxy.SymbolicMethod symbolicGetHandlers = new CompilationProxy.SymbolicMethod(HotSpotResolvedJavaMethod.class, "getExceptionHandlers");
        CompilationProxy.SymbolicMethod symbolicGetOopMap = new CompilationProxy.SymbolicMethod(HotSpotResolvedJavaMethod.class, "getOopMapAt", int.class);

        JavaConstant constant = getSnippetReflection().forObject(new Object());
        JavaConstant otherConstant = getSnippetReflection().forObject(new Object());
        SpecialResultMarker exceptionMarker = new SpecialResultMarker.ExceptionThrownMarker(new IllegalArgumentException("test exception"));
        BitSet bitSet = new BitSet(8);
        bitSet.set(4);
        bitSet.set(6);

        List<OperationRecorder.RecordedOperation> operations = List.of(
                        new OperationRecorder.RecordedOperation(constant, constantGetCallSiteTarget, null,
                                        new Assumptions.AssumptionResult<>(otherConstant, new Assumptions.CallSiteTargetValue(constant, otherConstant))),
                        new OperationRecorder.RecordedOperation(getConstantReflection(), symbolicReadArrayLength, new Object[]{constant}, exceptionMarker),
                        new OperationRecorder.RecordedOperation(dummyMethod, symbolicGetHandlers, null, dummyMethod.getExceptionHandlers()),
                        new OperationRecorder.RecordedOperation(dummyMethod, symbolicGetOopMap, new Object[]{0}, bitSet));

        return new RecordedOperationPersistence.RecordedCompilationUnit(
                        new HotSpotCompilationRequest(dummyMethod, INVOCATION_ENTRY_BCI, 0L, 1),
                        "test configuration",
                        false,
                        Platform.ofCurrentHost(),
                        new RecordedForeignCallLinkages(EconomicMap.create()),
                        "test graph",
                        operations);
    }

    private static final class ProxyFactory implements RecordedOperationPersistence.ProxyFactory {
        private final CompilerInterfaceDeclarations declarations;

        private ProxyFactory(CompilerInterfaceDeclarations declarations) {
            this.declarations = declarations;
        }

        public CompilationProxy proxify(Object input) {
            return createProxy(declarations.findRegistrationForInstance(input));
        }

        @Override
        public CompilationProxy createProxy(CompilerInterfaceDeclarations.Registration registration) {
            return CompilationProxy.newProxyInstance(registration.clazz(), (proxy, method, invokableMethod, args) -> {
                if (method.equals(CompilationProxyBase.unproxifyMethod)) {
                    return registration;
                } else if (method.equals(CompilationProxyBase.equalsMethod)) {
                    if (args[0] instanceof CompilationProxy other) {
                        return registration == other.unproxify();
                    } else {
                        return false;
                    }
                } else if (method.equals(CompilationProxyBase.hashCodeMethod)) {
                    return registration.hashCode();
                } else if (method.equals(CompilationProxyBase.toStringMethod)) {
                    return registration.clazz().getSimpleName();
                } else {
                    return null;
                }
            });
        }
    }
}
