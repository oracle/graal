/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.sboutlining.concat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.nodes.ValueNode;

import com.oracle.svm.core.sboutlining.concat.SubstrateSBConcatHelper;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;

/**
 * Build-time generator for graphs that materialize a {@link StringBuilder} or
 * {@link StringBuffer} from an outlined append sequence.
 *
 * <p>
 * This is the builder and buffer counterpart of {@link SubstrateStringConcatFactory}. Its
 * combinator pipeline stringifies operands, tracks the accumulated length and coder, reproduces
 * {@code AbstractStringBuilder} capacity growth, allocates the backing array, copies the values,
 * and creates the requested object. The generated handle also accepts the initial capacity derived
 * from the original constructor.
 *
 * <p>
 * Only the argument-only recipe used by {@link com.oracle.svm.hosted.sboutlining.SBOutliningPhase}
 * is supported. {@link SubstrateSBConcatGraphBuilder} turns the combinators into build-time graph
 * operations, and {@link SubstrateSBConcatHelper} supplies the runtime state and materialization
 * operations.
 */
@SuppressWarnings("all")
@Platforms(Platform.HOSTED_ONLY.class)
public class SubstrateSBConcatFactory {

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L231-L240")
    public MethodHandle makeConcat(MethodType concatType) throws StringConcatException {
        // This bootstrap method is unlikely to be used in practice,
        // avoid optimizing it at the expense of makeConcatWithConstants

        // Mock the recipe to reuse the concat generator code
        String recipe = "\u0001".repeat(concatType.parameterCount());
        return makeConcatWithConstants(concatType, recipe);
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L353-L408")
    private MethodHandle makeConcatWithConstants(
                    MethodType concatType,
                    String recipe,
                    Object... constants)
                    throws StringConcatException {

        Objects.requireNonNull(recipe, "Recipe is null");
        Objects.requireNonNull(concatType, "Concat type is null");
        Objects.requireNonNull(constants, "Constants are null");
        assert constants.length == 0 : "we expect no constants to be passed";

        for (Object o : constants) {
            Objects.requireNonNull(o, "Cannot accept null constants");
        }

        List<String> elements = SubstrateStringConcatFactory.parseRecipe(concatType, recipe, constants);

        assert elements.stream().allMatch(e -> e == null) : "the recipe should be only TAG_ARGs";

        try {
            return generateMHInlineCopy(concatType, elements.size());
        } catch (Error e) {
            // Pass through any error
            throw e;
        } catch (Throwable t) {
            throw new StringConcatException("Generator failed", t);
        }
    }

    /**
     * <p>
     * This strategy replicates what StringBuilders are doing: it builds the byte[] array and count
     * on its own and passes these values to the StringBuilder|StringBuffer constructor.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L526-L646")
    private MethodHandle generateMHInlineCopy(MethodType mt, int numElements) {

        /*
         * Create filters and obtain filtered parameter types. Filters would be used in the
         * beginning to convert the incoming arguments into the arguments we can process (e.g.
         * Objects -> Strings). The filtered argument type list is used all over in the combinators
         * below.
         */

        Class<?>[] ptypes = mt.erase().parameterArray();
        MethodHandle[] filters = null;
        for (int i = 0; i < ptypes.length; i++) {
            Class<?> cl = ptypes[i];
            ptypes[i] = SubstrateStringConcatFactory.promoteToIntType(ptypes[i]);
            MethodHandle filter = stringifierFor(cl);
            if (filter != null) {
                if (filters == null) {
                    filters = new MethodHandle[ptypes.length];
                }
                filters[i] = filter;
                ptypes[i] = String.class;
            }
        }
        Class<?>[] pValues = new Class<?>[ptypes.length];
        Arrays.fill(pValues, ValueNode.class);

        /*
         * Start building the combinator tree. The tree "starts" with ("initialCapacity", <args>)SB,
         * and "finishes" with the (byte[], long, long)SB shape to create a new SB object. The
         * combinators are assembled bottom-up, which makes the code arguably hard to read.
         */

        // Drop all remaining parameter types, leave only helper arguments:
        MethodHandle mh = MethodHandles.dropArguments(newSBObject(mt), 3, pValues);

        /*
         * Mix in prependers. This happens when (byte[], long, long) = (storage, indexCoder, length)
         * is already known from the combinators below. We are assembling the string backwards, so
         * the index coded into indexCoder is the *ending* index.
         */

        /*
         * We need one prepender per argument. Our limited recipe has no constant prefixes.
         */
        for (int pos = 0; pos < numElements; pos++) {
            // Add prepender
            mh = filterArgumentsWithCombinerHelper(
                            mh, 1,
                            prepender(null, ptypes[pos]),
                            1, 0, // indexCoder, storage
                            3 + pos  // selected argument
            );
        }

        // the method handle input (mh) is (byte[], lengthCoderAndCapacity, <args>)
        mh = filterArgumentsWithCombinerHelper(mh, 1, getIndexCoder(), 1);
        mh = foldArgumentsWithCombinerHelper(mh, 2, getCount(), 1);

        // Fold in byte[] instantiation at argument 0
        MethodHandle newArrayCombinator;
        mh = foldArgumentsWithCombinerHelper(mh, 0, newArray(),
                        1 // lengthAndCoderCapacity
        );

        /*
         * Start combining length, capacity, and coder mixers.
         *
         * Length and Capacity is easy: all non-constant shapes have been either converted to
         * Strings, or explicit methods for getting the string length out of primitives are
         * provided.
         *
         * Coders are more interesting. Only Object, String and char arguments (and constants) can
         * have non-Latin1 encoding. It is easier to blindly convert constants to String, and deduce
         * the coder from there. Arguments would be either converted to Strings during the initial
         * filtering, or handled by specializations in MIXERS.
         *
         * The method handle shape before and after all mixers are combined in is:
         * ("lengthCoderAndCapacity, <args>)
         */

        MethodHandle mix = null;
        for (int pos = numElements - 1; pos >= 0; pos--) {
            Class<?> argClass = ptypes[pos];
            mix = mixer(argClass);
            /*
             * Compute new "lengthCoderAndCapacity" in-place using old value plus the appropriate
             * argument.
             */
            mh = filterArgumentsWithCombinerHelper(mh, 0, mix,
                            0, // old lengthCoderAndCapacity
                            pos + 1 // selected argument
            );
        }

        // removing the "initial capacity" arg
        mh = MethodHandles.dropArguments(mh, 1, ValueNode.class);

        mh = filterArgumentsWithCombinerHelper(mh, 0, initializeCoderAndCapacity(), 0, 1);

        // The method handle shape here is ("lengthCoderAndCapacity", "initial capacity", <args>).

        mh = foldArgumentsWithCombinerHelper(mh, 0, stackAllocateCoderAndCapacity());

        // The method handle shape here is ("initial capacity", <args>).

        // Apply filters, converting the arguments:
        if (filters != null) {
            mh = MethodHandles.filterArguments(mh, 1, filters);
        }

        return mh;
    }

    /*
     * Checkstyle: stop
     */

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L745-L752")
    private MethodHandle prepender(String prefix, Class<?> cl) {
        if (prefix == null) {
            return NULL_PREPENDERS.computeIfAbsent(cl, NULL_PREPEND);
        }
        return insertArgumentsHelper(
                        PREPENDERS.computeIfAbsent(cl, PREPEND), 3, prefix);
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L860-L868")
    private MethodHandle mixer(Class<?> cl) {
        return MIXERS.computeIfAbsent(cl, MIX);
    }

    // These are deliberately not lambdas to optimize startup time:
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L754-L762") private final Function<Class<?>, MethodHandle> PREPEND = new Function<>() {
        @Override
        public MethodHandle apply(Class<?> c) {
            MethodHandle prepend = SubstrateStringConcatGraphBuilder.getPrependForKind(c);
            return prepend.bindTo(kit);
        }
    };

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L765-L772") private final Function<Class<?>, MethodHandle> NULL_PREPEND = new Function<>() {
        @Override
        public MethodHandle apply(Class<?> c) {
            /* JDK 25 prepend methods use an empty string to represent a missing prefix. */
            return insertArgumentsHelper(
                            PREPENDERS.computeIfAbsent(c, PREPEND), 3, "");
        }
    };

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L860-L868") private final Function<Class<?>, MethodHandle> MIX = new Function<>() {
        @Override
        public MethodHandle apply(Class<?> c) {
            MethodHandle mix = SubstrateSBConcatGraphBuilder.getMixForKind(c);
            return mix.bindTo(kit);
        }
    };

    private MethodHandle newSBObject(MethodType mt) {
        Class<?> returnClazz = mt.returnType();
        if (returnClazz.equals(StringBuilder.class)) {
            return SubstrateSBConcatGraphBuilder.newStringBuilderMH.bindTo(kit);
        } else if (returnClazz.equals(StringBuffer.class)) {
            return SubstrateSBConcatGraphBuilder.newStringBufferMH.bindTo(kit);
        } else {
            throw VMError.shouldNotReachHereUnexpectedInput(returnClazz); // ExcludeFromJacocoGeneratedReport
        }
    }

    private MethodHandle newArray() {
        return SubstrateSBConcatGraphBuilder.newArrayMH.bindTo(kit);
    }

    /**
     * Public gateways to public "stringify" methods. These methods have the form String apply(T
     * obj), and normally delegate to {@code String.valueOf}, depending on argument's type.
     */
    private MethodHandle OBJECT_STRINGIFIER;

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L937-L944")
    private MethodHandle objectStringifier() {
        MethodHandle mh = OBJECT_STRINGIFIER;
        if (mh == null) {
            OBJECT_STRINGIFIER = mh = SubstrateStringConcatGraphBuilder.objectStringifierMH.bindTo(kit);
        }
        return mh;
    }

    private MethodHandle FLOAT_STRINGIFIER;

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L946-L952")
    private MethodHandle floatStringifier() {
        MethodHandle mh = FLOAT_STRINGIFIER;
        if (mh == null) {
            FLOAT_STRINGIFIER = mh = SubstrateStringConcatGraphBuilder.floatStringifierMH.bindTo(kit);
        }
        return mh;
    }

    private MethodHandle DOUBLE_STRINGIFIER;

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L954-L960")
    private MethodHandle doubleStringifier() {
        MethodHandle mh = DOUBLE_STRINGIFIER;
        if (mh == null) {
            DOUBLE_STRINGIFIER = mh = SubstrateStringConcatGraphBuilder.doubleStringifierMH.bindTo(kit);
        }
        return mh;
    }

    private MethodHandle getIndexCoder() {
        return SubstrateSBConcatGraphBuilder.getIndexCoderMH.bindTo(kit);
    }

    private MethodHandle getCount() {
        return SubstrateSBConcatGraphBuilder.getCountMH.bindTo(kit);
    }

    private MethodHandle initializeCoderAndCapacity() {
        return SubstrateSBConcatGraphBuilder.initializeCoderAndCapacityMH.bindTo(kit);
    }

    private MethodHandle stackAllocateCoderAndCapacity() {
        return SubstrateSBConcatGraphBuilder.stackAllocateCoderAndCapacityMH.bindTo(kit);
    }

    private final HostedGraphKit kit;

    private static final Method foldArgumentsWithCombiner = ReflectionUtil.lookupMethod(MethodHandles.class, "foldArgumentsWithCombiner",
                    MethodHandle.class, int.class, MethodHandle.class, int[].class);
    private static final Method filterArgumentsWithCombiner = ReflectionUtil.lookupMethod(MethodHandles.class, "filterArgumentsWithCombiner",
                    MethodHandle.class, int.class, MethodHandle.class, int[].class);

    private final Map<Class<?>, MethodHandle> PREPENDERS;
    private final Map<Class<?>, MethodHandle> NULL_PREPENDERS;
    private final Map<Class<?>, MethodHandle> MIXERS;

    /**
     * Returns a stringifier for references and floats/doubles only. Always returns null for other
     * primitives.
     *
     * @param t class to stringify
     * @return stringifier; null, if not available
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L1049-L1057")
    private MethodHandle stringifierFor(Class<?> t) {
        if (t == Object.class) {
            return objectStringifier();
        } else if (t == float.class) {
            return floatStringifier();
        } else if (t == double.class) {
            return doubleStringifier();
        }
        return null;
    }

    public SubstrateSBConcatFactory(HostedGraphKit kit) {
        this.kit = kit;

        PREPENDERS = new HashMap<>();
        NULL_PREPENDERS = new HashMap<>();
        MIXERS = new HashMap<>();
    }
    /*
     * Checkstyle: resume
     */

    /*
     * Helpers for binding graph constants and invoking package-private MethodHandle combiners.
     */

    private MethodHandle insertArgumentsHelper(MethodHandle mh, int pos, String value) {
        ValueNode node = kit.createObject(value);
        return MethodHandles.insertArguments(mh, pos, node);
    }

    private MethodHandle insertArgumentsHelper(MethodHandle mh, int pos, long value) {
        ValueNode node = kit.createLong(value);
        return MethodHandles.insertArguments(mh, pos, node);
    }

    private static MethodHandle foldArgumentsWithCombinerHelper(MethodHandle mh, int position, MethodHandle combiner, int... argPositions) {
        try {
            mh = (MethodHandle) foldArgumentsWithCombiner.invoke(null,
                            mh, position,
                            combiner,
                            argPositions);
            return mh;
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere(t);
        }
    }

    private static MethodHandle filterArgumentsWithCombinerHelper(MethodHandle mh, int position, MethodHandle combiner, int... argPositions) {
        try {
            mh = (MethodHandle) filterArgumentsWithCombiner.invoke(null,
                            mh, position,
                            combiner,
                            argPositions);
            return mh;
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere(t);
        }
    }
}
