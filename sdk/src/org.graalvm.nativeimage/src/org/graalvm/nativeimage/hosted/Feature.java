/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

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
 * @since 19.0
 */
@Platforms(Platform.HOSTED_ONLY.class)
public interface Feature {

    /**
     * Access methods that are available for all feature methods.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface FeatureAccess {

        /**
         * Returns a class if it is present on the classpath.
         *
         * @since 19.0
         */
        Class<?> findClassByName(String className);

        /**
         * Returns the class path of the native image that is currently built.
         * 
         * The returned list does not include the native image generator itself, and does not
         * include the JDK.
         * 
         * @since 20.2
         */
        List<Path> getApplicationClassPath();

        /**
         * Returns the module path of the native image that is currently built.
         *
         * The returned list does not include the native image generator itself, and does not
         * include the JDK.
         *
         * @since 20.2
         */
        List<Path> getApplicationModulePath();

        /**
         * Returns the {@link ClassLoader} that can find all classes of the class path and module
         * path.
         *
         * @since 20.2
         */
        ClassLoader getApplicationClassLoader();
    }

    /**
     * Access methods available for {@link Feature#isInConfiguration}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface IsInConfigurationAccess extends FeatureAccess {
    }

    /**
     * Access methods available for {@link Feature#afterRegistration}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterRegistrationAccess extends FeatureAccess {
    }

    /**
     * Access methods available for {@link Feature#duringSetup}.
     *
     * @since 19.0
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
         * @since 19.0
         */
        void registerObjectReplacer(Function<Object, Object> replacer);
    }

    /**
     * Access methods available for {@link Feature#beforeAnalysis}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface BeforeAnalysisAccess extends FeatureAccess {

        /**
         * Registers the provided type a used, i.e., metadata for the type is put into the native
         * image.
         *
         * @since 19.0
         */
        void registerAsUsed(Class<?> type);

        /**
         * Registers the provided type as instantiated, i.e., the static analysis assumes that
         * instances of this type exist at run time even if there is no explicit instantiation in
         * the bytecodes.
         * <p>
         * This implies that the type is also marked as {@link #registerAsUsed used}.
         *
         * @since 19.0
         */
        void registerAsInHeap(Class<?> type);

        /**
         * Registers the provided field as accesses, i.e., the static analysis assumes the field is
         * used even if there are no explicit reads or writes in the bytecodes.
         *
         * @since 19.0
         */
        void registerAsAccessed(Field field);

        /**
         * Registers the provided field as written or read by {@link sun.misc.Unsafe}, i.e., the
         * static analysis merges together all values of unsafe accessed fields of a specific type.
         * <p>
         * This implies that the field is also marked as {@link #registerAsAccessed accessed}.
         *
         * @since 19.0
         */
        void registerAsUnsafeAccessed(Field field);

        /**
         * Registers a callback that is invoked once {@link Feature#duringAnalysis during analysis}
         * when any of the provided elements is determined to be reachable at run time. The elements
         * can only be of the following types:
         * <p>
         * <ul>
         * <li>{@link Class} to specify reachability of the given class
         * <li>{@link Field} to specify reachability of a field
         * <li>{@link Executable} to specify reachability of a method or constructor
         * </ul>
         * <p>
         *
         * @since 19.2
         */
        void registerReachabilityHandler(Consumer<DuringAnalysisAccess> callback, Object... elements);

        /**
         * Registers a callback that is invoked once {@link Feature#duringAnalysis during analysis}
         * for each time a method that overrides the specified {param baseMethod} is determined to
         * be reachable at run time. In addition the handler will also get invoked once when the
         * {param baseMethod} itself becomes reachable. The specific method that becomes reachable
         * is passed to the handler as the second parameter.
         *
         * @since 19.3
         */
        void registerMethodOverrideReachabilityHandler(BiConsumer<DuringAnalysisAccess, Executable> callback, Executable baseMethod);

        /**
         * Registers a callback that is invoked once {@link Feature#duringAnalysis during analysis}
         * for each time a subtype of the class specified by {param baseClass} is determined to be
         * reachable at run time. In addition the handler will also get invoked once when the {param
         * baseClass} itself becomes reachable. The specific class that becomes reachable is passed
         * to the handler as the second parameter.
         *
         * @since 19.3
         */
        void registerSubtypeReachabilityHandler(BiConsumer<DuringAnalysisAccess, Class<?>> callback, Class<?> baseClass);
    }

    /**
     * Access methods available for {@link Feature#duringAnalysis}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface DuringAnalysisAccess extends BeforeAnalysisAccess, QueryReachabilityAccess {

        /**
         * Notifies the static analysis that changes are made that enforce a new iteration of the
         * analysis.
         *
         * @since 19.0
         */
        void requireAnalysisIteration();
    }

    /**
     * Access methods available for {@link Feature#afterAnalysis}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterAnalysisAccess extends QueryReachabilityAccess {
    }

    /**
     * Access reachability methods available for {@link Feature#afterAnalysis} and
     * {@link Feature#duringAnalysis}.
     *
     * @since 19.2
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface QueryReachabilityAccess extends FeatureAccess {
        /**
         * Returns true if the static analysis determined that the provided class is reachable at
         * run time.
         *
         * @since 19.2
         */
        boolean isReachable(Class<?> clazz);

        /**
         * Returns true if the static analysis determined that the provided field is reachable at
         * run time.
         *
         * @since 19.2
         */
        boolean isReachable(Field field);

        /**
         * Returns true if the static analysis determined that the provided method is reachable at
         * run time.
         *
         * @since 19.2
         */
        boolean isReachable(Executable method);

        /**
         * Returns all subtypes of the given {param baseClass} that the static analysis determined
         * to be reachable at run time (including the {param baseClass} itself).
         *
         * @since 19.3
         */
        Set<Class<?>> reachableSubtypes(Class<?> baseClass);

        /**
         * Returns all method overrides of the given {param baseMethod} that the static analysis
         * determined to be reachable at run time (including the {param baseMethod} itself).
         *
         * @since 19.3
         */
        Set<Executable> reachableMethodOverrides(Executable baseMethod);
    }

    /**
     * Access methods available for {@link Feature#onAnalysisExit}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface OnAnalysisExitAccess extends FeatureAccess {

    }

    /**
     * Access methods available for {@link Feature#beforeCompilation} and
     * {@link Feature#afterCompilation}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface CompilationAccess extends FeatureAccess {

        /**
         * Returns the field offset of the provided instance field.
         *
         * @since 19.0
         */
        long objectFieldOffset(Field field);

        /**
         * Hint to the native image generator that the given object is immutable at runtime, i.e.,
         * can be placed in a read-only section of the native image heap.
         *
         * @since 19.0
         */
        void registerAsImmutable(Object object);

        /**
         * Register the object, and everything it transitively references, as immutable. When the
         * provided predicate returns false for an object, the object is not marked as immutable and
         * the transitive iteration is stopped.
         *
         * @since 19.0
         */
        void registerAsImmutable(Object root, Predicate<Object> includeObject);
    }

    /**
     * Access methods available for {@link Feature#beforeCompilation}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface BeforeCompilationAccess extends CompilationAccess {
    }

    /**
     * Access methods available for {@link Feature#afterCompilation}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterCompilationAccess extends CompilationAccess {
    }

    /**
     * Access methods available for {@link Feature#afterHeapLayout}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterHeapLayoutAccess extends FeatureAccess {
    }

    /**
     * Access methods available for {@link Feature#beforeImageWrite}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface BeforeImageWriteAccess extends FeatureAccess {
    }

    /**
     * Access methods available for {@link Feature#afterImageWrite}.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterImageWriteAccess extends FeatureAccess {
        /**
         * Returns the path to the created native-image file (includes the native-image file name).
         *
         * @since 19.0
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
     * @since 19.0
     */
    default boolean isInConfiguration(IsInConfigurationAccess access) {
        return true;
    }

    /**
     * Returns the list of features that this feature depends on. As long as the dependency chain is
     * non-cyclic, all required features are processed before this feature.
     *
     * @since 19.0
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
     * @since 19.0
     */
    default void afterRegistration(AfterRegistrationAccess access) {
    }

    /**
     * Handler for initializations at startup time. It allows customization of the static analysis
     * setup.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 19.0
     */
    default void duringSetup(DuringSetupAccess access) {
    }

    /**
     * Handler for initializations before the static analysis.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 19.0
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
     * @since 19.0
     */
    default void duringAnalysis(DuringAnalysisAccess access) {
    }

    /**
     * Handler for initializations after analysis and before universe creation.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 19.0
     */
    default void afterAnalysis(AfterAnalysisAccess access) {
    }

    /**
     * Handler for code that needs to run after the analysis, even if an error has occurred, e.g.,
     * like reporting code.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 19.0
     */
    default void onAnalysisExit(OnAnalysisExitAccess access) {
    }

    /**
     * Handler for initializations before compilation.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 19.0
     */
    default void beforeCompilation(BeforeCompilationAccess access) {
    }

    /**
     * Handler for initializations after compilation, i.e., before the native image is written.
     *
     * @param access The supported operations that the feature can perform at this time
     *
     * @since 19.0
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
     * @since 19.0
     */
    default void afterHeapLayout(AfterHeapLayoutAccess access) {
    }

    /**
     * Handler for altering the linker command after the native image has been built and before it
     * is written.
     *
     * @param access The supported operations that the feature can perform at this time.
     *
     * @since 19.0
     */
    default void beforeImageWrite(BeforeImageWriteAccess access) {
    }

    /**
     * Handler for altering the image (or shared object) that the linker command produced.
     *
     * @param access The supported operations that the feature can perform at this time.
     *
     * @since 19.0
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
     * @since 19.0
     */
    default void cleanup() {
    }
}
