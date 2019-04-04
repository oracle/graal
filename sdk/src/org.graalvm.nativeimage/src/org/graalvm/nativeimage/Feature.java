/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

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
 * @deprecated Replaced by {@link org.graalvm.nativeimage.hosted.Feature}.
 * @since 1.0
 */
@Platforms(Platform.HOSTED_ONLY.class)
@Deprecated
public interface Feature extends org.graalvm.nativeimage.hosted.Feature {

    /**
     * Access methods that are available for all feature methods.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface FeatureAccess extends org.graalvm.nativeimage.hosted.Feature.FeatureAccess {
    }

    /**
     * Access methods available for {@link Feature#isInConfiguration}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface IsInConfigurationAccess extends org.graalvm.nativeimage.hosted.Feature.IsInConfigurationAccess {
    }

    /**
     * Access methods available for {@link Feature#afterRegistration}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface AfterRegistrationAccess extends org.graalvm.nativeimage.hosted.Feature.AfterRegistrationAccess {
    }

    /**
     * Access methods available for {@link Feature#duringSetup}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface DuringSetupAccess extends org.graalvm.nativeimage.hosted.Feature.DuringSetupAccess {
    }

    /**
     * Access methods available for {@link Feature#beforeAnalysis}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface BeforeAnalysisAccess extends org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess {
    }

    /**
     * Access methods available for {@link Feature#duringAnalysis}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface DuringAnalysisAccess extends org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess {
    }

    /**
     * Access methods available for {@link Feature#afterAnalysis}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface AfterAnalysisAccess extends org.graalvm.nativeimage.hosted.Feature.AfterAnalysisAccess {

    }

    /**
     * Access methods available for {@link Feature#onAnalysisExit}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface OnAnalysisExitAccess extends org.graalvm.nativeimage.hosted.Feature.OnAnalysisExitAccess {

    }

    /**
     * Access methods available for {@link Feature#beforeCompilation} and
     * {@link Feature#afterCompilation}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface CompilationAccess extends org.graalvm.nativeimage.hosted.Feature.CompilationAccess {
    }

    /**
     * Access methods available for {@link Feature#beforeCompilation}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface BeforeCompilationAccess extends org.graalvm.nativeimage.hosted.Feature.BeforeCompilationAccess {
    }

    /**
     * Access methods available for {@link Feature#afterCompilation}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface AfterCompilationAccess extends org.graalvm.nativeimage.hosted.Feature.AfterCompilationAccess {
    }

    /**
     * Access methods available for {@link Feature#afterHeapLayout}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface AfterHeapLayoutAccess extends org.graalvm.nativeimage.hosted.Feature.AfterHeapLayoutAccess {
    }

    /**
     * Access methods available for {@link Feature#beforeImageWrite}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface BeforeImageWriteAccess extends org.graalvm.nativeimage.hosted.Feature.BeforeImageWriteAccess {
    }

    /**
     * Access methods available for {@link Feature#afterImageWrite}.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Deprecated
    interface AfterImageWriteAccess extends org.graalvm.nativeimage.hosted.Feature.AfterImageWriteAccess {
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

}
