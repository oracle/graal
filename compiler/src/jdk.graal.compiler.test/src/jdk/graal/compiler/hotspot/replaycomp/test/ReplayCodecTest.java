/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.Platform;
import jdk.graal.compiler.hotspot.replaycomp.BinaryReplayCodec;
import jdk.graal.compiler.hotspot.replaycomp.CompilationProxies;
import jdk.graal.compiler.hotspot.replaycomp.CompilationProxyMapper;
import jdk.graal.compiler.hotspot.replaycomp.CompilationTaskProduct;
import jdk.graal.compiler.hotspot.replaycomp.CompilerInterfaceDeclarations;
import jdk.graal.compiler.hotspot.replaycomp.OperationRecorder;
import jdk.graal.compiler.hotspot.replaycomp.RecordedCompilationUnit;
import jdk.graal.compiler.hotspot.replaycomp.RecordedForeignCallLinkages;
import jdk.graal.compiler.hotspot.replaycomp.SpecialResultMarker;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Tests replay-file serialization and deserialization for the binary codec.
 */
public class ReplayCodecTest extends GraalCompilerTest {
    /**
     * Checks that a binary round trip preserves recorded operations and proxy identities.
     */
    @Test
    public void dumpsAndLoadsBinary() throws Exception {
        CompilerInterfaceDeclarations declarations = CompilerInterfaceDeclarations.build();
        Platform hostPlatform = Platform.ofCurrentHost();
        var hostTarget = HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget();
        BinaryReplayCodec binaryCodec = new BinaryReplayCodec(declarations, hostPlatform, hostTarget);
        RecordedCompilationUnit compilationUnit = createRecordedCompilationUnit();
        TestProxyFactory proxyFactory = new TestProxyFactory(declarations);
        List<OperationRecorder.RecordedOperation> expectedOperations = proxifyOperations(compilationUnit, declarations, proxyFactory);
        byte[] binary;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            binaryCodec.write(compilationUnit, output);
            binary = output.toByteArray();
        }
        RecordedCompilationUnit roundTrippedCompilationUnit;
        try (ByteArrayInputStream input = new ByteArrayInputStream(binary)) {
            roundTrippedCompilationUnit = binaryCodec.read(input, proxyFactory);
        }
        assertRoundTrippedOperations(expectedOperations, roundTrippedCompilationUnit.operations());
    }

    private static List<OperationRecorder.RecordedOperation> proxifyOperations(RecordedCompilationUnit compilationUnit,
                    CompilerInterfaceDeclarations declarations, TestProxyFactory proxyFactory) {
        CompilationProxyMapper proxyMapper = new CompilationProxyMapper(declarations, proxyFactory::proxify);
        return compilationUnit.operations().stream().map(operation -> new OperationRecorder.RecordedOperation(
                        proxyMapper.proxifyRecursive(operation.receiver()),
                        operation.method(),
                        (Object[]) proxyMapper.proxifyRecursive(operation.args()),
                        proxyMapper.proxifyRecursive(operation.resultOrMarker()))).toList();
    }

    private static void assertRoundTrippedOperations(List<OperationRecorder.RecordedOperation> expectedOperations,
                    List<OperationRecorder.RecordedOperation> actualOperations) {
        // The shape of the operations must be the same.
        Assert.assertEquals(expectedOperations.size(), actualOperations.size());
        for (int i = 0; i < expectedOperations.size(); i++) {
            assertOperationEquals(expectedOperations.get(i), actualOperations.get(i));
        }

        var callSiteTargetOperation = actualOperations.get(0);
        var readArrayLengthOperation = actualOperations.get(1);
        var exceptionHandlersOperation = actualOperations.get(2);
        var oopMapOperation = actualOperations.get(3);

        // Check receiver proxy identities across operations.
        Assert.assertSame(exceptionHandlersOperation.receiver(), oopMapOperation.receiver());
        Assert.assertSame(callSiteTargetOperation.receiver(), readArrayLengthOperation.args()[0]);

        // Check proxy identities within an operation.
        var assumptionResult = (Assumptions.AssumptionResult<?>) callSiteTargetOperation.resultOrMarker();
        Assumptions assumptions = recordAssumptions(assumptionResult);
        var callSiteTargetValue = (Assumptions.CallSiteTargetValue) assumptions.iterator().next();
        Assert.assertSame(callSiteTargetOperation.receiver(), callSiteTargetValue.callSite);
        Assert.assertSame(assumptionResult.getResult(), callSiteTargetValue.methodHandle);
    }

    private static void assertOperationEquals(OperationRecorder.RecordedOperation expectedOperation,
                    OperationRecorder.RecordedOperation actualOperation) {
        Assert.assertEquals(expectedOperation.receiver(), actualOperation.receiver());
        Assert.assertTrue(Objects.deepEquals(expectedOperation.args(), actualOperation.args()));
        Object expectedResult = expectedOperation.resultOrMarker();
        Object actualResult = actualOperation.resultOrMarker();
        if (expectedResult instanceof SpecialResultMarker.ExceptionThrownMarker) {
            Assert.assertTrue(actualResult instanceof SpecialResultMarker.ExceptionThrownMarker);
            return;
        }
        if (expectedResult instanceof Assumptions.AssumptionResult<?> expectedAssumptionResult) {
            Assert.assertTrue(actualResult instanceof Assumptions.AssumptionResult<?>);
            var actualAssumptionResult = (Assumptions.AssumptionResult<?>) actualResult;
            Assert.assertEquals(expectedAssumptionResult.getResult(), actualAssumptionResult.getResult());
            Assert.assertEquals(recordAssumptions(expectedAssumptionResult), recordAssumptions(actualAssumptionResult));
            return;
        }
        Assert.assertTrue(Objects.deepEquals(expectedResult, actualResult));
    }

    private static Assumptions recordAssumptions(Assumptions.AssumptionResult<?> assumptionResult) {
        Assumptions assumptions = new Assumptions();
        assumptionResult.recordTo(assumptions);
        return assumptions;
    }

    public static Object dummyMethod() {
        try {
            throw new Exception();
        } catch (Exception e) {
            return null;
        }
    }

    private RecordedCompilationUnit createRecordedCompilationUnit() {
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

        return new RecordedCompilationUnit(
                        new HotSpotCompilationRequest(dummyMethod, INVOCATION_ENTRY_BCI, 0L, 1),
                        "test configuration",
                        false,
                        Platform.ofCurrentHost(),
                        new EconomicHashMap<>(),
                        new RecordedForeignCallLinkages(EconomicMap.create()),
                        new CompilationTaskProduct.CompilationTaskException("test exception", "test stack trace"),
                        operations);
    }

    private static final class TestProxyFactory implements CompilationProxies.ProxyFactory {
        private final CompilerInterfaceDeclarations declarations;

        private TestProxyFactory(CompilerInterfaceDeclarations declarations) {
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
