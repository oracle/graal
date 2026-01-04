/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static com.oracle.svm.core.IsolateArgumentAccess.readCCharPointer;
import static com.oracle.svm.core.IsolateArgumentAccess.readLong;
import static com.oracle.svm.core.IsolateArgumentAccess.writeBoolean;
import static com.oracle.svm.core.IsolateArgumentAccess.writeCCharPointer;
import static com.oracle.svm.core.IsolateArgumentAccess.writeLong;
import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.imagelayer.BuildingImageLayerPredicate;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredPersistFlags;
import com.oracle.svm.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.ImageHeapList;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.word.Word;

/**
 * Parses a small subset of the runtime arguments before the image heap is mapped and before the
 * isolate is fully started.
 *
 * If options are specified in {@link CEntryPointCreateIsolateParameters} and {@code argv} the value
 * stored in {@code argv} is used.
 */
@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class IsolateArgumentParser {
    @SuppressWarnings("unchecked")//
    private final List<RuntimeOptionKey<?>> options = (List<RuntimeOptionKey<?>>) ImageHeapList.createGeneric(RuntimeOptionKey.class);
    private static final CGlobalData<CCharPointer> OPTION_NAMES = CGlobalDataFactory.createBytes(IsolateArgumentParser::createOptionNames);
    private static final CGlobalData<CIntPointer> OPTION_NAME_POSITIONS = CGlobalDataFactory.createBytes(IsolateArgumentParser::createOptionNamePosition);
    private static final CGlobalData<CCharPointer> OPTION_TYPES = CGlobalDataFactory.createBytes(IsolateArgumentParser::createOptionTypes);

    /** The default values are created in {@code RuntimeOptionsFeature}. */
    public interface DefaultValuesProvider {
        CGlobalData<CLongPointer> getDefaultValues();
    }

    /**
     * All values (regardless of their type) are stored as 8 byte values. See
     * {@link IsolateArguments#setParsedArgs(CLongPointer)} for more information.
     */
    private long[] parsedOptionValues;

    private static final long K = 1024;
    private static final long M = K * K;
    private static final long G = K * M;
    private static final long T = K * G;

    private boolean isCompilationIsolate;

    @Platforms(Platform.HOSTED_ONLY.class)
    public IsolateArgumentParser() {
    }

    @Fold
    public static IsolateArgumentParser singleton() {
        return ImageSingletons.lookup(IsolateArgumentParser.class);
    }

    /**
     * Getter method for retrieving defaultValues. Note we fold this method to ensure the actual
     * cglobal data object is seen at all call sites.
     */
    @Fold
    public static CGlobalData<CLongPointer> getDefaultValues() {
        return ImageSingletons.lookup(DefaultValuesProvider.class).getDefaultValues();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void register(RuntimeOptionKey<?> optionKey) {
        assert optionKey != null;
        assert singleton().parsedOptionValues == null;

        singleton().options.add(optionKey);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void sealOptions() {
        singleton().parsedOptionValues = new long[getOptionCount()];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static byte[] createOptionNames() {
        StringBuilder optionNames = new StringBuilder();
        for (int i = 0; i < getOptionCount(); i++) {
            optionNames.append(getOptions().get(i).getName());
            optionNames.append("\0");
        }
        return optionNames.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static byte[] createOptionNamePosition() {
        byte[] result = new byte[Integer.BYTES * getOptionCount()];
        ByteBuffer buffer = ByteBuffer.wrap(result).order(ByteOrder.nativeOrder());
        buffer.putInt(0);

        byte[] optionNames = createOptionNames();
        for (int i = 0; i < optionNames.length; i++) {
            if (optionNames[i] == '\0' && (i + 1) < optionNames.length) {
                buffer.putInt(i + 1);
            }
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static byte[] createOptionTypes() {
        byte[] result = new byte[Byte.BYTES * getOptionCount()];
        ByteBuffer buffer = ByteBuffer.wrap(result).order(ByteOrder.nativeOrder());
        for (int i = 0; i < getOptionCount(); i++) {
            Class<?> optionValueType = getOptions().get(i).getDescriptor().getOptionValueType();
            buffer.put(OptionValueType.fromClass(optionValueType));
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static byte[] createDefaultValues() {
        assert !ImageLayerBuildingSupport.buildingImageLayer();
        return createDefaultValuesArray(getOptions());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static byte[] createDefaultValuesArray(List<RuntimeOptionKey<?>> options) {
        assert options.size() == getOptionCount();
        byte[] result = new byte[Long.BYTES * options.size()];
        ByteBuffer buffer = ByteBuffer.wrap(result).order(ByteOrder.nativeOrder());
        for (var option : options) {
            VMError.guarantee(option.isIsolateCreationOnly(), "Options parsed by IsolateArgumentParser should all have the IsolateCreationOnly flag. %s doesn't", option);
            long value = toLong(option.getHostedValue(), option.getDescriptor().getOptionValueType());
            buffer.putLong(value);
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static long toLong(Object value, Class<?> clazz) {
        if (value == null && String.class.equals(clazz)) {
            return 0L;
        } else if (value instanceof Boolean) {
            return ((Boolean) value) ? 1L : 0L;
        } else if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Long) {
            return (Long) value;
        } else {
            throw VMError.shouldNotReachHere("Unexpected option value: " + value);
        }
    }

    @Fold
    protected static List<RuntimeOptionKey<?>> getOptions() {
        return singleton().options;
    }

    @Fold
    protected static int getOptionCount() {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            return getOptions().size();
        } else {
            return LayeredOptionInfo.singleton().getNumOptions();
        }
    }

    @Uninterruptible(reason = "Still being initialized.")
    public void parse(CEntryPointCreateIsolateParameters parameters, IsolateArguments arguments) {
        initialize(arguments, parameters);
        if (!LibC.isSupported() || !shouldParseArguments(arguments)) {
            // Without LibC support, argument parsing is disabled. So, we just use the build-time
            // values of the runtime options.
            return;
        }

        CLongPointer value = StackValue.get(Long.BYTES);
        // Ignore the first argument as it represents the executable file name.
        for (int i = 1; i < arguments.getArgc(); i++) {
            CCharPointer arg = arguments.getArgv().read(i);
            if (arg.isNonNull()) {
                CCharPointer tail = matchPrefix(arg);
                if (tail.isNonNull()) {
                    CCharPointer xOptionTail = matchXOption(tail);
                    if (xOptionTail.isNonNull()) {
                        parseXOption(arguments, value, xOptionTail);
                    } else {
                        CCharPointer xxOptionTail = matchXXOption(tail);
                        if (xxOptionTail.isNonNull()) {
                            parseXXOption(arguments, value, xxOptionTail);
                        }
                    }
                }
            }
        }

        copyStringArguments(arguments);
    }

    @Uninterruptible(reason = "Tear-down in progress.")
    public boolean tearDown(IsolateArguments arguments) {
        for (int i = 0; i < getOptionCount(); i++) {
            if (OPTION_TYPES.get().read(i) == OptionValueType.C_CHAR_POINTER) {
                UntrackedNullableNativeMemory.free(readCCharPointer(arguments, i));
                writeCCharPointer(arguments, i, Word.nullPointer());
            }
        }
        return true;
    }

    @Uninterruptible(reason = "Tear-down in progress.")
    public boolean tearDown() {
        for (int i = 0; i < getOptionCount(); i++) {
            if (OPTION_TYPES.get().read(i) == OptionValueType.C_CHAR_POINTER) {
                UntrackedNullableNativeMemory.free(Word.pointer(parsedOptionValues[i]));
                parsedOptionValues[i] = 0;
            }
        }
        return true;
    }

    public void copyToRuntimeOptions() {
        int index = getOptionIndex(SubstrateGCOptions.ReservedAddressSpaceSize);
        long value = getLongOptionValue(index);
        if (getDefaultValues().get().read(index) != value) {
            SubstrateGCOptions.ReservedAddressSpaceSize.update(value);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void copyStringArguments(IsolateArguments arguments) {
        for (int i = 0; i < getOptionCount(); i++) {
            if (OPTION_TYPES.get().read(i) == OptionValueType.C_CHAR_POINTER) {
                CCharPointer string = readCCharPointer(arguments, i);

                if (string.isNonNull()) {
                    CCharPointer copy = LibC.strdup(string);
                    VMError.guarantee(copy.isNonNull(), "Copying of string argument failed.");
                    writeCCharPointer(arguments, i, copy);
                } else {
                    writeCCharPointer(arguments, i, Word.nullPointer());
                }
            }
        }
    }

    /**
     * Note that this logic must be in sync with
     * {@link com.oracle.svm.core.option.RuntimeOptionParser#parseAndConsumeAllOptions}.
     */
    @Uninterruptible(reason = "Thread state not yet set up.")
    public static boolean shouldParseArguments(IsolateArguments arguments) {
        if (SubstrateOptions.ParseRuntimeOptions.getValue()) {
            return true;
        } else if (RuntimeCompilation.isEnabled() && SubstrateOptions.supportCompileInIsolates()) {
            return isCompilationIsolate(arguments);
        }
        return false;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    private static boolean isCompilationIsolate(IsolateArguments arguments) {
        return arguments.getIsCompilationIsolate();
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    public void persistOptions(IsolateArguments arguments) {
        isCompilationIsolate = isCompilationIsolate(arguments);

        for (int i = 0; i < getOptionCount(); i++) {
            parsedOptionValues[i] = readLong(arguments, i);
        }
    }

    public void verifyOptionValues() {
        for (int i = 0; i < getOptionCount(); i++) {
            RuntimeOptionKey<?> option = getOptions().get(i);
            if (shouldValidate(option)) {
                validate(option, getOptionValue(i));
            }
        }
    }

    private static boolean shouldValidate(RuntimeOptionKey<?> option) {
        if (SubstrateOptions.useSerialGC()) {
            /* The serial GC supports changing the heap size at run-time to some degree. */
            return option != SubstrateGCOptions.MinHeapSize && option != SubstrateGCOptions.MaxHeapSize && option != SubstrateGCOptions.MaxNewSize;
        }
        return true;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isCompilationIsolate() {
        return singleton().isCompilationIsolate;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean getBooleanOptionValue(int index) {
        return parsedOptionValues[index] == 1L;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getIntOptionValue(int index) {
        return (int) parsedOptionValues[index];
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getLongOptionValue(int index) {
        return parsedOptionValues[index];
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setLongOptionValue(int optionIndex, long newValue) {
        parsedOptionValues[optionIndex] = newValue;
    }

    protected CCharPointer getCCharPointerOptionValue(int index) {
        return Word.pointer(parsedOptionValues[index]);
    }

    protected Object getOptionValue(int index) {
        Class<?> optionValueType = getOptions().get(index).getDescriptor().getOptionValueType();
        long value = parsedOptionValues[index];
        if (optionValueType == Boolean.class) {
            assert value == 0L || value == 1L : value;
            return value == 1L;
        } else if (optionValueType == Integer.class) {
            return (int) value;
        } else if (optionValueType == Long.class) {
            return value;
        } else if (optionValueType == String.class) {
            if (value == 0L) {
                return null;
            }

            return CTypeConversion.toJavaString(Word.pointer(value));
        } else {
            throw VMError.shouldNotReachHere("Option value has unexpected type: " + optionValueType);
        }
    }

    private static void validate(RuntimeOptionKey<?> option, Object oldValue) {
        Object newValue = option.getValue();
        if (oldValue == newValue) {
            return;
        }

        if (newValue == null || !newValue.equals(oldValue)) {
            throw new IllegalArgumentException(
                            "The option '" + option.getName() + "' can't be changed after isolate creation. Old value: " + oldValue + ", new value: " + newValue);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected void initialize(IsolateArguments arguments, CEntryPointCreateIsolateParameters parameters) {
        for (int i = 0; i < getOptionCount(); i++) {
            writeLong(arguments, i, getDefaultValues().get().read(i));
        }

        if (parameters.isNonNull() && parameters.version() >= 3) {
            arguments.setArgc(parameters.getArgc());
            arguments.setArgv(parameters.getArgv());
            arguments.setProtectionKey(parameters.protectionKey());
        } else {
            arguments.setArgc(0);
            arguments.setArgv(Word.nullPointer());
            arguments.setProtectionKey(0);
        }

        if (parameters.isNonNull() && parameters.version() >= 5) {
            arguments.setIsCompilationIsolate(parameters.getIsCompilationIsolate());
        } else {
            arguments.setIsCompilationIsolate(false);
        }

        /*
         * If a value for ReservedAddressSpaceSize is set in the isolate parameters, then this value
         * has a higher priority than a default value that was set at build-time.
         */
        UnsignedWord reservedAddressSpaceSize = parameters.reservedSpaceSize();
        if (reservedAddressSpaceSize.notEqual(0)) {
            writeLong(arguments, getOptionIndex(SubstrateGCOptions.ReservedAddressSpaceSize), reservedAddressSpaceSize.rawValue());
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static CCharPointer matchPrefix(CCharPointer arg) {
        if (arg.read(0) == '-') {
            return arg.addressOf(1);
        }
        return Word.nullPointer();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static CCharPointer matchXOption(CCharPointer arg) {
        if (arg.read(0) == 'X' && arg.read(1) == 'm') {
            return arg.addressOf(2);
        }
        return Word.nullPointer();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static CCharPointer matchXXOption(CCharPointer arg) {
        if (arg.read(0) == 'X' && arg.read(1) == 'X' && arg.read(2) == ':') {
            return arg.addressOf(3);
        }
        return Word.nullPointer();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void parseXOption(IsolateArguments arguments, CLongPointer value, CCharPointer tail) {
        byte kind = tail.read();
        if (kind == 's' && parseNumericXOption(tail.addressOf(1), value)) {
            writeLong(arguments, getOptionIndex(SubstrateGCOptions.MinHeapSize), value.read());
        } else if (kind == 'x' && parseNumericXOption(tail.addressOf(1), value)) {
            writeLong(arguments, getOptionIndex(SubstrateGCOptions.MaxHeapSize), value.read());
        } else if (kind == 'n' && parseNumericXOption(tail.addressOf(1), value)) {
            writeLong(arguments, getOptionIndex(SubstrateGCOptions.MaxNewSize), value.read());
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void parseXXOption(IsolateArguments arguments, CLongPointer value, CCharPointer tail) {
        byte firstChar = tail.read();
        if (firstChar == '+' || firstChar == '-') {
            boolean booleanValue = firstChar == '+';
            for (int i = 0; i < getOptionCount(); i++) {
                int pos = OPTION_NAME_POSITIONS.get().read(i);
                CCharPointer optionName = OPTION_NAMES.get().addressOf(pos);
                if (OPTION_TYPES.get().read(i) == OptionValueType.BOOLEAN && matches(tail.addressOf(1), optionName)) {
                    writeBoolean(arguments, i, booleanValue);
                    break;
                }
            }
        } else {
            for (int i = 0; i < getOptionCount(); i++) {
                int pos = OPTION_NAME_POSITIONS.get().read(i);
                CCharPointer optionName = OPTION_NAMES.get().addressOf(pos);
                CCharPointer valueStart = startsWith(tail, optionName);

                if (valueStart.isNonNull() && valueStart.read() == '=') {
                    if (OptionValueType.isNumeric(OPTION_TYPES.get().read(i))) {
                        parseNumericXOption(valueStart.addressOf(1), value);
                        writeLong(arguments, i, value.read());
                        break;
                    } else if (OPTION_TYPES.get().read(i) == OptionValueType.C_CHAR_POINTER) {
                        writeCCharPointer(arguments, i, valueStart.addressOf(1));
                        break;
                    }
                }

            }
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean parseNumericXOption(CCharPointer string, CLongPointer result) {
        CCharPointer pos = string;

        boolean negativeValue = false;
        if (pos.read() == '-') {
            negativeValue = true;
            pos = pos.addressOf(1);
        }

        if (!atojulong(pos, result)) {
            return false;
        }

        if (negativeValue) {
            result.write(-result.read());
        }
        return true;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean atojulong(CCharPointer s, CLongPointer result) {
        /* First char must be a digit. Don't allow negative numbers or leading spaces. */
        if (LibC.isdigit(s.read()) == 0) {
            return false;
        }

        CCharPointerPointer tailPtr = (CCharPointerPointer) StackValue.get(CCharPointer.class);

        LibC.setErrno(0);
        UnsignedWord n = LibC.strtoull(s, tailPtr, 10);
        if (LibC.errno() != 0) {
            return false;
        }

        CCharPointer tail = tailPtr.read();
        /*
         * Fail if no number was read at all or if the tail contains more than a single non-digit
         * character.
         */
        if (tail == s || LibC.strlen(tail).aboveThan(1)) {
            return false;
        }

        long modifier;
        switch (tail.read()) {
            case 'T', 't' -> modifier = T;
            case 'G', 'g' -> modifier = G;
            case 'M', 'm' -> modifier = M;
            case 'K', 'k' -> modifier = K;
            case '\0' -> modifier = 1;
            default -> {
                return false;
            }
        }

        UnsignedWord value = n.multiply(Word.unsigned(modifier));
        if (checkForOverflow(value, n, modifier)) {
            return false;
        }
        result.write(value.rawValue());
        return true;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean checkForOverflow(UnsignedWord value, UnsignedWord n, long modifier) {
        return value.unsignedDivide(Word.unsigned(modifier)) != n;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean matches(CCharPointer input, CCharPointer expected) {
        CCharPointer tail = startsWith(input, expected);
        return tail.isNonNull() && tail.read() == 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static CCharPointer startsWith(CCharPointer input, CCharPointer prefix) {
        int i = 0;
        while (prefix.read(i) != 0 && input.read(i) == prefix.read(i)) {
            i++;
        }

        if (prefix.read(i) == 0) {
            return input.addressOf(i);
        }
        return Word.nullPointer();
    }

    @Fold
    public static int getOptionIndex(RuntimeOptionKey<?> key) {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            List<RuntimeOptionKey<?>> options = getOptions();
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i) == key) {
                    return i;
                }
            }
        } else {
            var keyName = key.getName();
            var optionNames = LayeredOptionInfo.singleton().getOptionNames();
            for (int i = 0; i < optionNames.size(); i++) {
                if (optionNames.get(i).equals(keyName)) {
                    return i;
                }
            }
        }

        throw VMError.shouldNotReachHere("Could not find option " + key.getName() + " in the options array.");
    }

    @Fold
    public static int getParsedArgsSize() {
        return Long.BYTES * getOptionCount();
    }

    protected static class OptionValueType {
        public static final byte BOOLEAN = 1;
        public static final byte INTEGER = 2;
        public static final byte LONG = 3;
        public static final byte C_CHAR_POINTER = 4;

        public static byte fromClass(Class<?> c) {
            if (c == Boolean.class) {
                return BOOLEAN;
            } else if (c == Integer.class) {
                return INTEGER;
            } else if (c == Long.class) {
                return LONG;
            } else if (c == String.class) {
                return C_CHAR_POINTER;
            } else {
                throw VMError.shouldNotReachHere("Option value has unexpected type: " + c);
            }
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static boolean isNumeric(byte optionValueType) {
            return optionValueType == INTEGER || optionValueType == LONG;
        }
    }

    /**
     * Within {@link IsolateArgumentParser} many methods need to be {@link Fold}ed. This class adds
     * support so that we can handle these method folds within the application layer.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @AutomaticallyRegisteredImageSingleton(onlyWith = BuildingImageLayerPredicate.class)
    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = LayeredOptionInfo.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
    static class LayeredOptionInfo {
        private static final int UNSET = -1;
        final int numOptions;
        final List<String> optionNames;

        LayeredOptionInfo() {
            this(UNSET, null);
        }

        LayeredOptionInfo(int numOptions, List<String> optionNames) {
            this.numOptions = numOptions;
            this.optionNames = optionNames;
        }

        static LayeredOptionInfo singleton() {
            return ImageSingletons.lookup(LayeredOptionInfo.class);
        }

        int getNumOptions() {
            if (numOptions == UNSET) {
                throw VMError.shouldNotReachHere("numOptions is unset");
            }
            return numOptions;
        }

        List<String> getOptionNames() {
            Objects.requireNonNull(optionNames);
            return optionNames;
        }

        static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {

            @Override
            public SingletonTrait getLayeredCallbacksTrait() {
                return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks<LayeredOptionInfo>() {
                    @Override
                    public LayeredPersistFlags doPersist(ImageSingletonWriter writer, LayeredOptionInfo singleton) {
                        if (ImageLayerBuildingSupport.firstImageBuild()) {
                            writer.writeInt("numOptions", IsolateArgumentParser.getOptionCount());
                            writer.writeStringList("optionNames", IsolateArgumentParser.getOptions().stream().map(OptionKey::getName).toList());
                        } else {
                            writer.writeInt("numOptions", singleton.getNumOptions());
                            writer.writeStringList("optionNames", singleton.optionNames);
                        }
                        return LayeredPersistFlags.CREATE;
                    }

                    @Override
                    public Class<? extends SingletonLayeredCallbacks.LayeredSingletonInstantiator<?>> getSingletonInstantiator() {
                        return SingletonInstantiator.class;
                    }
                });
            }
        }
    }

    static class SingletonInstantiator implements SingletonLayeredCallbacks.LayeredSingletonInstantiator<LayeredOptionInfo> {
        @Override
        public LayeredOptionInfo createFromLoader(ImageSingletonLoader loader) {
            int numOptions = loader.readInt("numOptions");
            var optionNames = Collections.unmodifiableList(loader.readStringList("optionNames"));
            return new LayeredOptionInfo(numOptions, optionNames);
        }
    }
}
