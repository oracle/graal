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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import jdk.graal.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.sboutlining.concat.SubstrateStringConcatHelper;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;

/**
 * Build-time generator for graphs that concatenate values into a {@link String}.
 *
 * <p>
 * The implementation is derived from the method-handle inline-copy strategy in the JDK
 * {@code StringConcatFactory}. It parses a concat recipe and assembles a combinator pipeline that
 * stringifies operands, mixes their lengths and coders, allocates the result buffer, writes values
 * in reverse order, and creates the final string.
 *
 * <p>
 * Unlike the JDK factory, the method handles produced here operate on graph {@link ValueNode}s.
 * {@link SubstrateStringConcatGraphBuilder} translates each combinator into operations on a
 * {@link HostedGraphKit}, while {@link SubstrateStringConcatHelper} provides the runtime operations
 * called by the generated graph. Invoking the completed handle therefore builds an
 * {@link com.oracle.svm.hosted.sboutlining.OutlinedSBMethod}; it does not concatenate
 * runtime values through method handles.
 *
 * <p>
 * Recipe constants are exposed by {@link #parseRecipe} so the invokedynamic plugin can turn them
 * into regular arguments and maximize sharing between outlined method shapes.
 */

// Suppressing formatters to keep the source-derived code close to the upstream layout
/*
 * Checkstyle: stop
 * @formatter:off
 */
@SuppressWarnings("all")
@Platforms(Platform.HOSTED_ONLY.class)
public class SubstrateStringConcatFactory {

    /**
     * Tag used to demarcate an ordinary argument.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L138")
    private static final char TAG_ARG = '\u0001';

    /**
     * Tag used to demarcate a constant.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L143")
    private static final char TAG_CONST = '\u0002';

    /**
     * Equivalent to
     * {@link java.lang.invoke.StringConcatFactory#makeConcat(MethodHandles.Lookup, String, MethodType)}.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L231-L240")
    public MethodHandle makeConcat(MethodType concatType) throws StringConcatException {
        // This bootstrap method is unlikely to be used in practice,
        // avoid optimizing it at the expense of makeConcatWithConstants

        // Mock the recipe to reuse the concat generator code
        String recipe = "\u0001".repeat(concatType.parameterCount());
        return makeConcatWithConstants(concatType, recipe);
    }

    /**
     * Equivalent to
     * {@link java.lang.invoke.StringConcatFactory#makeConcatWithConstants(MethodHandles.Lookup, String, MethodType, String, Object...)}.
     * Made private because we only use {@link #makeConcat(MethodType)}.
     */
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

        List<String> elements = parseRecipe(concatType, recipe, constants);

        if (!concatType.returnType().isAssignableFrom(String.class)) {
            throw new StringConcatException(
                    "The return type should be compatible with String, but it is " +
                            concatType.returnType());
        }

        assert elements.stream().allMatch(e -> e == null) : "the recipe should be only TAG_ARGs";

        try {
            return generateMHInlineCopy(concatType, elements);
        } catch (Error e) {
            // Pass through any error
            throw e;
        } catch (Throwable t) {
            throw new StringConcatException("Generator failed", t);
        }
    }

    /**
     * Create an array of String values to be concatenated together. While doing this, it also
     * extracts any constants directly within the recipe and passed within {@code constants}. We
     * expose this publicly so to help convert a
     * {@link #makeConcatWithConstants(MethodType, String, Object...)} call into a
     * {@link #makeConcat(MethodType)}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L410-L468")
    public static List<String> parseRecipe(MethodType concatType,
                    String recipe,
                    Object[] constants)
                    throws StringConcatException {

        Objects.requireNonNull(recipe, "Recipe is null");
        // Element list containing String constants, or null for arguments
        List<String> elements = new ArrayList<>();

        int cCount = 0;
        int oCount = 0;

        StringBuilder acc = new StringBuilder();

        for (int i = 0; i < recipe.length(); i++) {
            char c = recipe.charAt(i);

            if (c == TAG_CONST) {
                if (cCount == constants.length) {
                    // Not enough constants
                    throw constantMismatch(constants, cCount);
                }
                // Accumulate constant args along with any constants encoded
                // into the recipe
                acc.append(constants[cCount++]);
            } else if (c == TAG_ARG) {
                // Flush any accumulated characters into a constant
                if (acc.length() > 0) {
                    elements.add(acc.toString());
                    acc.setLength(0);
                }
                elements.add(null);
                oCount++;
            } else {
                // Not a special character, this is a constant embedded into
                // the recipe itself.
                acc.append(c);
            }
        }

        // Flush the remaining characters as constant:
        if (acc.length() > 0) {
            elements.add(acc.toString());
        }
        if (oCount != concatType.parameterCount()) {
            throw argumentMismatch(concatType, oCount);
        }
        if (cCount < constants.length) {
            throw constantMismatch(constants, cCount);
        }
        return elements;
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L470-L477")
    private static StringConcatException argumentMismatch(MethodType concatType,
                                                          int oCount) {
        return new StringConcatException(
                "Mismatched number of concat arguments: recipe wants " +
                        oCount +
                        " arguments, but signature provides " +
                        concatType.parameterCount());
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L479-L487")
    private static StringConcatException constantMismatch(Object[] constants,
                                                          int cCount) {
        return new StringConcatException(
                "Mismatched number of concat constants: recipe wants " +
                        cCount +
                        " constants, but only " +
                        constants.length +
                        " are passed");
    }

    /**
     * <p>This strategy replicates what StringBuilders are doing: it builds the
     * byte[] array on its own and passes that byte[] array to String
     * constructor. This strategy requires access to some private APIs in JDK,
     * most notably, the private String constructor that accepts byte[] arrays
     * without copying.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L489-L517")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L526-L646")
    private MethodHandle generateMHInlineCopy(MethodType mt, List<String> elements) {

        // Fast-path unary concatenations
        if (elements.size() == 1) {
            String s0 = elements.get(0);
            if (s0 == null) {
                return unaryConcat(mt.parameterType(0));
            } else {
                return insertArgumentsHelper(unaryConcat(Object.class), 0, s0);
            }
        }

        // Fast-path binary concatenations
        if (elements.size() == 2) {
            // Two arguments
            String s0 = elements.get(0);
            String s1 = elements.get(1);

            if (mt.parameterCount() == 2 &&
                    !mt.parameterType(0).isPrimitive() &&
                    !mt.parameterType(1).isPrimitive() &&
                    s0 == null &&
                    s1 == null) {
                return simpleConcat();
            } else if (mt.parameterCount() == 1) {
                // One argument, one constant
                String constant;
                int constIdx;
                if (s1 == null) {
                    constant = s0;
                    constIdx = 0;
                } else {
                    constant = s1;
                    constIdx = 1;
                }
                if (constant.isEmpty()) {
                    return unaryConcat(mt.parameterType(0));
                } else if (!mt.parameterType(0).isPrimitive()) {
                    // Non-primitive argument
                    return insertArgumentsHelper(simpleConcat(), constIdx, constant);
                }
            }
            // else... fall-through to slow-path
        }

        // Create filters and obtain filtered parameter types. Filters would be used in the beginning
        // to convert the incoming arguments into the arguments we can process (e.g. Objects -> Strings).
        // The filtered argument type list is used all over in the combinators below.

        Class<?>[] ptypes = mt.erase().parameterArray();
        MethodHandle[] filters = null;
        for (int i = 0; i < ptypes.length; i++) {
            Class<?> cl = ptypes[i];
            // Use int as the logical type for byte and short. Keep the original logical type for
            // char and boolean because they require special handling.
            ptypes[i] = promoteToIntType(ptypes[i]);
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

        // Start building the combinator tree. The tree "starts" with (<parameters>)String, and "finishes"
        // with the (byte[], long)String shape to invoke newString in StringConcatHelper. The combinators are
        // assembled bottom-up, which makes the code arguably hard to read.

        // Drop all remaining parameter types, leave only helper arguments:
        MethodHandle mh = MethodHandles.dropArguments(newString(), 2, pValues);

        long initialLengthCoder = INITIAL_CODER;

        // Mix in prependers. This happens when (byte[], long) = (storage, indexCoder) is already
        // known from the combinators below. We are assembling the string backwards, so the index coded
        // into indexCoder is the *ending* index.

        // We need one prepender per argument, but also need to fold in constants. We do so by greedily
        // create prependers that fold in surrounding constants into the argument prepender. This reduces
        // the number of unique MH combinator tree shapes we'll create in an application.
        String constant = null;
        int pos = 0;
        for (String el : elements) {
            // Do the prepend, and put "new" index at index 1
            if (el != null) {
                // Constant element

                // Eagerly update the initialLengthCoder value
                initialLengthCoder = SubstrateStringConcatHelper.mix(initialLengthCoder, el);

                // Save the constant and fold it either into the next
                // argument prepender, or into the newArray combinator
                assert (constant == null);
                constant = el;
            } else {
                // Add prepender, along with any prefix constant
                mh = filterArgumentsWithCombinerHelper(
                        mh, 1,
                        prepender(constant, ptypes[pos]),
                        1, 0, // indexCoder, storage
                        2 + pos  // selected argument
                );
                constant = null;
                pos++;
            }
        }

        // Fold in byte[] instantiation at argument 0
        MethodHandle newArrayCombinator;
        if (constant != null) {
            // newArray variant that deals with prepending the trailing constant
            //
            // initialLengthCoder has been adjusted to have the correct coder
            // and length already, but to avoid binding an extra variable to
            // the method handle we now adjust the length to be correct for the
            // first prepender above, while adjusting for the missing length of
            // the constant in StringConcatHelper
            initialLengthCoder -= constant.length();
            newArrayCombinator = newArrayWithSuffix(constant);
        } else {
            newArrayCombinator = newArray();
        }
        mh = foldArgumentsWithCombinerHelper(mh, 0, newArrayCombinator,
                1 // index
        );

        // Start combining length and coder mixers.
        //
        // Length is easy: constant lengths can be computed on the spot, and all non-constant
        // shapes have been either converted to Strings, or explicit methods for getting the
        // string length out of primitives are provided.
        //
        // Coders are more interesting. Only Object, String and char arguments (and constants)
        // can have non-Latin1 encoding. It is easier to blindly convert constants to String,
        // and deduce the coder from there. Arguments would be either converted to Strings
        // during the initial filtering, or handled by specializations in MIXERS.
        //
        // The method handle shape before all mixers are combined in is:
        // (long, <args>)String = ("indexCoder", <args>)
        //
        // We will bind the initialLengthCoder value to the last mixer (the one that will be
        // executed first), then fold that in. This leaves the shape after all mixers are
        // combined in as:
        // (<args>)String = (<args>)
        pos = -1;
        MethodHandle mix = null;
        for (String el : elements) {
            // Constants already handled in the code above
            if (el == null) {
                if (pos >= 0) {
                    // Compute new "index" in-place using old value plus the appropriate argument.
                    mh = filterArgumentsWithCombinerHelper(mh, 0, mix,
                            0, // old-index
                            1 + pos // selected argument
                    );
                }

                Class<?> argClass = ptypes[++pos];
                mix = mixer(argClass);
            }
        }

        // Insert the initialLengthCoder value into the final mixer, then
        // fold that into the base method handle
        if (pos >= 0) {
            mix = insertArgumentsHelper(mix, 0, initialLengthCoder);
            mh = foldArgumentsWithCombinerHelper(mh, 0, mix,
                    1 + pos // selected argument
            );
        } else {
            // No mixer (constants only concat), insert initialLengthCoder directly
            mh = insertArgumentsHelper(mh, 0, initialLengthCoder);
        }

        // The method handle shape here is (<args>).

        // Apply filters, converting the arguments:
        if (filters != null) {
            mh = MethodHandles.filterArguments(mh, 0, filters);
        }

        return mh;
    }

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
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L754-L762")
    private final Function<Class<?>, MethodHandle> PREPEND = new Function<>() {
        @Override
        public MethodHandle apply(Class<?> c) {
            MethodHandle prepend = SubstrateStringConcatGraphBuilder.getPrependForKind(c);
            return prepend.bindTo(kit);
        }
    };

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L765-L772")
    private final Function<Class<?>, MethodHandle> NULL_PREPEND = new Function<>() {
        @Override
        public MethodHandle apply(Class<?> c) {
            /* JDK 25 prepend methods use an empty string to represent a missing prefix. */
            return insertArgumentsHelper(
                    PREPENDERS.computeIfAbsent(c, PREPEND), 3, "");
        }
    };

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L860-L868")
    private final Function<Class<?>, MethodHandle> MIX = new Function<>() {
        @Override
        public MethodHandle apply(Class<?> c) {
            MethodHandle mix = SubstrateStringConcatGraphBuilder.getMixForKind(c);
            return mix.bindTo(kit);
        }
    };

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L898-L907")
    private MethodHandle simpleConcat() {
        return SubstrateStringConcatGraphBuilder.simpleConcatMH.bindTo(kit);
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L909-L918")
    private MethodHandle newString() {
        return SubstrateStringConcatGraphBuilder.newStringMH.bindTo(kit);
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L920-L929")
    private MethodHandle newArrayWithSuffix(String suffix) {
        MethodHandle mh = SubstrateStringConcatGraphBuilder.newArrayWithSuffixMH.bindTo(kit);
        return insertArgumentsHelper(mh, 0, suffix);
    }

    private MethodHandle newArray() {
        return SubstrateStringConcatGraphBuilder.newArrayMH.bindTo(kit);
    }

    /**
     * Public gateways to public "stringify" methods. These methods have the
     * form String apply(T obj), and normally delegate to {@code String.valueOf},
     * depending on argument's type.
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

    private MethodHandle INT_STRINGIFIER;

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L963-L969")
    private MethodHandle intStringifier() {
        MethodHandle mh = INT_STRINGIFIER;
        if (mh == null) {
            INT_STRINGIFIER = mh = SubstrateStringConcatGraphBuilder.intStringifierMH.bindTo(kit);
        }
        return mh;
    }

    private MethodHandle LONG_STRINGIFIER;

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L972-L978")
    private MethodHandle longStringifier() {
        MethodHandle mh = LONG_STRINGIFIER;
        if (mh == null) {
            LONG_STRINGIFIER = mh = SubstrateStringConcatGraphBuilder.longStringifierMH.bindTo(kit);
        }
        return mh;
    }

    private MethodHandle CHAR_STRINGIFIER;

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L981-L987")
    private MethodHandle charStringifier() {
        MethodHandle mh = CHAR_STRINGIFIER;
        if (mh == null) {
            CHAR_STRINGIFIER = mh = SubstrateStringConcatGraphBuilder.charStringifierMH.bindTo(kit);
        }
        return mh;
    }

    private MethodHandle BOOLEAN_STRINGIFIER;

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L990-L996")
    private MethodHandle booleanStringifier() {
        MethodHandle mh = BOOLEAN_STRINGIFIER;
        if (mh == null) {
            BOOLEAN_STRINGIFIER = mh = SubstrateStringConcatGraphBuilder.booleanStringifierMH.bindTo(kit);
        }
        return mh;
    }

    private MethodHandle NEW_STRINGIFIER;

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L999-L1006")
    private MethodHandle newStringifier() {
        MethodHandle mh = NEW_STRINGIFIER;
        if (mh == null) {
            NEW_STRINGIFIER = mh = SubstrateStringConcatGraphBuilder.newStringifierMH.bindTo(kit);
        }
        return mh;
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L1008-L1026")
    private MethodHandle unaryConcat(Class<?> cl) {
        if (!cl.isPrimitive()) {
            return newStringifier();
        } else if (cl == int.class || cl == short.class || cl == byte.class) {
            return intStringifier();
        } else if (cl == long.class) {
            return longStringifier();
        } else if (cl == char.class) {
            return charStringifier();
        } else if (cl == boolean.class) {
            return booleanStringifier();
        } else if (cl == float.class) {
            return floatStringifier();
        } else if (cl == double.class) {
            return doubleStringifier();
        } else {
            throw new InternalError("Unhandled type for unary concatenation: " + cl);
        }
    }

    private final HostedGraphKit kit;

    private static final Method foldArgumentsWithCombiner= ReflectionUtil.lookupMethod(MethodHandles.class, "foldArgumentsWithCombiner", MethodHandle.class, int.class, MethodHandle.class, int[].class);
    private static final Method filterArgumentsWithCombiner = ReflectionUtil.lookupMethod(MethodHandles.class, "filterArgumentsWithCombiner", MethodHandle.class, int.class, MethodHandle.class, int[].class);

    private final Map<Class<?>, MethodHandle> PREPENDERS;
    private final Map<Class<?>, MethodHandle> NULL_PREPENDERS;
    private final Map<Class<?>, MethodHandle> MIXERS;
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L1031")
    private static final long INITIAL_CODER= SubstrateStringConcatHelper.initialCoder();

    /**
     * Uses {@code int} as the logical type for {@code byte} and {@code short}.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/invoke/StringConcatFactory.java#L1033-L1040")
    static Class<?> promoteToIntType(Class<?> t) {
        // Keep special mixers and prependers for char and boolean.
        return t == byte.class || t == short.class ? int.class : t;
    }

    /**
     * Returns a stringifier for references and floats/doubles only.
     * Always returns null for other primitives.
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

    public SubstrateStringConcatFactory(HostedGraphKit kit) {
        this.kit = kit;

        PREPENDERS = new HashMap<>();
        NULL_PREPENDERS = new HashMap<>();
        MIXERS = new HashMap<>();
    }

    /*
     * @formatter:on
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
