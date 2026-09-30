/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine.CancellationCallback;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.auximage.AuxiliaryImage;
import com.oracle.svm.core.auximage.AuxiliaryImageBuilder;
import com.oracle.svm.core.auximage.AuxiliaryImageLoader;
import com.oracle.svm.core.auximage.AuxiliaryImageObjectReplacer;
import com.oracle.svm.core.auximage.AuxiliaryImagePersistenceCallback;
import com.oracle.svm.core.auximage.AuxiliaryImagePersistenceCancelledException;
import com.oracle.svm.core.auximage.MaximumAuxiliaryImageSizeExceededException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.runtime.AbstractEngineCacheSupport;
import com.oracle.truffle.runtime.EngineData;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.graal.compiler.debug.TTY;

/**
 * Implements engine cache support using auxiliary images.
 */
@Option.Group("engine")
public final class AuxiliaryEngineCacheSupport extends AbstractEngineCacheSupport {

    public enum UpdatePolicy {
        always,
        newcode,
        newroot,
        newsource,
        never
    }

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Loads and stores the cached engine from/to the configured path.")//
    public static final OptionKey<String> Cache = new OptionKey<>("");

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Loads the cached engine from the configured path.")//
    public static final OptionKey<String> CacheLoad = new OptionKey<>("");

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Stores the cached engine to the configured path automatically on engine close. ")//
    public static final OptionKey<String> CacheStore = new OptionKey<>("");

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "If set to true prepares the engine to use the Engine.storeCache(Path) method.")//
    public static final OptionKey<Boolean> CacheStoreEnabled = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Sets the maximum size for the stored image in bytes. " +
                    "If the value is set to a negative value then the size is unlimited. By default the size is unlimited. " +
                    "If the size is too big then no image is persisted and false will be returned by Engine.storeCache(...). Example value: '100MB'.", usageSyntax = "[0, inf)B|KB|MB|GB")//
    public static final OptionKey<Long> CacheStoreMaxImageSize = new OptionKey<>(-1L, SizeInBytesOptionType.POSITIVE_AND_NEGATIVE);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Preinitialize a new context with all languages that support it and that were used during the run.")//
    public static final OptionKey<Boolean> CachePreinitializeContext = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = COMPILE_HELP)//
    public static final OptionKey<CompilePolicy> CacheCompile = new OptionKey<>(CompilePolicy.hot);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = ""//
                    + "If true uses the last tier instead of the first tier compiler. By default the last tier compiler is used.")//
    public static final OptionKey<Boolean> CacheCompileUseLastTier = new OptionKey<>(Boolean.TRUE);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "" //
                    + "Specifies the policy when a stored cached engine should be written to file. "//
                    + "Possible values are 'always', 'newcode', 'newsource', 'newroot' or 'never'. Default update policy is 'always'.")//
    public static final OptionKey<UpdatePolicy> CacheUpdate = new OptionKey<>(UpdatePolicy.always);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Enable tracing of engine cache operations.")//
    public static final OptionKey<Boolean> TraceCache = new OptionKey<>(false);

    @Override
    public OptionDescriptors getEngineOptions() {
        return new AuxiliaryEngineCacheSupportOptionDescriptors();
    }

    @Override
    public int getPriority() {
        return 20;
    }

    /*
     * Engine currently being tried to load. Temporary storage until the PolyglotEngine is
     * initialized and verified.
     */
    private final ThreadLocal<EngineRoot> loadingEngine = new ThreadLocal<>();

    @Override
    public Object tryLoadingCachedEngine(OptionValues options, Function<String, TruffleLogger> loggerFactory) {
        if (!TruffleOptions.AOT) {
            return null;
        }
        Path loadPath = getLoadPath(options);
        if (loadPath == null) {
            traceLoad(options, loggerFactory, "No load engine cache configured.");
            return null;
        }
        if (!SubstrateOptions.TruffleStableOptions.AuxiliaryEngineCache.getValue()) {
            throw new IllegalArgumentException("Cache image cannot be loaded. Native image does not support auxilliary engine caching. Enable with -H:+AuxiliaryEngineCache at image build time.");
        }
        assert loadingEngine.get() == null;
        EngineRoot engine = tryLoadImpl(options, loggerFactory, loadPath);
        if (engine == null) {
            return null;
        }
        loadingEngine.set(engine);
        return engine.getPolyglotEngine();
    }

    private static Path getLoadPath(OptionValues options) {
        Path path;
        if (options.hasBeenSet(Cache)) {
            if (options.hasBeenSet(CacheLoad)) {
                throw new IllegalArgumentException("The options engine.Cache and engine.CacheLoad are mutually exclusive.");
            }
            path = Paths.get(options.get(Cache));
        } else if (options.hasBeenSet(CacheLoad)) {
            path = Paths.get(options.get(CacheLoad));
        } else {
            return null;
        }
        return path;
    }

    private static Path getStorePath(OptionValues options) {
        Path path;
        if (options.hasBeenSet(Cache)) {
            if (options.hasBeenSet(CacheStore)) {
                throw new IllegalArgumentException("The options engine.Cache and engine.CacheStore are mutually exclusive.");
            }
            path = Paths.get(options.get(Cache));
        } else if (options.hasBeenSet(CacheStore)) {
            path = Paths.get(options.get(CacheStore));
        } else {
            return null;
        }
        return path;
    }

    private EngineRoot tryLoadImpl(OptionValues options, Function<String, TruffleLogger> loggerFactory, Path imagePath) {
        if (AuxiliaryImageLoader.hasLoaded()) {
            throw new IllegalStateException("Auxiliary image memory is already in use. Only one image can be loaded per process.");
        }
        boolean trace = options.get(TraceCache);
        try {
            long time = 0;
            if (trace) {
                time = System.nanoTime();
                traceLoad(options, loggerFactory, "Try loading image '%s'...", imagePath);
            }

            if (!Files.exists(imagePath)) {
                traceLoad(options, loggerFactory, "Failed to load image. File does not exist.", imagePath);
                return null;
            }

            AuxiliaryImage image = AuxiliaryImageLoader.load(imagePath.toString());
            if (!image.contains(EngineRoot.class)) {
                traceLoad(options, loggerFactory, "Failed to load image. Image ignored.", imagePath);
                return null;
            }
            EngineRoot engine = image.lookup(EngineRoot.class);

            if (trace) {
                time = System.nanoTime() - time;
                traceLoad(options, loggerFactory, "Loaded image in %d ms. %,6d bytes %3d sources %3d roots", time / 1_000_000,
                                Files.size(imagePath), engine.getSources().length, engine.getCallTargets().length);
            }

            return engine;
        } catch (Throwable t) {
            if (trace) {
                traceLoad(options, loggerFactory, "Failed to load image from %s. Image ignored.", imagePath);
                t.printStackTrace(TTY.out);
            }
            return null;
        }
    }

    @Override
    public void onEngineCreated(EngineData e) {
    }

    @Override
    public void onEnginePatch(EngineData e) {
        if (!TruffleOptions.AOT) {
            return;
        }
        EngineRoot cachedEngine = loadingEngine.get();
        if (cachedEngine != null) {
            trace(e, "Engine from image successfully patched with new options.", cachedEngine);
            e.putEngineLocal(EngineRoot.class, cachedEngine);
            loadingEngine.set(null);
            e.mergeLoadedSources(cachedEngine.getSources());
        }
    }

    @Override
    protected OptionKey<Boolean> getTraceOption() {
        return TraceCache;
    }

    @Override
    public boolean isStoreEnabled(OptionValues options) {
        return TruffleOptions.AOT && (getStorePath(options) != null || options.get(CacheStoreEnabled));
    }

    @Override
    public boolean onStoreCache(EngineData e, Path path, long controlWord) {
        try {
            return performStore(e, path, Word.pointer(controlWord));
        } finally {
            restoreEngine(e);
        }
    }

    @Override
    public ByteBuffer persistCache(EngineData e, CancellationCallback callback) {
        try {
            return performPersist(e, callback);
        } finally {
            restoreEngine(e);
        }
    }

    @Override
    public boolean onEngineClosing(EngineData e) {
        if (!TruffleOptions.AOT) {
            return false;
        }
        /*
         * We either end up in onEngineCreate and use the loading engine or if the engine cannot be
         * created (e.g. invalid options) we directly end up in onEngineClosing on the same thread.
         */
        if (loadingEngine.get() != null) {
            loadingEngine.set(null);
        }

        Path imagePath = getStorePath(e.getEngineOptions());
        if (imagePath == null) {
            trace(e, "No store engine cache configured.");
            return false;
        }
        imagePath = imagePath.toAbsolutePath();

        try {
            performStore(e, imagePath, Word.nullPointer());
        } finally {
            restoreEngine(e);
        }

        // return false to indicate that the engine can now be safely closed
        return false;
    }

    private static final ComparableWord MEMORY_CODE_CONTINUE = Word.unsigned(0);

    private boolean performStore(EngineData e, Path imagePath, WordPointer cancelledWord) throws CancellationException {
        if (Files.exists(imagePath) && !Files.isWritable(imagePath)) {
            throw new IllegalArgumentException("Configured store image path is not writable: " + imagePath.toAbsolutePath());
        }

        boolean trace = e.getEngineOptions().get(getTraceOption());
        long time = 0;

        ByteBuffer buffer = prepareAndPersist(e, createControlWordCancellationPredicate(cancelledWord), cancelledWord, null, true);
        try {
            if (buffer != null) {
                if (trace) {
                    trace(e, "Writing image to %s...", imagePath);
                    time = System.nanoTime();
                }

                int size = writeImage(buffer, imagePath.toFile());

                if (trace) {
                    time = System.nanoTime() - time;
                    trace(e, "Finished writing %,5d bytes in %,d ms.", size, time / 1_000_000);
                }
                return true;
            } else {
                trace(e, "Skipped image write. ", imagePath);
                return false;
            }
        } catch (IOException e1) {
            throw new IllegalStateException("Failed writing image " + e1, e1);
        }
    }

    private ByteBuffer performPersist(EngineData e, CancellationCallback callback) throws CancellationException {
        AuxiliaryImagePersistenceCallback persistenceCallback = callback instanceof AuxiliaryImagePersistenceCallback auxiliaryCallback ? auxiliaryCallback : null;
        BooleanSupplier cancelledPredicate = callback == null ? null : callback::shouldCancel;
        return prepareAndPersist(e, cancelledPredicate, Word.nullPointer(), persistenceCallback, false);
    }

    private static BooleanSupplier createControlWordCancellationPredicate(WordPointer cancelledWord) {
        if (cancelledWord.isNull()) {
            return null;
        }
        long rawControl = cancelledWord.rawValue();
        return () -> {
            ComparableWord base = ((Pointer) Word.pointer(rawControl)).readWord(0);
            return base.notEqual(MEMORY_CODE_CONTINUE);
        };
    }

    private ByteBuffer prepareAndPersist(EngineData e, BooleanSupplier cancelledPredicate, WordPointer controlWord, AuxiliaryImagePersistenceCallback persistenceCallback, boolean detectChanges)
                    throws CancellationException {
        if (controlWord.isNonNull() && persistenceCallback != null) {
            throw new IllegalArgumentException("Control word and persistence callback are mutually exclusive.");
        }
        if (!SubstrateOptions.TruffleStableOptions.AuxiliaryEngineCache.getValue()) {
            throw new IllegalArgumentException("Cache image cannot be stored. Native image does not support auxilliary engine caching. Enable with -H:+AuxiliaryEngineCache at image build time.");
        }

        OptionValues options = e.getEngineOptions();
        final boolean trace = options.get(getTraceOption());
        long maxSize = options.get(CacheStoreMaxImageSize);
        if (maxSize == 0) {
            if (trace) {
                trace(e, "Skipped persisting engine because the maximum image size is 0 bytes.");
            }
            return null;
        }

        final CompilePolicy compileMode = options.get(CacheCompile);
        long time = 0;
        if (trace) {
            trace(e, "Preparing engine for store (compile policy %s)...", compileMode);
            time = System.nanoTime();
        }

        EngineRoot newEngine = new EngineRoot(prepareEngine(e, compileMode, options.get(CacheCompileUseLastTier), options.get(CachePreinitializeContext), cancelledPredicate));

        if (trace) {
            time = System.nanoTime() - time;
            trace(e, "Prepared engine in %,d ms.", time / 1_000_000);
        }

        EngineRoot previousEngine = e.getEngineLocal(EngineRoot.class);

        // clear used engine local for fresh load
        e.clearEngineLocal(EngineRoot.class);

        // we can't store the finalization result in the image as it stores non-serializable objects
        // so we clear it and restore it later
        FinalizationResult savedFinalizationResult = e.getEngineLocal(FinalizationResult.class);
        e.clearEngineLocal(FinalizationResult.class);
        try {
            return persistAndDetectChanges(e, previousEngine, newEngine, controlWord, persistenceCallback, detectChanges, maxSize);
        } finally {
            e.putEngineLocal(FinalizationResult.class, savedFinalizationResult);
        }
    }

    private ByteBuffer persistAndDetectChanges(EngineData e, EngineRoot previousEngine, EngineRoot newEngine, WordPointer controlWord,
                    AuxiliaryImagePersistenceCallback persistenceCallback, boolean detectChanges, long maxSize) throws CancellationException {
        boolean trace = e.getEngineOptions().get(TraceCache);
        long time = 0;
        if (trace) {
            trace(e, "Persisting engine for store ...");
            time = System.nanoTime();
        }

        AuxiliaryImageBuilder builder = new AuxiliaryImageBuilder();
        builder.add(EngineRoot.class, newEngine);
        SourceCollector sourceReplacer = new SourceCollector(newEngine.getSources());
        CallTargetCollector callTargetReplacer = new CallTargetCollector(newEngine.getCallTargets(), newEngine.getCallTargetState());
        builder.registerObjectReplacer(sourceReplacer);
        builder.registerObjectReplacer(callTargetReplacer);
        if (controlWord.isNonNull()) {
            builder.enableControlMemoryWord(controlWord);
        }
        if (persistenceCallback != null) {
            builder.enableCallback(persistenceCallback);
        }
        if (maxSize > 0) {
            builder.setMaximumAllowedAuxiliaryImageSize(maxSize);
        }

        /*
         * The engine lock is the only lock that is allowed to be held during persistence. We can
         * reset this one lock, every other lock is an error.
         */
        Object engineLock = e.getEngineLock();
        Object newLock = new Object();
        /*
         * The engine's 'logHandler' may contain a file descriptor reference, which is disallowed in
         * the image heap. It is essential to clean all references to the 'logHandler' instance in
         * the image heap. Both the engine's 'logHandler' and the 'engineLoggerSupplier' are
         * recreated during the engine patching process.
         */
        Object logHandler = e.getEngineLogHandler();
        builder.registerObjectReplacer(new AuxiliaryImageObjectReplacer() {
            @Override
            public Object replace(Object obj, Access access) {
                if (obj == engineLock) {
                    return newLock;
                } else if (obj == logHandler) {
                    return null;
                } else {
                    return obj;
                }
            }
        });

        ByteBuffer buffer;
        try {
            buffer = builder.persist();
        } catch (AuxiliaryImagePersistenceCancelledException t) {
            CancellationException c = new CancellationException("Engine persist was cancelled.");
            c.initCause(t);
            throw c;
        } catch (MaximumAuxiliaryImageSizeExceededException t) {
            if (trace) {
                trace(e, "Failed to persist engine to buffer. Image is bigger than the limit of " + maxSize + " bytes.");
            }
            return null; // return false in storeCache
        } catch (Throwable t) {
            if (trace) {
                trace(e, "Failed to persist engine to buffer.");
                trace(e, "" + t);
            }
            throw new IllegalStateException("Failed persisting image " + t, t);
        }

        if (trace) {
            time = System.nanoTime() - time;
            trace(e, "Persisted engine in %d ms.", time / 1_000_000);
        }

        if (!detectChanges) {
            return buffer;
        }

        UpdatePolicy updatePolicy = e.getEngineOptions().get(CacheUpdate);
        if (trace) {
            trace(e, "Detecting changes (update policy %s)...", updatePolicy.toString());
        }

        if (previousEngine != null) {
            trace(e, "    Previous image contains  %3d sources and %3d function roots.", previousEngine.getSources().length, previousEngine.getCallTargets().length);
        }
        trace(e, "    New image contains       %3d sources and %3d function roots.",
                        sourceReplacer.getFoundObjects().size(),
                        callTargetReplacer.getFoundObjects().size(),
                        e.getCallTargets().size());

        switch (updatePolicy) {
            case always:
                trace(e, "    Always persist policy. ");
                break;
            case newsource:
                if (previousEngine == null) {
                    if (trace) {
                        trace(e, "    New image detected -> always persist.");
                    }
                } else {
                    Source[] prevSources = previousEngine.getSources();
                    if (!hasNewObject(prevSources, sourceReplacer.getFoundObjects())) {
                        buffer = null;
                    }
                    if (trace) {
                        traceOldNew(e, "sources", prevSources, sourceReplacer.getFoundObjects(), buffer != null);
                    }
                }

                break;
            case newcode:
                if (previousEngine == null) {
                    if (trace) {
                        trace(e, "    New image detected -> always persist.");
                    }
                } else {
                    CallTarget[] prevCallTargets = previousEngine.getCallTargets();
                    boolean rootsChanged = hasNewObject(prevCallTargets, callTargetReplacer.getFoundObjects());
                    long previousCompilationCount = previousEngine.getCallTargetState().successfulCompilationCount;
                    long compilationCount = callTargetReplacer.getSuccessfulCompilationCount();
                    boolean compilationsChanged = previousCompilationCount != compilationCount;
                    int previousValidCallTargetCount = previousEngine.getCallTargetState().validCallTargetCount;
                    int validCallTargetCount = callTargetReplacer.getValidCallTargetCount();
                    boolean validCallTargetsChanged = !compilationsChanged && previousValidCallTargetCount != validCallTargetCount;
                    if (!rootsChanged && !compilationsChanged && !validCallTargetsChanged) {
                        buffer = null;
                    }
                    if (trace) {
                        traceOldNew(e, "function roots", prevCallTargets, callTargetReplacer.getFoundObjects(), rootsChanged);
                        trace(e, "    Successful compilation count: %d -> %d%s", previousCompilationCount, compilationCount, compilationsChanged ? " (changed)" : "");
                        trace(e, "    Valid compiled function roots: %d -> %d%s", previousValidCallTargetCount, validCallTargetCount,
                                        validCallTargetsChanged ? " (changed)" : "");
                    }
                }
                break;
            case newroot:
                if (previousEngine == null) {
                    if (trace) {
                        trace(e, "    New image detected -> always persist.");
                    }
                } else {
                    CallTarget[] prevCallTargets = previousEngine.getCallTargets();
                    if (!hasNewObject(prevCallTargets, callTargetReplacer.getFoundObjects())) {
                        buffer = null;
                    }
                    if (trace) {
                        traceOldNew(e, "function roots", prevCallTargets, callTargetReplacer.getFoundObjects(), buffer != null);
                    }
                }
                break;
            case never:
                buffer = null;
                trace(e, "    Never persist policy. Keep previous image.");
                break;
            default:
                // should not reach here
                throw new AssertionError();

        }
        return buffer;
    }

    private <T> void traceOldNew(EngineData e, String objectName, T[] previousObjects, Set<T> newObjects, boolean foundChanges) {
        if (foundChanges) {
            HashSet<T> set = new HashSet<>();
            set.addAll(newObjects);
            if (previousObjects != null) {
                for (T t : previousObjects) {
                    set.remove(t);
                }
            }
            trace(e, "    Changed %s detected:", objectName);
            set.stream().map((s) -> s.toString() + " 0x" + Integer.toHexString(System.identityHashCode(s))).sorted().forEach((s) -> {
                trace(e, "      + %s", s);
            });

            set.clear();
            if (previousObjects != null) {
                for (T t : previousObjects) {
                    set.add(t);
                }
            }
            for (T key : newObjects) {
                set.remove(key);
            }
            set.stream().map((s) -> s.toString() + " 0x" + Integer.toHexString(System.identityHashCode(s))).sorted().forEach((s) -> {
                trace(e, "      - %s", s);
            });
        } else {
            trace(e, "    No changed %s detected.", objectName);
        }
    }

    private static <T> boolean hasNewObject(T[] oldSources, Set<T> newSources) {
        if (oldSources == null) {
            return true;
        }
        if (oldSources.length != newSources.size()) {
            return true;
        }
        if (oldSources.length == 0) {
            return false;
        }
        return !newSources.containsAll(Arrays.asList(oldSources));
    }

    static int writeImage(ByteBuffer data, File file) throws IOException {
        Files.deleteIfExists(file.toPath());
        try (FileChannel ch = new FileOutputStream(file).getChannel()) {
            int written = 0;
            while (data.hasRemaining()) {
                written += ch.write(data);
            }
            return written;
        }
    }

    @Override
    public void onEngineClosed(EngineData e) {
        // unload auxiliary image, not yet supported atm.
    }

    private static final class EngineRoot {

        private final Object polyglotEngine;
        private final Source[] sources = new Source[0];
        private final CallTarget[] callTargets = new CallTarget[0];
        private final CallTargetState callTargetState = new CallTargetState(0, 0);

        EngineRoot(Object polyglotEngine) {
            this.polyglotEngine = polyglotEngine;
        }

        public Object getPolyglotEngine() {
            return polyglotEngine;
        }

        public Source[] getSources() {
            return sources;
        }

        public CallTarget[] getCallTargets() {
            return callTargets;
        }

        public CallTargetState getCallTargetState() {
            return callTargetState;
        }

        @Override
        public String toString() {
            return "CachedEngine[" + polyglotEngine + "]";
        }
    }

    static final class SourceCollector extends ObjectCollector<Source> {

        SourceCollector(Source[] targetArray) {
            super(targetArray);
        }

        @Override
        protected Source[] newArray(int length) {
            return new Source[length];
        }

        @Override
        protected boolean isInstance(Object o) {
            return o instanceof Source;
        }

    }

    static final class CallTargetCollector extends ObjectCollector<CallTarget> {

        private final CallTargetState targetState;
        private long successfulCompilationCount;
        private int validCallTargetCount;

        CallTargetCollector(CallTarget[] targetArray, CallTargetState targetState) {
            super(targetArray);
            this.targetState = targetState;
        }

        @Override
        protected CallTarget[] newArray(int length) {
            return new CallTarget[length];
        }

        @Override
        protected boolean isInstance(Object o) {
            return o instanceof OptimizedCallTarget;
        }

        @Override
        public void epilogue(EpilogueAccess access) {
            super.epilogue(access);
            for (CallTarget callTarget : getFoundObjects()) {
                OptimizedCallTarget optimizedCallTarget = (OptimizedCallTarget) callTarget;
                successfulCompilationCount += optimizedCallTarget.getSuccessfulCompilationCount();
                if (optimizedCallTarget.isValid()) {
                    validCallTargetCount++;
                }
            }
            access.replaceLate(targetState, new CallTargetState(successfulCompilationCount, validCallTargetCount));
        }

        long getSuccessfulCompilationCount() {
            return successfulCompilationCount;
        }

        int getValidCallTargetCount() {
            return validCallTargetCount;
        }

    }

    private static final class CallTargetState {

        final long successfulCompilationCount;
        final int validCallTargetCount;

        CallTargetState(long successfulCompilationCount, int validCallTargetCount) {
            this.successfulCompilationCount = successfulCompilationCount;
            this.validCallTargetCount = validCallTargetCount;
        }
    }

    abstract static class ObjectCollector<T> implements AuxiliaryImageObjectReplacer {

        private final T[] targetArray;
        private final Set<T> foundObjects = Collections.newSetFromMap(new IdentityHashMap<>());

        ObjectCollector(T[] targetArray) {
            this.targetArray = targetArray;
        }

        protected abstract boolean isInstance(Object o);

        @SuppressWarnings("unchecked")
        @Override
        public final Object replace(Object obj, Access access) {
            if (isInstance(obj)) {
                foundObjects.add((T) obj);
            }
            return obj;
        }

        protected abstract T[] newArray(int length);

        @Override
        @SuppressWarnings("unchecked")
        public void epilogue(EpilogueAccess access) {
            T[] newArray = foundObjects.toArray(newArray(foundObjects.size()));
            access.replaceLate(targetArray, newArray);
        }

        public final Set<T> getFoundObjects() {
            return foundObjects;
        }

    }
}
