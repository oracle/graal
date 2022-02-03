/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c.function;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointSetup.EnterIsolatePrologue;
import com.oracle.svm.core.c.function.CEntryPointSetup.EnterPrologue;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveEpilogue;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CEntryPointOptions {

    final class DefaultNameTransformation implements Function<String, String> {
        @Override
        public String apply(String name) {
            return SubstrateOptions.EntryPointNamePrefix.getValue() + name;
        }
    }

    final class UnchangedNameTransformation implements Function<String, String> {
        @Override
        public String apply(String name) {
            return name;
        }
    }

    /**
     * A function that is passed the name provided with {@link CEntryPoint#name()} and returns
     * another string that will be used for the entry point's symbol name. A common use is to add
     * prefixes or suffixes to entry point symbol names. When {@link CEntryPoint#name()} is not set,
     * the function is passed a string that contains the mangled class and method names.
     */
    Class<? extends Function<String, String>> nameTransformation() default DefaultNameTransformation.class;

    /**
     * If the supplier returns true, this entry point is added automatically when building a shared
     * library. This means the method is a root method for compilation, and everything reachable
     * from it is compiled too.
     * <p>
     * The provided class must have a nullary constructor, which is used to instantiate the class.
     * Then the supplier function is called on the newly instantiated instance.
     *
     * @deprecated Use {@link CEntryPoint#include()}.
     */
    @Deprecated
    Class<? extends BooleanSupplier> include() default CEntryPointOptions.AlwaysIncluded.class;

    /**
     * A {@link BooleanSupplier} that always returns {@code true}.
     *
     * @deprecated Use {@link org.graalvm.nativeimage.c.function.CEntryPoint.AlwaysIncluded}.
     */
    @Deprecated
    class AlwaysIncluded implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return true;
        }
    }

    /**
     * A {@link BooleanSupplier} that always returns {@code false}.
     *
     * @deprecated Use
     *             {@link org.graalvm.nativeimage.c.function.CEntryPoint.NotIncludedAutomatically}.
     */
    @Deprecated
    class NotIncludedAutomatically implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return false;
        }
    }

    /** Marker interface for all prologue classes. */
    interface Prologue {
    }

    /**
     * Special placeholder class for {@link #prologue()} for examining the entry point method's
     * signature and, in the case of an {@link IsolateThread} parameter, using {@link EnterPrologue}
     * or, in the case of an {@link Isolate} parameter, using {@link EnterIsolatePrologue}.
     */
    final class AutomaticPrologue implements Prologue {
    }

    /**
     * Special placeholder class for {@link #prologue()} to omit the prologue entirely. This value
     * is only permitted for entry point methods that are annotated with {@link Uninterruptible}.
     */
    final class NoPrologue implements Prologue {
    }

    /** Marker interface for all prologue bailout classes. */
    interface PrologueBailout {
    }

    /**
     * Special placeholder class for {@link #prologueBailout()}. If the prologue returns a non-zero
     * value, this class tries to convert the result of the prologue to a return value for the
     * bailout. The behavior depends on the return type that is expected by the method that is
     * annotated with {@link CEntryPointOptions}:
     *
     * <ul>
     * <li>int: return the non-zero result from the prologue as the bailout value.</li>
     * <li>void: bailout without a return value.</li>
     * <li>any other return type: throw a build-time error</li>
     * </ul>
     */
    final class AutomaticPrologueBailout implements PrologueBailout {
    }

    final class ReturnNullPointer implements PrologueBailout {
        @SuppressWarnings("unused")
        @Uninterruptible(reason = "Thread state not set up yet.")
        public static PointerBase bailout(int prologueResult) {
            return WordFactory.nullPointer();
        }
    }

    /**
     * Specifies a class with prologue code that is executed when the entry point method is called
     * from C in order to establish an execution context. See {@link AutomaticPrologue} for the
     * default behavior and {@link CEntryPointSetup} for commonly used prologues.
     * <p>
     * The given class must have exactly one static method with parameters that are a subsequence of
     * the entry point method's parameters. In other words, individual parameters may be omitted,
     * but must be in the same order. The entry point method's parameters are matched to the
     * prologue method's parameters. The prologue method must be {@link Uninterruptible} and its
     * return type must either be int or void.
     * <p>
     * If the return type is an integer, then a return value of zero indicates success. A non-zero
     * return value indicates a failure, and will result in early bailout. The value that is
     * returned by the early bailout can be customized by specifying a class for
     * {@link #prologueBailout}.
     * <p>
     * A void return type indicates that the prologue method directly handles errors (e.g., by
     * calling {@link CEntryPointActions#failFatally}), so no extra bailout-related code will be
     * generated.
     */
    Class<? extends Prologue> prologue() default AutomaticPrologue.class;

    /**
     * Specifies a class that is used when an early bailout occurs in the prologue. The given class
     * must have exactly one static {@link Uninterruptible} method with an int argument. This method
     * is invoked if the prologue methods returns a non-zero value.
     */
    Class<? extends PrologueBailout> prologueBailout() default AutomaticPrologueBailout.class;

    /** Marker interface for all epilogue classes. */
    interface Epilogue {
    }

    /**
     * Special placeholder class for {@link #epilogue()} to omit the epilogue entirely. This value
     * is only permitted for entry point methods that are annotated with {@link Uninterruptible}.
     */
    final class NoEpilogue implements Epilogue {
    }

    /**
     * Specifies a class with epilogue code that is executed when the entry point method returns to
     * C in order to leave the execution context. See {@link CEntryPointSetup} for commonly used
     * epilogues.
     * <p>
     * The given class must have exactly one static {@link Uninterruptible} method with no
     * parameters. Within the epilogue method, {@link CEntryPointActions} can be used to leave the
     * execution context.
     */
    Class<? extends Epilogue> epilogue() default LeaveEpilogue.class;

    enum Publish {
        /**
         * Do not publish the entry point method.
         */
        NotPublished,
        /**
         * Create a symbol for the entry point method in the native image.
         */
        SymbolOnly,
        /**
         * Create a symbol for the entry point method in the native image, and if building a shared
         * library image, also include a C declaration in the generated C header file.
         */
        SymbolAndHeader,
    }

    Publish publishAs() default Publish.SymbolAndHeader;
}
