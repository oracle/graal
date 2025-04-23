/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.shadowed.com.ibm.icu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleReader;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.graalvm.shadowed.com.ibm.icu.impl.ICUData;
import org.graalvm.shadowed.org.tukaani.xz.FinishableWrapperOutputStream;
import org.graalvm.shadowed.org.tukaani.xz.LZMA2Options;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

public class ICU4JFeature implements Feature {

    /***
     * Only compress larger than this number of bytes. We want to avoid compressing tiny files:
     * simply not worth any overhead for the negligible potential savings.
     */
    private static final int MIN_SIZE_TO_COMPRESS = 512;
    /**
     * Only compress if compressedSize / uncompressedSize <= {@link #GOOD_ENOUGH_COMPRESSION_RATIO}.
     */
    private static final double GOOD_ENOUGH_COMPRESSION_RATIO = 0.9;
    /**
     * Use a smaller-than-default dictionary size. Results in lower decoder memory consumption at
     * the cost of slightly worse compression ratio.
     */
    static final int DICT_SIZE = 64 * 1024;

    static final int MAGIC = 0xAB02;

    @Platforms(Platform.HOSTED_ONLY.class)
    private record ResourceEntry(List<String> classNames, List<String> resourcePaths) {
        ResourceEntry(List<String> classNames, String resourcePath) {
            this(classNames, List.of(resourcePath));
        }

        ResourceEntry(String className, String resourcePath) {
            this(List.of(className), List.of(resourcePath));
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        List<ResourceEntry> resourcePatterns = List.of(
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.util.UResourceBundle",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/[^/]*\\.(?:res|lst)$"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.charset.CharsetMBCS",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/.*\\.cnv$"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.UCaseProps",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/ucase\\.icu$"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.UCharacterName",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/unames\\.icu$"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.UCharacterProperty",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/uprops\\.icu$"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.UCharacterProperty$LayoutProps",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/ulayout\\.icu$"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.UPropertyAliases",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/pnames\\.icu$"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.EmojiProps",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/uemoji\\.icu$"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.UBiDiProps",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/ubidi\\.icu$"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.coll.CollationRoot",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/coll/ucadata\\.icu$"),
                        new ResourceEntry(List.of(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.coll.CollationLoader",
                                        "org.graalvm.shadowed.com.ibm.icu.text.Collator"),
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/coll/[^/]*\\.res$"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.charset.UConverterAlias",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/cnvalias\\.icu$"),
                        new ResourceEntry(List.of(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.TimeZoneNamesImpl",
                                        "org.graalvm.shadowed.com.ibm.icu.impl.TimeZoneGenericNames",
                                        "org.graalvm.shadowed.com.ibm.icu.text.TimeZoneFormat"),
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/zone/.*"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.ICURegionDataTables",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/region/.*"),
                        new ResourceEntry(List.of(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.ICULangDataTables",
                                        "org.graalvm.shadowed.com.ibm.icu.util.LocaleData"),
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/lang/.*"),
                        new ResourceEntry(List.of(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.ICUCurrencyDisplayInfoProvider",
                                        "org.graalvm.shadowed.com.ibm.icu.impl.ICUCurrencyMetaInfo"),
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/curr/.*"),
                        new ResourceEntry(List.of(
                                        "org.graalvm.shadowed.com.ibm.icu.util.MeasureUnit",
                                        "org.graalvm.shadowed.com.ibm.icu.text.MeasureFormat",
                                        "org.graalvm.shadowed.com.ibm.icu.text.TimeUnitFormat",
                                        "org.graalvm.shadowed.com.ibm.icu.impl.number.LongNameHandler"),
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/unit/.*"),
                        new ResourceEntry(List.of(
                                        "org.graalvm.shadowed.com.ibm.icu.text.TransliteratorRegistry",
                                        "org.graalvm.shadowed.com.ibm.icu.text.Transliterator"),
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/translit/.*"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.text.RuleBasedNumberFormat",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/rbnf/.*"),
                        new ResourceEntry(List.of(
                                        "org.graalvm.shadowed.com.ibm.icu.text.BreakIteratorFactory",
                                        "org.graalvm.shadowed.com.ibm.icu.impl.breakiter.DictionaryBreakEngine",
                                        "org.graalvm.shadowed.com.ibm.icu.impl.SimpleFilteredSentenceBreakIterator$Builder"),
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/brkitr/.*"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.text.SpoofChecker$SpoofData$DefaultData",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/confusables\\.cfu"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.text.StringPrep",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/[^/]*\\.spp"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.Normalizer2Impl",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/data/icudata/[^/]*\\.nrm"),
                        new ResourceEntry(
                                        "org.graalvm.shadowed.com.ibm.icu.impl.duration.impl.ResourceBasedPeriodFormatterDataService",
                                        "org/graalvm/shadowed/com/ibm/icu/impl/duration/impl/data/.*"));

        for (var e : resourcePatterns) {
            access.registerReachabilityHandler(acc -> addCompressedResources(e.resourcePaths()),
                            e.classNames().stream().map(cn -> Objects.requireNonNull(access.findClassByName(cn), cn)).toArray());
        }
    }

    private void addCompressedResources(List<String> patterns) {
        Class<?> refClass = ICUData.class;
        Module module = refClass.getModule();
        ModuleLayer layer = module.getLayer();
        try {
            if (layer != null) {
                // named module, i.e. module-path dependency
                Optional<ResolvedModule> resolvedModule = layer.configuration().findModule(module.getName());
                try (ModuleReader reader = resolvedModule.get().reference().open()) {
                    Stream<String> entryStream = reader.list();
                    addCompressedResources(patterns, module, entryStream, name -> reader.open(name).get());
                }
            } else {
                // unnamed module, i.e. class-path dependency
                URL urlClassPath = refClass.getClassLoader().getResource(refClass.getName().replace('.', '/').concat(".class"));
                if (urlClassPath != null && urlClassPath.getProtocol().equals("jar")) {
                    String jarFilePath = URI.create(urlClassPath.getPath()).getPath();
                    int separatorIndex = jarFilePath.indexOf("!/");
                    if (separatorIndex != -1) {
                        jarFilePath = jarFilePath.substring(0, separatorIndex);
                        try (JarFile jf = new JarFile(jarFilePath)) {
                            Stream<String> entryStream = jf.stream().map(JarEntry::getName);
                            addCompressedResources(patterns, module, entryStream, name -> jf.getInputStream(jf.getJarEntry(name)));
                        }
                    } else {
                        throw new IllegalArgumentException(jarFilePath);
                    }
                } else {
                    throw new IllegalArgumentException(
                                    "ICU4J module needs to be to either be on the module path or in a jar file on the native image builder class path." +
                                                    " Found URL: " + urlClassPath);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addCompressedResources(List<String> patterns, Module module, Stream<String> entryStream, ThrowingFunction<String, InputStream, IOException> openResource) throws IOException {
        byte[] magic = new byte[]{(byte) ICU4JFeature.MAGIC, (byte) (ICU4JFeature.MAGIC >>> 8)};
        List<Pattern> compiledPatterns = patterns.stream().map(Pattern::compile).toList();
        Stream<String> filteredEntries = entryStream.filter(s -> !s.endsWith("/") && !s.endsWith(".class"));
        for (Iterator<String> iterator = filteredEntries.iterator(); iterator.hasNext();) {
            String name = iterator.next();
            if (compiledPatterns.stream().anyMatch(pattern -> pattern.matcher(name).matches())) {
                ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
                byte[] data = null;
                // Avoid compressing tiny files, not worth it.
                int keepUncompressedThreshold = MIN_SIZE_TO_COMPRESS;
                do {
                    try (InputStream resourceInput = openResource.apply(name)) {
                        int uncompressedSize = resourceInput.available();
                        if (uncompressedSize <= keepUncompressedThreshold) {
                            data = resourceInput.readAllBytes();
                            if (data.length >= magic.length && Arrays.equals(data, 0, magic.length, magic, 0, magic.length)) {
                                throw new IllegalStateException("Uncompressed data must not start with compressed header bytes");
                            }
                        } else {
                            int preset = LZMA2Options.PRESET_DEFAULT;
                            var opts = new LZMA2Options(preset);
                            opts.setDictSize(DICT_SIZE);
                            compressedOutput.write(magic);
                            compressedOutput.write(preset);
                            compressedOutput.write(Integer.numberOfTrailingZeros(DICT_SIZE));
                            compressedOutput.write(new byte[]{
                                            (byte) (uncompressedSize),
                                            (byte) (uncompressedSize >> 8),
                                            (byte) (uncompressedSize >> 16),
                                            (byte) (uncompressedSize >> 24)});
                            try (OutputStream xz = opts.getOutputStream(new FinishableWrapperOutputStream(compressedOutput))) {
                                resourceInput.transferTo(xz);
                            }
                            byte[] compressed = compressedOutput.toByteArray();
                            int compressedSize = compressed.length;
                            if ((double) compressedSize / uncompressedSize > GOOD_ENOUGH_COMPRESSION_RATIO) {
                                keepUncompressedThreshold = uncompressedSize;
                                continue;
                            }
                            data = compressed;
                        }
                    }
                } while (data == null);
                RuntimeResourceAccess.addResource(module, name, data);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R, E extends Throwable> {
        R apply(T t) throws E;
    }
}

@TargetClass(value = org.graalvm.shadowed.com.ibm.icu.impl.ICUData.class)
final class Target_org_graalvm_shadowed_com_ibm_icu_impl_ICUData {
    @Substitute
    private static InputStream getResourceStream(ClassLoader loader, Class<?> root, String resourceName) {
        Class<?> refClass = root;
        if (refClass == null && loader == ICUData.class.getClassLoader()) {
            refClass = ICUData.class;
        }
        boolean uncompressed = false;
        do {
            InputStream inputStream;
            if (refClass != null) {
                inputStream = refClass.getResourceAsStream(resourceName);
            } else {
                inputStream = loader.getResourceAsStream(resourceName);
            }
            if (inputStream == null) {
                return null;
            }
            if (uncompressed) {
                return inputStream;
            }
            try {
                byte[] expected = new byte[]{(byte) ICU4JFeature.MAGIC, (byte) (ICU4JFeature.MAGIC >> 8)};
                byte[] hdr = new byte[expected.length];
                int nBytes = inputStream.read(hdr);
                if (nBytes >= expected.length && Arrays.equals(hdr, 0, expected.length, expected, 0, expected.length)) {
                    int preset = inputStream.read();
                    int dictSize = inputStream.read();
                    int uncompressedSize = inputStream.read() |
                                    (inputStream.read() << 8) |
                                    (inputStream.read() << 16) |
                                    (inputStream.read() << 24);
                    var opts = new LZMA2Options(preset);
                    opts.setDictSize(1 << dictSize);
                    return opts.getInputStream(inputStream);
                } else {
                    // Resource is not compressed. Reopen and return the original input stream.
                    uncompressed = true;
                    inputStream.close();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } while (true);
    }
}
