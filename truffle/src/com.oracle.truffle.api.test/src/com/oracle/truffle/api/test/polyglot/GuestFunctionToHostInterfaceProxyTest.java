/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.Value.Optimized;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.asm.ClassWriter;
import com.oracle.truffle.api.impl.asm.Opcodes;
import com.oracle.truffle.api.impl.asm.Type;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.polyglot.PolyglotGeneratedProxy;

public class GuestFunctionToHostInterfaceProxyTest extends AbstractPolyglotTest {

    private static final class TestGeneratedProxy extends PolyglotGeneratedProxy {

        private static final long serialVersionUID = 1L;

        TestGeneratedProxy(Object receiver, CallTarget equalsTarget, CallTarget hashCodeTarget, CallTarget toStringTarget) {
            super(null, receiver, null, equalsTarget, hashCodeTarget, toStringTarget);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> Object raise(Throwable failure) throws E {
        throw (E) failure;
    }

    @Before
    public void setup() {
        setupEnv();
    }

    @FunctionalInterface
    public interface BiFunctionLike<T, U, R> {
        <RR extends R, RRR extends RR> RRR apply(T t, U u);
    }

    @FunctionalInterface
    public interface BiFunctionBase<T, U, R> extends BiFunctionLike<T, U, R> {
    }

    @FunctionalInterface
    public interface BiFunctionReturningValue<T, U> extends BiFunctionBase<T, U, Value> {
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastMixedFunction {
        long apply(long left, int right);

        default long twice(long value) {
            return value * 2;
        }
    }

    @Optimized
    @HostAccess.Implementable
    public interface FastArithmetic {
        int add(int left, int right);

        int subtract(int left, int right);

        default int twice(int value) {
            return value * 2;
        }
    }

    @FunctionalInterface
    public interface SharedSumFunction {
        long apply(long left, int right);
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FirstFastSharedSumFunction extends SharedSumFunction {
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface SecondFastSharedSumFunction extends SharedSumFunction {
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastVarArgsFunction {
        String apply(String prefix, Object... arguments);
    }

    @FunctionalInterface
    public interface LegacyVarArgsFunction {
        String apply(String prefix, Object... arguments);
    }

    @FunctionalInterface
    public interface LegacyVarArgsOnlyFunction {
        String apply(Object... arguments);
    }

    public interface ArrayFunction {
        int apply(Object[] arguments);
    }

    public interface VarArgsFunction {
        int apply(Object... arguments);
    }

    @FunctionalInterface
    public interface LegacyInheritedVarArgsFunction extends ArrayFunction, VarArgsFunction {
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastInheritedVarArgsFunction extends ArrayFunction, VarArgsFunction {
    }

    @FunctionalInterface
    public interface LegacyInheritedArrayFunction extends VarArgsFunction, ArrayFunction {
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastInheritedArrayFunction extends VarArgsFunction, ArrayFunction {
    }

    public interface ObjectFunction {
        Object get();
    }

    public interface StringFunction {
        String get();
    }

    @FunctionalInterface
    public interface LegacyCovariantFunction extends ObjectFunction, StringFunction {
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastCovariantFunction extends ObjectFunction, StringFunction {
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastConcurrentFunction {
        long apply(long left, int right);
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastPrimitiveFunction {
        boolean apply(boolean booleanValue, byte byteValue, char charValue, short shortValue, int intValue, long longValue, float floatValue, double doubleValue);
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastVoidFunction {
        void apply(int value);
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastByteFunction {
        byte apply();
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastCharacterFunction {
        char apply();
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastShortFunction {
        short apply();
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastLongFunction {
        long apply();
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastFloatFunction {
        float apply();
    }

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface FastDoubleFunction {
        double apply();
    }

    public interface GenericResult<T> {
        T generic();
    }

    public interface NumberResult {
        Number covariant();
    }

    @Optimized
    @HostAccess.Implementable
    public interface GeneratedObject extends GenericResult<String>, NumberResult {
        int add(int left, int right);

        int subtract(int left, int right);

        String overloaded(int value);

        String overloaded(String value);

        @Override
        Integer covariant();

        int answer();

        String join(String prefix, Object... values);

        default String greeting() {
            return "java";
        }
    }

    public interface LegacyVarArgs {
        String join(String prefix, Object... values);
    }

    public interface DefaultConversionsBase {
        default boolean identity(boolean value) {
            return value;
        }

        default byte identity(byte value) {
            return value;
        }

        default char identity(char value) {
            return value;
        }

        default short identity(short value) {
            return value;
        }

        default int identity(int value) {
            return value;
        }

        default long identity(long value) {
            return value;
        }

        default float identity(float value) {
            return value;
        }

        default double identity(double value) {
            return value;
        }

        default double mixed(long first, double second, int third, long fourth, double fifth) {
            return first + second + third + fourth + fifth;
        }

        default Object[] identity(Object[] value) {
            return value;
        }

        default void store(int[] values, int value) {
            values[0] = value;
        }
    }

    @Optimized
    @HostAccess.Implementable
    public interface DefaultConversions extends DefaultConversionsBase {
    }

    @Optimized
    public interface AnnotationOnly {
        int answer();

        int other();
    }

    @SuppressWarnings({"static-method"})
    @ExportLibrary(InteropLibrary.class)
    static final class SumFunctionObject implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments) {
            return (long) arguments[0] + (int) arguments[1];
        }
    }

    @SuppressWarnings({"static-method"})
    @ExportLibrary(InteropLibrary.class)
    static final class PrimitiveFunctionObject implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments) {
            return arguments.length == 8;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class PrimitiveReturnFunctionObject implements TruffleObject {
        final Object returnValue;

        PrimitiveReturnFunctionObject(Object returnValue) {
            this.returnValue = returnValue;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(@SuppressWarnings("unused") Object[] arguments) {
            return returnValue;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class VoidFunctionObject implements TruffleObject {
        int lastValue;

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments) {
            lastValue = (int) arguments[0];
            return lastValue;
        }
    }

    @SuppressWarnings({"static-method"})
    @ExportLibrary(InteropLibrary.class)
    static final class JoinerFunctionObject implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object[] arguments) {
            return Arrays.stream(arguments).map(a -> a.toString()).collect(Collectors.joining(", "));
        }
    }

    @SuppressWarnings({"static-method"})
    @ExportLibrary(InteropLibrary.class)
    static final class ArrayOfFunctionObject implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object[] arguments) {
            return new GuestArrayObject(arguments);
        }
    }

    @SuppressWarnings({"static-method"})
    @ExportLibrary(InteropLibrary.class)
    static final class GuestArrayObject implements TruffleObject {

        private final Object[] array;

        GuestArrayObject(Object[] array) {
            this.array = array;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return array.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws UnsupportedMessageException {
            if (!isArrayElementReadable(index)) {
                throw UnsupportedMessageException.create();
            }
            return array[(int) index];
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < array.length;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused"})
    static final class MembersObject implements TruffleObject {
        private final Map<String, ?> map;

        MembersObject(Map<String, ?> keys) {
            this.map = keys;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            return ProxyArray.fromList(List.copyOf(map.keySet()));
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberReadable(String member) {
            return map.containsKey(member);
        }

        @ExportMessage
        @TruffleBoundary
        Object readMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
            if (!map.containsKey(member)) {
                throw UnknownIdentifierException.create(member);
            }
            return map.get(member);
        }
    }

    private static final TypeLiteral<BiFunctionLike<Object, Object, Object>> BF_OOO = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Value, Value, Value>> BF_VVV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, Value>> BF_OOV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, ? extends Value>> BF_OOXV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionReturningValue<Object, Object>> BFRV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, ?>> BF_OOY = new TypeLiteral<>() {
    };

    private static final TypeLiteral<BiFunctionLike<Object, Object, List<Object>>> BF_OOLO = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, List<?>>> BF_OOLY = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, List<Value>>> BF_OOLV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, List<? extends Value>>> BF_OOLXV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, ? extends List<Value>>> BF_OOXLV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, ? extends List<? extends Value>>> BF_OOXLXV = new TypeLiteral<>() {
    };

    @Test
    public void testFastAnnotatedFunctionalInterfaceProxy() {
        Value function = context.asValue(new SumFunctionObject());
        FastMixedFunction first = function.as(FastMixedFunction.class);
        FastMixedFunction second = function.as(FastMixedFunction.class);

        assertFalse(Proxy.isProxyClass(first.getClass()));
        assertThat(first.apply(40L, 2), equalTo(42L));
        assertThat(first.twice(21), equalTo(42L));
        assertThat(first.hashCode(), equalTo(second.hashCode()));
        assertTrue(first.equals(second));
        assertTrue(first.toString() != null);
    }

    @Test
    public void testDistinctGeneratedProxiesForInheritedSam() {
        Value function = context.asValue(new SumFunctionObject());
        SharedSumFunction legacy = function.as(SharedSumFunction.class);
        FirstFastSharedSumFunction first = function.as(FirstFastSharedSumFunction.class);
        SecondFastSharedSumFunction second = function.as(SecondFastSharedSumFunction.class);

        assertTrue(Proxy.isProxyClass(legacy.getClass()));
        assertFalse(Proxy.isProxyClass(first.getClass()));
        assertFalse(Proxy.isProxyClass(second.getClass()));
        assertTrue(legacy instanceof Serializable);
        assertTrue(first instanceof Serializable);
        assertTrue(second instanceof Serializable);
        assertThat(legacy.apply(20L, 22), equalTo(42L));
        assertThat(first.apply(20L, 22), equalTo(42L));
        assertThat(second.apply(20L, 22), equalTo(42L));
        assertTrue(legacy.equals(first));
        assertTrue(first.equals(legacy));
        assertTrue(first.equals((Object) second));
        assertTrue(second.equals((Object) first));
        assertThat(first.hashCode(), equalTo(legacy.hashCode()));
        for (Object proxy : new Object[]{legacy, first, second}) {
            Value roundTrip = context.asValue(proxy);
            assertThat(roundTrip, equalTo(function));
            assertThat(roundTrip.execute(20L, 22).asLong(), equalTo(42L));
        }
    }

    @Test
    public void testConcurrentGeneratedProxyCreation() throws Exception {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<FastConcurrentFunction>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try (Context threadContext = Context.newBuilder().allowAllAccess(true).build()) {
                        FastConcurrentFunction proxy = threadContext.asValue(new SumFunctionObject()).as(FastConcurrentFunction.class);
                        assertThat(proxy.apply(20L, 22), equalTo(42L));
                        return proxy;
                    }
                }));
            }
            ready.await();
            start.countDown();
            Class<?> proxyClass = futures.get(0).get().getClass();
            for (Future<FastConcurrentFunction> future : futures) {
                assertSame(proxyClass, future.get().getClass());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testConcurrentGeneratedProxyCreationInSharedEngine() throws Exception {
        try (Engine sharedEngine = Engine.create();
                        Context firstContext = Context.newBuilder(ProxyLanguage.ID).engine(sharedEngine).allowAllAccess(true).build();
                        Context secondContext = Context.newBuilder(ProxyLanguage.ID).engine(sharedEngine).allowAllAccess(true).build()) {
            firstContext.initialize(ProxyLanguage.ID);
            secondContext.initialize(ProxyLanguage.ID);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<FastConcurrentFunction> first = executor.submit(() -> firstContext.asValue(new SumFunctionObject()).as(FastConcurrentFunction.class));
                Future<FastConcurrentFunction> second = executor.submit(() -> secondContext.asValue(new SumFunctionObject()).as(FastConcurrentFunction.class));
                assertThat(first.get(30, TimeUnit.SECONDS).apply(20L, 22), equalTo(42L));
                assertThat(second.get(30, TimeUnit.SECONDS).apply(20L, 22), equalTo(42L));
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void testGeneratedProxiesCreatedBeforeLayerClaim() {
        try (Engine sharedEngine = Engine.create();
                        Context firstContext = Context.newBuilder(ProxyLanguage.ID).engine(sharedEngine).allowAllAccess(true).build();
                        Context secondContext = Context.newBuilder(ProxyLanguage.ID).engine(sharedEngine).allowAllAccess(true).build()) {
            FastMixedFunction firstBeforeClaim = firstContext.asValue(new SumFunctionObject()).as(FastMixedFunction.class);
            FastMixedFunction secondBeforeClaim = secondContext.asValue(new SumFunctionObject()).as(FastMixedFunction.class);
            firstContext.initialize(ProxyLanguage.ID);
            secondContext.initialize(ProxyLanguage.ID);
            FastMixedFunction firstAfterClaim = firstContext.asValue(new SumFunctionObject()).as(FastMixedFunction.class);
            FastMixedFunction secondAfterClaim = secondContext.asValue(new SumFunctionObject()).as(FastMixedFunction.class);
            for (FastMixedFunction proxy : List.of(firstBeforeClaim, secondBeforeClaim, firstAfterClaim, secondAfterClaim)) {
                assertThat(proxy.apply(20L, 22), equalTo(42L));
            }
        }
    }

    @Test
    public void testGeneratedMultiMethodProxiesCreatedBeforeLayerClaim() {
        try (Engine sharedEngine = Engine.create();
                        Context firstContext = Context.newBuilder(ProxyLanguage.ID).engine(sharedEngine).allowAllAccess(true).build();
                        Context secondContext = Context.newBuilder(ProxyLanguage.ID).engine(sharedEngine).allowAllAccess(true).build()) {
            ProxyObject receiver = ProxyObject.fromMap(Map.of(
                            "add", (ProxyExecutable) arguments -> arguments[0].asInt() + arguments[1].asInt(),
                            "subtract", (ProxyExecutable) arguments -> arguments[0].asInt() - arguments[1].asInt()));
            FastArithmetic first = firstContext.asValue(receiver).as(FastArithmetic.class);
            FastArithmetic second = secondContext.asValue(receiver).as(FastArithmetic.class);
            firstContext.initialize(ProxyLanguage.ID);
            secondContext.initialize(ProxyLanguage.ID);
            FastArithmetic afterClaim = secondContext.asValue(receiver).as(FastArithmetic.class);
            for (FastArithmetic proxy : List.of(first, second, afterClaim)) {
                assertThat(proxy.add(40, 2), equalTo(42));
                assertThat(proxy.subtract(40, 2), equalTo(38));
                assertThat(proxy.twice(21), equalTo(42));
            }
        }
    }

    @Test
    public void testGenericProxiesSurviveLayerClaim() {
        try (Engine sharedEngine = Engine.create();
                        Context firstContext = Context.newBuilder(ProxyLanguage.ID).engine(sharedEngine).allowAllAccess(true).build();
                        Context secondContext = Context.newBuilder(ProxyLanguage.ID).engine(sharedEngine).allowAllAccess(true).build()) {
            FastMixedFunction firstBefore = firstContext.asValue(new SumFunctionObject()).as(FastMixedFunction.class);
            FastMixedFunction secondBefore = secondContext.asValue(new SumFunctionObject()).as(FastMixedFunction.class);
            firstContext.initialize(ProxyLanguage.ID);
            assertThat(firstBefore.apply(40L, 2), equalTo(42L));
            assertThat(secondBefore.apply(40L, 2), equalTo(42L));
            secondContext.initialize(ProxyLanguage.ID);
            FastMixedFunction firstAfter = firstContext.asValue(new SumFunctionObject()).as(FastMixedFunction.class);
            FastMixedFunction secondAfter = secondContext.asValue(new SumFunctionObject()).as(FastMixedFunction.class);
            assertThat(firstBefore.apply(40L, 2), equalTo(42L));
            assertThat(secondBefore.apply(40L, 2), equalTo(42L));
            assertThat(firstAfter.apply(40L, 2), equalTo(42L));
            assertThat(secondAfter.apply(40L, 2), equalTo(42L));
        }
    }

    @Test
    public void testFastAnnotatedVarArgsFunctionalInterfaceProxy() {
        Value function = context.asValue(new JoinerFunctionObject());
        FastVarArgsFunction proxy = function.as(FastVarArgsFunction.class);

        assertFalse(Proxy.isProxyClass(proxy.getClass()));
        assertThat(proxy.apply("X", "Y", "Z"), equalTo("X, Y, Z"));
        assertThat(proxy.apply("X"), equalTo("X"));
    }

    @Test
    public void testGeneratedInheritedVarArgsPolicyMatchesLegacyProxy() {
        Value function = context.asValue((ProxyExecutable) arguments -> arguments.length);
        LegacyInheritedVarArgsFunction legacyVarArgs = function.as(LegacyInheritedVarArgsFunction.class);
        FastInheritedVarArgsFunction generatedVarArgs = function.as(FastInheritedVarArgsFunction.class);
        LegacyInheritedArrayFunction legacyArray = function.as(LegacyInheritedArrayFunction.class);
        FastInheritedArrayFunction generatedArray = function.as(FastInheritedArrayFunction.class);
        for (Object[] arguments : new Object[][]{{"X", "Y", "Z"}, {}}) {
            assertThat(generatedVarArgs.apply(arguments), equalTo(legacyVarArgs.apply(arguments)));
            assertThat(generatedArray.apply(arguments), equalTo(legacyArray.apply(arguments)));
        }
    }

    @Test
    public void testGeneratedCovariantReturnConversionMatchesLegacyProxy() {
        Value function = context.asValue((ProxyExecutable) arguments -> "result");
        ObjectFunction legacy = function.as(LegacyCovariantFunction.class);
        ObjectFunction generated = function.as(FastCovariantFunction.class);
        assertThat(generated.get(), equalTo(legacy.get()));
        assertThat(((StringFunction) generated).get(), equalTo(((StringFunction) legacy).get()));

        Value invalid = context.asValue((ProxyExecutable) arguments -> 42);
        ObjectFunction invalidLegacy = invalid.as(LegacyCovariantFunction.class);
        ObjectFunction invalidGenerated = invalid.as(FastCovariantFunction.class);
        org.junit.Assert.assertThrows(ClassCastException.class, invalidLegacy::get);
        org.junit.Assert.assertThrows(ClassCastException.class, invalidGenerated::get);
    }

    @Test
    public void testLegacyFunctionalVarArgs() {
        LegacyVarArgsFunction proxy = context.asValue(new JoinerFunctionObject()).as(LegacyVarArgsFunction.class);
        assertTrue(Proxy.isProxyClass(proxy.getClass()));
        assertThat(proxy.apply("X", "Y", "Z"), equalTo("X, Y, Z"));
        assertThat(proxy.apply("X"), equalTo("X"));
    }

    @Test
    public void testLegacyFunctionalVarArgsOnly() {
        LegacyVarArgsOnlyFunction proxy = context.asValue(new JoinerFunctionObject()).as(LegacyVarArgsOnlyFunction.class);
        assertTrue(Proxy.isProxyClass(proxy.getClass()));
        assertThat(proxy.apply("X", "Y"), equalTo("X, Y"));
        assertThat(proxy.apply(), equalTo(""));
    }

    @Test
    public void testFastAnnotatedPrimitiveDescriptors() {
        FastPrimitiveFunction function = context.asValue(new PrimitiveFunctionObject()).as(FastPrimitiveFunction.class);

        assertTrue(function.apply(true, (byte) 1, 'A', (short) 2, 3, 4L, 5.0f, 6.0d));
    }

    @Test
    public void testFastAnnotatedVoidDescriptor() {
        VoidFunctionObject executable = new VoidFunctionObject();
        FastVoidFunction function = context.asValue(executable).as(FastVoidFunction.class);

        function.apply(42);
        assertThat(executable.lastValue, equalTo(42));
    }

    @Test
    public void testFastAnnotatedPrimitiveReturnDescriptors() {
        assertThat(context.asValue(new PrimitiveReturnFunctionObject((byte) 1)).as(FastByteFunction.class).apply(), equalTo((byte) 1));
        assertThat(context.asValue(new PrimitiveReturnFunctionObject('A')).as(FastCharacterFunction.class).apply(), equalTo('A'));
        assertThat(context.asValue(new PrimitiveReturnFunctionObject((short) 2)).as(FastShortFunction.class).apply(), equalTo((short) 2));
        assertThat(context.asValue(new PrimitiveReturnFunctionObject(3L)).as(FastLongFunction.class).apply(), equalTo(3L));
        assertThat(context.asValue(new PrimitiveReturnFunctionObject(4.0f)).as(FastFloatFunction.class).apply(), equalTo(4.0f));
        assertThat(context.asValue(new PrimitiveReturnFunctionObject(5.0d)).as(FastDoubleFunction.class).apply(), equalTo(5.0d));
    }

    @Test
    public void testGeneratedObjectMethodsAndInheritance() {
        Map<String, Object> members = Map.of(
                        "add", (ProxyExecutable) arguments -> arguments[0].asInt() + arguments[1].asInt(),
                        "subtract", (ProxyExecutable) arguments -> arguments[0].asInt() - arguments[1].asInt(),
                        "overloaded", (ProxyExecutable) arguments -> arguments[0].isNumber() ? "int:" + arguments[0].asInt() : "string:" + arguments[0].asString(),
                        "generic", (ProxyExecutable) arguments -> "generic",
                        "covariant", (ProxyExecutable) arguments -> 42,
                        "answer", 42);
        GeneratedObject proxy = context.asValue(ProxyObject.fromMap(members)).as(GeneratedObject.class);

        assertFalse(Proxy.isProxyClass(proxy.getClass()));
        assertThat(proxy.add(40, 2), equalTo(42));
        assertThat(proxy.subtract(44, 2), equalTo(42));
        assertThat(proxy.overloaded(42), equalTo("int:42"));
        assertThat(proxy.overloaded("answer"), equalTo("string:answer"));
        assertThat(proxy.generic(), equalTo("generic"));
        GenericResult<String> generic = proxy;
        assertThat(generic.generic(), equalTo("generic"));
        assertThat(proxy.covariant(), equalTo(42));
        NumberResult covariant = proxy;
        assertThat(covariant.covariant(), equalTo(42));
        assertThat(proxy.answer(), equalTo(42));
        assertThat(proxy.greeting(), equalTo("java"));
    }

    @Test
    public void testGeneratedObjectDefaultAndObjectMethodOverrides() {
        GeneratedObject overridden = context.asValue(ProxyObject.fromMap(Map.of(
                        "greeting", (ProxyExecutable) arguments -> "guest",
                        "equals", (ProxyExecutable) arguments -> true,
                        "hashCode", (ProxyExecutable) arguments -> 42,
                        "toString", (ProxyExecutable) arguments -> "guest object"))).as(GeneratedObject.class);
        assertThat(overridden.greeting(), equalTo("guest"));
        assertTrue(overridden.equals(new Object()));
        assertThat(overridden.hashCode(), equalTo(42));
        assertThat(overridden.toString(), equalTo("guest object"));

        Value value = context.asValue(ProxyObject.fromMap(Map.of()));
        GeneratedObject first = value.as(GeneratedObject.class);
        GeneratedObject second = value.as(GeneratedObject.class);
        assertTrue(first.equals(second));
        assertThat(first.hashCode(), equalTo(second.hashCode()));
        assertTrue(first.toString() != null);
    }

    @Test
    public void testGeneratedInheritedDefaultConversions() {
        DefaultConversions proxy = context.asValue(ProxyObject.fromMap(Map.of())).as(DefaultConversions.class);
        assertFalse(Proxy.isProxyClass(proxy.getClass()));
        assertTrue(proxy.identity(true));
        assertThat(proxy.identity((byte) 1), equalTo((byte) 1));
        assertThat(proxy.identity('A'), equalTo('A'));
        assertThat(proxy.identity((short) 2), equalTo((short) 2));
        assertThat(proxy.identity(3), equalTo(3));
        assertThat(proxy.identity(4L), equalTo(4L));
        assertThat(proxy.identity(5.5f), equalTo(5.5f));
        assertThat(proxy.identity(6.5d), equalTo(6.5d));
        assertThat(proxy.mixed(1L, 2.5d, 3, 4L, 5.5d), equalTo(16d));
        Object[] values = {"value"};
        assertSame(values, proxy.identity(values));
        assertSame(null, proxy.identity((Object[]) null));
        int[] stored = {0};
        proxy.store(stored, 42);
        assertThat(stored[0], equalTo(42));
    }

    @Test
    public void testGeneratedObjectVarArgsMatchesLegacyProxy() {
        ProxyExecutable join = arguments -> arguments.length + ":" + arguments[1].getArraySize();
        Value value = context.asValue(ProxyObject.fromMap(Map.of("join", join)));
        GeneratedObject generated = value.as(GeneratedObject.class);
        LegacyVarArgs legacy = value.as(LegacyVarArgs.class);

        assertTrue(legacy instanceof Serializable);
        assertTrue(generated instanceof Serializable);
        assertThat(generated.join("x", 1, 2), equalTo(legacy.join("x", 1, 2)));
        assertThat(generated.join("x"), equalTo(legacy.join("x")));
        assertTrue(generated.equals((Object) legacy));
        assertTrue(legacy.equals((Object) generated));
        assertThat(generated.hashCode(), equalTo(legacy.hashCode()));
        assertThat(context.asValue(generated), equalTo(value));
        assertThat(context.asValue(legacy), equalTo(value));
    }

    @Test
    public void testGeneratedObjectExceptionsAndClosedContext() {
        GeneratedObject throwing = context.asValue(ProxyObject.fromMap(Map.of(
                        "answer", (ProxyExecutable) arguments -> {
                            throw new IllegalStateException("expected");
                        }))).as(GeneratedObject.class);
        PolyglotException exception = org.junit.Assert.assertThrows(PolyglotException.class, throwing::answer);
        assertTrue(exception.isHostException());
        assertThat(exception.asHostException().getMessage(), equalTo("expected"));

        Context local = Context.newBuilder().allowAllAccess(true).build();
        GeneratedObject closed = local.asValue(ProxyObject.fromMap(Map.of("answer", 42))).as(GeneratedObject.class);
        local.close();
        org.junit.Assert.assertThrows(IllegalStateException.class, closed::answer);
    }

    @Test
    public void testGeneratedFunctionJavaMethodsAfterContextClose() {
        Context local = Context.newBuilder().allowAllAccess(true).build();
        FastMixedFunction closed = local.asValue(new SumFunctionObject()).as(FastMixedFunction.class);
        local.close();
        assertThat(closed.twice(21), equalTo(42L));
        assertTrue(closed.equals(closed));
        closed.hashCode();
        assertTrue(closed.toString() != null);
    }

    @Test
    public void testGeneratedProxyUnwrapsWithoutEnteredContext() {
        Value function = context.asValue(new SumFunctionObject());
        FastMixedFunction functionProxy = function.as(FastMixedFunction.class);
        assertThat(Value.asValue(functionProxy), equalTo(function));
        Value object = context.asValue(ProxyObject.fromMap(Map.of("answer", 42)));
        GeneratedObject objectProxy = object.as(GeneratedObject.class);
        assertThat(Value.asValue(objectProxy), equalTo(object));
    }

    private record AnchoredProxy(PolyglotGeneratedProxy proxy, WeakReference<Context> context) {
    }

    private static AnchoredProxy newAnchoredProxy(boolean function) {
        Context local = Context.newBuilder().allowAllAccess(true).build();
        PolyglotGeneratedProxy proxy = function
                        ? (PolyglotGeneratedProxy) local.asValue(new SumFunctionObject()).as(FastMixedFunction.class)
                        : (PolyglotGeneratedProxy) local.asValue(ProxyObject.fromMap(Map.of("answer", 42))).as(GeneratedObject.class);
        return new AnchoredProxy(proxy, new WeakReference<>(local));
    }

    @Test
    public void testGeneratedProxyAnchorsCreatorContext() {
        for (boolean function : new boolean[]{true, false}) {
            AnchoredProxy anchored = newAnchoredProxy(function);
            try {
                GCUtils.assertNotGc("Generated proxy must keep its creator context alive", anchored.context());
                if (function) {
                    assertThat(((FastMixedFunction) anchored.proxy()).apply(20, 22), equalTo(42L));
                } else {
                    assertThat(((GeneratedObject) anchored.proxy()).answer(), equalTo(42));
                }
            } finally {
                Context creator = anchored.context().get();
                if (creator != null) {
                    creator.close();
                }
            }
        }
    }

    @Test
    public void testGeneratedProxyObjectMethodsUseSeparateTargets() {
        Object other = new Object();
        // Keep assertion machinery out of native runtime-compilation graphs.
        CallTarget equalsTarget = new RootNode(null) {
            @Override
            @TruffleBoundary
            public Object execute(VirtualFrame frame) {
                assertThat(frame.getArguments().length, equalTo(3));
                assertSame(other, frame.getArguments()[2]);
                return true;
            }
        }.getCallTarget();
        CallTarget hashCodeTarget = new RootNode(null) {
            @Override
            @TruffleBoundary
            public Object execute(VirtualFrame frame) {
                assertThat(frame.getArguments().length, equalTo(2));
                return 42;
            }
        }.getCallTarget();
        CallTarget toStringTarget = new RootNode(null) {
            @Override
            @TruffleBoundary
            public Object execute(VirtualFrame frame) {
                assertThat(frame.getArguments().length, equalTo(2));
                return "proxy";
            }
        }.getCallTarget();
        PolyglotGeneratedProxy proxy = new TestGeneratedProxy(null, equalsTarget, hashCodeTarget, toStringTarget);
        assertTrue(proxy.equals(other));
        assertThat(proxy.hashCode(), equalTo(42));
        assertTrue(proxy.toString() != null);
    }

    @Test
    public void testGeneratedProxyObjectMethodsWithoutGuestTargets() {
        SumFunctionObject receiver = new SumFunctionObject();
        PolyglotGeneratedProxy proxy = new TestGeneratedProxy(receiver, null, null, null);
        assertTrue(proxy.equals(proxy));
        assertFalse(proxy.equals(new Object()));
        assertThat(proxy.hashCode(), equalTo(System.identityHashCode(receiver)));
        assertTrue(proxy.toString() != null);
    }

    @Test
    public void testGeneratedProxyObjectMethodsFallbackFromGuestTargets() {
        SumFunctionObject receiver = new SumFunctionObject();
        int[] calls = {0};
        CallTarget target = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                calls[0]++;
                throw new UnsupportedOperationException("no guest override");
            }
        }.getCallTarget();
        PolyglotGeneratedProxy proxy = new TestGeneratedProxy(receiver, target, target, target);
        assertTrue(proxy.equals(proxy));
        assertThat(proxy.hashCode(), equalTo(System.identityHashCode(receiver)));
        assertTrue(proxy.toString() != null);
        assertThat(calls[0], equalTo(3));
    }

    @Test
    public void testGeneratedProxyObjectMethodsWrapCheckedExceptions() {
        IOException failure = new IOException("expected");
        CallTarget target = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return GuestFunctionToHostInterfaceProxyTest.<RuntimeException> raise(failure);
            }
        }.getCallTarget();
        PolyglotGeneratedProxy proxy = new TestGeneratedProxy(null, target, target, target);
        UndeclaredThrowableException equalsFailure = org.junit.Assert.assertThrows(UndeclaredThrowableException.class, () -> proxy.equals(proxy));
        assertSame(failure, equalsFailure.getUndeclaredThrowable());
        UndeclaredThrowableException hashCodeFailure = org.junit.Assert.assertThrows(UndeclaredThrowableException.class, proxy::hashCode);
        assertSame(failure, hashCodeFailure.getUndeclaredThrowable());
        UndeclaredThrowableException toStringFailure = org.junit.Assert.assertThrows(UndeclaredThrowableException.class, proxy::toString);
        assertSame(failure, toStringFailure.getUndeclaredThrowable());
    }

    @Test
    public void testOptimizedDoesNotAuthorizeImplementation() {
        ProxyObject object = ProxyObject.fromMap(Map.of("answer", 42, "other", 0));
        try (Context all = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            assertThat(all.asValue(object).as(AnnotationOnly.class).answer(), equalTo(42));
        }
        HostAccess customAccess = HostAccess.newBuilder().allowImplementations(AnnotationOnly.class).build();
        try (Context custom = Context.newBuilder().allowHostAccess(customAccess).build()) {
            assertThat(custom.asValue(object).as(AnnotationOnly.class).answer(), equalTo(42));
        }
        for (HostAccess denied : List.of(HostAccess.NONE, HostAccess.EXPLICIT)) {
            try (Context restricted = Context.newBuilder().allowHostAccess(denied).build()) {
                org.junit.Assert.assertThrows(ClassCastException.class, () -> restricted.asValue(object).as(AnnotationOnly.class));
            }
        }
    }

    @Test
    public void testGeneratedProxyNamesAreIndependentOfClassLoader() throws ReflectiveOperationException {
        if (TruffleOptions.AOT) {
            return;
        }
        String name = "com.oracle.truffle.api.test.polyglot.RepeatedProxy";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                        name.replace('.', '/'), null, "java/lang/Object", null);
        writer.visitAnnotation(Type.getDescriptor(Optimized.class), true).visitEnd();
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "result", "()I", null, null).visitEnd();
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        Class<?>[] proxyTypes = new Class<?>[2];
        Value value = context.asValue(ProxyObject.fromMap(Map.of("result", 42)));
        for (int i = 0; i < proxyTypes.length; i++) {
            Class<?> interfaceType = new ClassLoader(getClass().getClassLoader()) {
                Class<?> define() {
                    return defineClass(name, bytes, 0, bytes.length);
                }
            }.define();
            Object proxy = value.as(interfaceType);
            assertThat(interfaceType.getMethod("result").invoke(proxy), equalTo(42));
            proxyTypes[i] = proxy.getClass();
            assertSame(interfaceType, proxyTypes[i].getInterfaces()[0]);
        }
        assertNotSame(proxyTypes[0], proxyTypes[1]);
        assertThat(proxyTypes[0].getName(), equalTo(proxyTypes[1].getName()));
    }

    private static final class HiddenSignatureType {
    }

    @Test
    public void testGeneratedProxyRejectsInaccessibleSignatures() throws ClassNotFoundException {
        if (TruffleOptions.AOT) {
            return;
        }
        // Define the annotation dynamically so unsupported interfaces do not enter hosted discovery.
        for (Class<?> resultType : new Class<?>[]{HiddenSignatureType.class, Class.forName("sun.security.x509.X500Name")}) {
            String name = "com.oracle.truffle.api.test.polyglot.InaccessibleProxy";
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                            name.replace('.', '/'), null, "java/lang/Object", null);
            writer.visitAnnotation(Type.getDescriptor(Optimized.class), true).visitEnd();
            writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "result", "()" + Type.getDescriptor(resultType), null, null).visitEnd();
            writer.visitEnd();
            byte[] bytes = writer.toByteArray();
            Class<?> interfaceType = new ClassLoader(getClass().getClassLoader()) {
                Class<?> define() {
                    return defineClass(name, bytes, 0, bytes.length);
                }
            }.define();
            Value value = context.asValue(ProxyObject.fromMap(Map.of("result", 42)));
            UnsupportedOperationException failure = org.junit.Assert.assertThrows(UnsupportedOperationException.class, () -> value.as(interfaceType));
            assertTrue(failure.getCause().getMessage().contains(resultType.getTypeName()));
            if (resultType == HiddenSignatureType.class) {
                assertTrue(failure.getCause().getMessage().contains("public"));
            } else {
                assertTrue(failure.getCause().getMessage().contains("--add-exports=java.base/sun.security.x509=ALL-UNNAMED"));
            }
            UnsupportedOperationException retry = org.junit.Assert.assertThrows(UnsupportedOperationException.class, () -> value.as(interfaceType));
            assertNotSame(failure.getCause(), retry.getCause());
        }
    }

    @Test
    public void testGenericReturnTypeOfPolyglotFunctionProxy() {
        Value join = context.asValue(new JoinerFunctionObject());
        Value collect = context.asValue(new ArrayOfFunctionObject());

        testGenericReturnTypeOfPolyglotProxy(join, collect);
    }

    @Test
    public void testGenericReturnTypeOfPolyglotObjectProxy() {
        Value join = context.asValue(new MembersObject(Map.of("apply", new JoinerFunctionObject())));
        Value collect = context.asValue(new MembersObject(Map.of("apply", new ArrayOfFunctionObject())));

        testGenericReturnTypeOfPolyglotProxy(join, collect);
    }

    private void testGenericReturnTypeOfPolyglotProxy(Value join, Value collect) {
        assertThat(join.as(BF_OOO).apply("X", "Y"), equalTo("X, Y"));
        assertThat(join.as(BF_VVV).apply(context.asValue("X"), context.asValue("Y")), isValueOfStringEqualTo("X, Y"));
        assertThat(join.as(BF_OOV).apply("X", "Y"), isValueOfStringEqualTo("X, Y"));
        assertThat(join.as(BF_OOXV).apply("X", "Y"), isValueOfStringEqualTo("X, Y"));
        assertThat(join.as(BFRV).apply("X", "Y"), isValueOfStringEqualTo("X, Y"));
        assertThat((Object) join.as(BF_OOY).apply("X", "Y"), equalTo("X, Y"));

        final var listOfStringsXandY = isListOf(equalTo("X"), equalTo("Y"));
        final var listOfValueOfStringsXandY = isListOf(isValueOfStringEqualTo("X"), isValueOfStringEqualTo("Y"));
        assertThat(collect.as(BF_OOLO).apply("X", "Y"), listOfStringsXandY);
        assertThat(collect.as(BF_OOLY).apply("X", "Y"), listOfStringsXandY);
        assertThat(collect.as(BF_OOLV).apply("X", "Y"), listOfValueOfStringsXandY);
        assertThat(collect.as(BF_OOLXV).apply("X", "Y"), listOfValueOfStringsXandY);
        assertThat(collect.as(BF_OOXLV).apply("X", "Y"), listOfValueOfStringsXandY);
        assertThat(collect.as(BF_OOXLXV).apply("X", "Y"), listOfValueOfStringsXandY);
    }

    public static class HostCallee {
        private static void assertNotProxy(Consumer<Object> verifier) {
            assertFalse(Proxy.isProxyClass(verifier.getClass()));
        }

        private static void verifyUsing(Consumer<Object> verifier, Object result) {
            assertNotProxy(verifier);
            verifier.accept(result);
        }

        public void functionObject(Function<Object, Object> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("A"));
        }

        public void functionValue(Function<Object, Value> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("A"));
        }

        public void bifunctionObject(BiFunction<Object, Object, Object> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        public void bifunctionValue(BiFunction<Object, Object, Value> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        public void bifunctionExtendsValue(BiFunction<Object, Object, ? extends Value> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        public void bifunctionAny(BiFunction<Object, Object, ?> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        public void bifunctionValueCustom(BiFunctionReturningValue<Object, Object> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        public void bifunctionListOfObject(BiFunction<Object, Object, List<Object>> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        public void bifunctionListOfValue(BiFunction<Object, Object, List<Value>> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        public void bifunctionListOfExtendsValue(BiFunction<Object, Object, List<? extends Value>> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        public void bifunctionListOfAny(BiFunction<Object, Object, List<?>> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        public <BFV extends BiFunction<Object, Object, Value>> void bifunctionValueFromTypeVar(BFV f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        @SuppressWarnings("all")
        public <V extends Value, T extends V, BF extends BiFunction<Object, Object, ? extends T>, BFV extends BF> void bifunctionValueFromTypeVar2(BFV f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        public <ANY> void bifunctionUnboundedTypeVar(BiFunction<Object, Object, ANY> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        public <LV extends List<Value>> void bifunctionListOfValueFromTypeVar(BiFunction<Object, Object, LV> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }

        @SuppressWarnings("all")
        public <V extends Value, LV extends List<V>> void bifunctionListOfValueFromTypeVar2(BiFunction<Object, Object, LV> f, Consumer<Object> verifier) {
            verifyUsing(verifier, f.apply("X", "Y"));
        }
    }

    @Test
    public void testGenericReturnTypeOfPolyglotFunctionProxyForHostMethodParameter() {
        Value join = context.asValue(new JoinerFunctionObject());
        Value collect = context.asValue(new ArrayOfFunctionObject());
        Value target = context.asValue(new HostCallee());

        target.invokeMember("functionObject", join, verifyThat(equalTo("A")));
        target.invokeMember("functionValue", join, verifyThat(isValueOfStringEqualTo("A")));

        target.invokeMember("bifunctionObject", join, verifyThat(equalTo("X, Y")));
        target.invokeMember("bifunctionValue", join, verifyThat(isValueOfStringEqualTo("X, Y")));
        target.invokeMember("bifunctionExtendsValue", join, verifyThat(isValueOfStringEqualTo("X, Y")));
        target.invokeMember("bifunctionValueCustom", join, verifyThat(isValueOfStringEqualTo("X, Y")));
        target.invokeMember("bifunctionAny", join, verifyThat(equalTo("X, Y")));

        final var listOfStringsXandY = isListOf(equalTo("X"), equalTo("Y"));
        final var listOfValueOfStringsXandY = isListOf(isValueOfStringEqualTo("X"), isValueOfStringEqualTo("Y"));
        target.invokeMember("bifunctionListOfObject", collect, verifyThat(listOfStringsXandY));
        target.invokeMember("bifunctionListOfValue", collect, verifyThat(listOfValueOfStringsXandY));
        target.invokeMember("bifunctionListOfExtendsValue", collect, verifyThat(listOfValueOfStringsXandY));
        target.invokeMember("bifunctionListOfAny", collect, verifyThat(listOfStringsXandY));

        // some more contrived cases
        target.invokeMember("bifunctionValueFromTypeVar", join, verifyThat(isValueOfStringEqualTo("X, Y")));
        target.invokeMember("bifunctionValueFromTypeVar2", join, verifyThat(isValueOfStringEqualTo("X, Y")));
        target.invokeMember("bifunctionUnboundedTypeVar", join, verifyThat(equalTo("X, Y")));
        target.invokeMember("bifunctionListOfValueFromTypeVar", collect, verifyThat(listOfValueOfStringsXandY));
        target.invokeMember("bifunctionListOfValueFromTypeVar2", collect, verifyThat(listOfValueOfStringsXandY));
    }

    private static <T> Consumer<T> verifyThat(Matcher<T> equalTo) {
        return (result) -> {
            assertThat(result, equalTo);
        };
    }

    static Matcher<? super Value> isValueOfStringEqualTo(String expectedString) {
        class StringValueMatcher<T> extends BaseMatcher<T> {
            private final String expectedString;

            StringValueMatcher(String expectedString) {
                this.expectedString = expectedString;
            }

            @Override
            public boolean matches(Object item) {
                return item instanceof Value v && v.isString() && v.asString().equals(expectedString);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a Value of a String equal to ").appendValue(expectedString);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                super.describeMismatch(item, description);
                if (!(item instanceof Value v)) {
                    description.appendText(" (not a Value)");
                } else if (!v.isString()) {
                    description.appendText(" (a Value of a different type)");
                }
            }
        }
        return new StringValueMatcher<>(expectedString);
    }

    static Matcher<? super List<?>> isListOf(Matcher<?>... expectedValues) {
        class ListMatcher<T> extends BaseMatcher<T> {
            private final List<Matcher<?>> expectedValues;

            ListMatcher(List<Matcher<?>> expectedValues) {
                this.expectedValues = expectedValues;
            }

            @Override
            public boolean matches(Object item) {
                return item instanceof List<?> list &&
                                list.size() == expectedValues.size() &&
                                IntStream.range(0, list.size()).allMatch(i -> expectedValues.get(i).matches(list.get(i)));
            }

            @Override
            public void describeTo(Description description) {
                description.appendList("a List of [", ", ", "]", this.expectedValues);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                if (item instanceof List<?> list) {
                    if (list.size() != expectedValues.size()) {
                        description.appendText("was a List of size ").appendValue(list.size()).appendText(", expected size ").appendValue(expectedValues.size());
                    } else {
                        description.appendText("[");
                        for (int i = 0; i < list.size(); i++) {
                            if (i != 0) {
                                description.appendText(", ");
                            }
                            description.appendText(i + ": ");
                            var matcher = expectedValues.get(i);
                            if (matcher.matches(list.get(i))) {
                                description.appendText("(matched)");
                            } else {
                                matcher.describeMismatch(list.get(i), description);
                            }
                        }
                        description.appendText("]");
                    }
                } else {
                    description.appendText("was not a List: ").appendValue(item);
                }
            }
        }
        return new ListMatcher<>(List.of(expectedValues));
    }
}
