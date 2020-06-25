/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.tck.TruffleRunner;

/**
 * This test tries to create various {@link #getConfigurations() combination} of recursive types.
 * For those combination that {@link #createConfig can be created}, member functions are called to
 * test whether they correctly handle the recursion.
 */
@RunWith(Parameterized.class)
public class RecursiveTypesTest {

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule();
    private DataLayout dataLayout;

    private static class TypeFactory {

        private final Function<Type, Type> ctor;
        private final BiConsumer<Type, Type> patch;
        private final Class<? extends Type> type;
        private final String suffix;

        TypeFactory(Class<? extends Type> type, Function<Type, Type> ctor, BiConsumer<Type, Type> patch) {
            this(null, type, ctor, patch);
        }

        TypeFactory(String suffix, Class<? extends Type> type, Function<Type, Type> ctor, BiConsumer<Type, Type> patch) {
            this.type = type;
            this.ctor = ctor;
            this.patch = patch;
            this.suffix = suffix;
        }

        Type create(Type innerType) {
            return ctor.apply(innerType);
        }

        void setInnerType(Type receiver, Type innerType) {
            patch.accept(receiver, innerType);
        }

        @Override
        public String toString() {
            if (suffix == null) {
                return type.getSimpleName();
            }
            return type.getSimpleName() + suffix;
        }
    }

    public static final String MYSTRUCT = "mystruct";

    private static final TypeFactory[] AGGREGATE_TYPES = {
                    new TypeFactory(PointerType.class, PointerType::new, (ptr, type) -> ((PointerType) ptr).setPointeeType(type)),
                    new TypeFactory(VectorType.class, (type) -> new VectorType(type, 1), (ptr, type) -> ((VectorType) ptr).setElementType(type)),
                    new TypeFactory(ArrayType.class, (type) -> new ArrayType(type, 1), (ptr, type) -> ((ArrayType) ptr).setElementType(type)),
                    new TypeFactory(StructureType.class, (type) -> StructureType.createUnnamed(false, type), (ptr, type) -> ((StructureType) ptr).setElementType(0, type)),
                    new TypeFactory("Named", StructureType.class, (type) -> StructureType.createNamed(MYSTRUCT, false, type),
                                    (ptr, type) -> ((StructureType) ptr).setElementType(0, type)),
                    new TypeFactory("Packed", StructureType.class, (type) -> StructureType.createUnnamed(true, type), (ptr, type) -> ((StructureType) ptr).setElementType(0, type)),
                    new TypeFactory("PackedNamed", StructureType.class, (type) -> StructureType.createNamed(MYSTRUCT, true, type),
                                    (ptr, type) -> ((StructureType) ptr).setElementType(0, type)),
                    new TypeFactory(FunctionType.class, (type) -> new FunctionType(type, 0, false), (ptr, type) -> ((FunctionType) ptr).setReturnType(type))
    };

    private static final Map<Class<? extends Type>, TypeFactory> TYPE_FACTORY_MAP = new HashMap<>();

    static {
        for (TypeFactory tf : AGGREGATE_TYPES) {
            TYPE_FACTORY_MAP.put(tf.type, tf);
        }
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> getConfigurations() {
        ArrayList<Object[]> params = new ArrayList<>();
        for (TypeFactory root : AGGREGATE_TYPES) {
            createConfig(params, root, Collections.emptyList());
            for (TypeFactory other : AGGREGATE_TYPES) {
                createConfig(params, root, Arrays.asList(other));
                for (TypeFactory other2 : AGGREGATE_TYPES) {
                    createConfig(params, root, Arrays.asList(other, other2));
                }
            }
        }
        return params;
    }

    private static void createConfig(ArrayList<Object[]> configs, TypeFactory rootFactory, List<TypeFactory> otherFactories) {
        try {
            String name = Stream.concat(Stream.of(rootFactory), otherFactories.stream()).map(TypeFactory::toString).collect(Collectors.joining("-"));
            Type rootType = createRecursiveType(rootFactory, otherFactories);
            Type copyType = createRecursiveType(rootFactory, otherFactories);
            configs.add(new Object[]{name, rootType, copyType});
            Type indirectRootType = rootFactory.create(rootType);
            Type indirectCopyType = rootFactory.create(copyType);
            configs.add(new Object[]{name + "_indirect", indirectRootType, indirectCopyType});
        } catch (LLVMParserException | AssertionError e) {
            // cannot create type
        }
    }

    private static Type createRecursiveType(TypeFactory rootFactory, List<TypeFactory> otherFactories) {
        Type rootType = rootFactory.create(null);
        Type type = rootType;
        for (TypeFactory tf : otherFactories) {
            type = tf.create(type);
        }
        setInnerType(rootType, type);
        return rootType;
    }

    private DataLayout getTargetDataLayout() {
        if (dataLayout == null) {
            TruffleLanguage.Env env = runWithPolyglot.getTruffleTestEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get("llvm");
            env.initializeLanguage(llvmInfo);
            dataLayout = LLVMLanguage.getContext().getLibsulongDataLayout();
            Assert.assertNotNull(dataLayout);
        }
        return dataLayout;
    }

    private final Type type;
    private final Type copy;

    public RecursiveTypesTest(String name, Type type, Type copy) {
        assert name != null;
        this.type = type;
        this.copy = copy;
    }

    private static void setInnerType(Type base, Type inner) {
        TYPE_FACTORY_MAP.get(base.getClass()).setInnerType(base, inner);
    }

    @Test
    public void testToString() {
        String s = type.toString();
        Assert.assertTrue(s.contains("recursive") || s.contains(MYSTRUCT));
    }

    @Test
    public void testHashCode() {
        type.hashCode();
    }

    @Test
    public void testEquals() {
        Assert.assertTrue(type.equals(copy));
    }

    @Test
    public void getBitSize() throws Type.TypeOverflowException {
        try {
            type.getBitSize();
        } catch (UnsupportedOperationException e) {
            Assume.assumeTrue("Unpacked structs do not support getBitSize", false);
        }
    }

    @Test
    public void getAlignment() {
        type.getAlignment(getTargetDataLayout());
    }

    @Test
    public void getSize() throws Type.TypeOverflowException {
        type.getSize(getTargetDataLayout());
    }

    @Test
    public void getPadding() throws Type.TypeOverflowException {
        Assume.assumeTrue("Not an AggregateType:", type instanceof AggregateType);
        ((AggregateType) type).getOffsetOf(0, getTargetDataLayout());
    }

}
