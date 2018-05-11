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
package org.graalvm.nativeimage;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Features allow clients to intercept the native image generation and run custom initialization
 * code at various stages. All code within feature classes is executed during native image
 * generation, and never at run time.
 * <p>
 * Features have several advantages over static class initializers (which also run during native
 * image generation):
 * <ul>
 * <li>The different feature methods are called at different stages during native image generation,
 * which gives clients control over when code is executed.
 * <li>Feature methods have an {@code access} parameter that allows callbacks into the native image
 * generator.
 * <li>Feature methods run when the {@link ImageSingletons} is already set up, which allows features
 * to prepare data structures that are then used at run time by querying the {@link ImageSingletons}
 * .
 * </ul>
 * <p>
 * Implementation classes must have a no-argument constructor, which is used to instantiate a
 * singleton instance for each feature using reflection. The following features are included during
 * native image generation:
 * <ul>
 * <li>Features explicitly specified on the command line.</li>
 * <li>Features referenced as {@link #getRequiredFeatures() required} by another included feature.
 * Required features are added transitively, and initialization methods of required features are
 * called after invoking the constructor and {@link #isInConfiguration} of the requiring features
 * (unless the feature dependencies are cyclic).
 * </ul>
 *
 * @since 1.0
 */
@Platforms(Platform.HOSTED_ONLY.class)
public interface Feature {

    /**
     * Access methods that are available for all feature methods.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface FeatureAccess {

        /**
         * Returns a class if it is present on the classpath.
         *
         * @since 1.0
         */
        Class<?> findClassByName(String className);
    }

    /**
     * Access methods available for {@link Feature#isInConfiguration}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface IsInConfigurationAccess extends FeatureAccess {
    }

    /**
     * Access methods available for {@link Feature#afterRegistration}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterRegistrationAccess extends FeatureAccess {
    }

    /**
     * Access methods available for {@link Feature#duringSetup}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface DuringSetupAccess extends FeatureAccess {

        /**
         * Registers the provided function to replace objects.
         * <p>
         * The function checks if an object should be replaced. In such a case, the function creates
         * the new object and returns it. The function must return the original object if the object
         * should not be replaced.
         *
         * @since 1.0
         */
        void registerObjectReplacer(Function<Object, Object> replacer);
    }

    /**
     * Access methods available for {@link Feature#beforeAnalysis}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface BeforeAnalysisAccess extends FeatureAccess {

        /**
         * Registers the provided type a used, i.e., metadata for the type is put into the native
         * image.
         *
         * @since 1.0
         */
        void registerAsUsed(Class<?> type);

        /**
         * Registers the provided type as instantiated, i.e., the static analysis assumes that
         * instances of this type exist at run time even if there is no explicit instantiation in
         * the bytecodes.
         * <p>
         * This implies that the type is also marked as {@link #registerAsUsed used}.
         *
         * @since 1.0
         */
        void registerAsInHeap(Class<?> type);

        /**
         * Registers the provided field as accesses, i.e., the static analysis assumes the field is
         * used even if there are no explicit reads or writes in the bytecodes.
         *
         * @since 1.0
         */
        void registerAsAccessed(Field field);

        /**
         * This method is now @Deprecated. Please use registerAsUnsafeAccessed instead.
         * 
         * Registers the provided field as written by {@link sun.misc.Unsafe}, i.e., the static
         * analysis merges together all values of unsafe accessed fields of a specific type.
         * <p>
         * This implies that the field is also marked as {@link #registerAsAccessed accessed}.
         *
         * @since 1.0
         */
        @Deprecated
        default void registerAsUnsafeWritten(Field field) {
            registerAsUnsafeAccessed(field);
        }

        /**
         * Registers the provided field as written or read by {@link sun.misc.Unsafe}, i.e., the
         * static analysis merges together all values of unsafe accessed fields of a specific type.
         * <p>
         * This implies that the field is also marked as {@link #registerAsAccessed accessed}.
         *
         * @since 1.0
         */
        void registerAsUnsafeAccessed(Field field);
    }

    /**
     * Access methods available for {@link Feature#duringAnalysis}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface DuringAnalysisAccess extends BeforeAnalysisAccess {

        /**
         * Notifies the static analysis that changes are made that enforce a new iteration of the
         * analysis.
         *
         * @since 1.0
         */
        void requireAnalysisIteration();
    }

    /**
     * Access methods available for {@link Feature#afterAnalysis}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterAnalysisAccess extends FeatureAccess {

    }

    /**
     * Access methods available for {@link Feature#onAnalysisExit}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface OnAnalysisExitAccess extends FeatureAccess {

    }

    /**
     * Access methods available for {@link Feature#beforeCompilation} and
     * {@link Feature#afterCompilation}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface CompilationAccess extends FeatureAccess {

        /**
         * Returns the field offset of the provided instance field.
         *
         * @since 1.0
         */
        long objectFieldOffset(Field field);

        /**
         * Hint to the native image generator that the given object is immutable at runtime, i.e.,
         * can be placed in a read-only section of the native image heap.
         *
         * @since 1.0
         */
        void registerAsImmutable(Object object);

        /**
         * Register the object, and everything it transitively references, as immutable. When the
         * provided predicate returns false for an object, the object is not marked as immutable and
         * the transitive iteration is stopped.
         *
         * @since 1.0
         */
        void registerAsImmutable(Object root, Predicate<Object> includeObject);
    }

    /**
     * Access methods available for {@link Feature#beforeCompilation}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface BeforeCompilationAccess extends CompilationAccess {
    }

    /**
     * Access methods available for {@link Feature#afterCompilation}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterCompilationAccess extends CompilationAccess {
    }

    /**
     * Access methods available for {@link Feature#afterHeapLayout}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterHeapLayoutAccess extends FeatureAccess {
    }

    /**
     * Access methods available for {@link Feature#beforeImageWrite}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface BeforeImageWriteAccess extends FeatureAccess {
    }

    /**
     * Access methods available for {@link Feature#afterImageWrite}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterImageWriteAccess extends FeatureAccess {
        /**
         * Returns the path to the created native-image file (includes the native-image file name).
         *
         * @since 1.0
         */
        Path getImagePath();
    }

    /**
     * This method is called immediately after the constructor, to check whether the feature is part
     * of the configuration or not. If this method returns false, the feature is not included in the
     * list of features and no other methods are called (in particular, the
     * {@link #getRequiredFeatures required features} are not processed).
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 1.0
     */
    default boolean isInConfiguration(IsInConfigurationAccess access) {
        return true;
    }

    /**
     * Returns the list of features that this feature depends on. As long as the dependency chain is
     * non-cyclic, all required features are processed before this feature.
     *
     * @since 1.0
     */
    default List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.emptyList();
    }

    /**
     * Handler for initializations after all features have been registered and all options have been
     * parsed; but before any initializations for the static analysis have happened.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 1.0
     */
    default void afterRegistration(AfterRegistrationAccess access) {
    }

    /**
     * Handler for initializations at startup time. It allows customization of the static analysis
     * setup.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 1.0
     */
    default void duringSetup(DuringSetupAccess access) {
    }

    /**
     * Handler for initializations before the static analysis.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 1.0
     */
    default void beforeAnalysis(BeforeAnalysisAccess access) {
    }

    /**
     * Handler for performing operations during the static analysis. This handler is called after
     * analysis is complete. So all analysis meta data is available. If the handler performs
     * changes, e.g., makes new types or methods reachable, it needs to call
     * {@link DuringAnalysisAccess#requireAnalysisIteration()}. This triggers a new iteration:
     * analysis is performed again and the handler is called again.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 1.0
     */
    default void duringAnalysis(DuringAnalysisAccess access) {
    }

    /**
     * Handler for initializations after analysis and before universe creation.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 1.0
     */
    default void afterAnalysis(AfterAnalysisAccess access) {
    }

    /**
     * Handler for code that needs to run after the analysis, even if an error has occured, e.g.,
     * like reporting code.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 1.0
     */
    default void onAnalysisExit(OnAnalysisExitAccess access) {
    }

    /**
     * Handler for initializations before compilation.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 1.0
     */
    default void beforeCompilation(BeforeCompilationAccess access) {
    }

    /**
     * Handler for initializations after compilation, i.e., before the native image is written.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 1.0
     */
    default void afterCompilation(AfterCompilationAccess access) {
    }

    /**
     * Handler for initializations after the native image heap and code layout. Objects and methods
     * have their offsets assigned. At this point, no additional objects must be added to the native
     * image heap, i.e., modifying object fields of native image objects that are part of the native
     * image heap is not allowed at this point.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 1.0
     */
    default void afterHeapLayout(AfterHeapLayoutAccess access) {
    }

    /**
     * Handler for altering the linker command after the native image has been built and before it
     * is written.
     *
     * @param access The supported operations that the feature can perform at this time.
     *
     * @since 1.0
     */
    default void beforeImageWrite(BeforeImageWriteAccess access) {
    }

    /**
     * Handler for altering the image (or shared object) that the linker command produced.
     *
     * @param access The supported operations that the feature can perform at this time.
     *
     * @since 1.0
     */
    default void afterImageWrite(AfterImageWriteAccess access) {
    }

    /**
     * Handler for cleanup. Can be used to cleanup static data. This can avoid memory leaks if
     * native image generation is done many times, e.g. during unit tests.
     * <p>
     * Usually, overriding this method can be avoided by putting a configuration object into the
     * {@link ImageSingletons}.
     *
     * @since 1.0
     */
    default void cleanup() {
    }
}
