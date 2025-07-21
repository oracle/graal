/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import static jdk.graal.compiler.serviceprovider.GraalServices.getSavedProperty;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMField;

/**
 * Interposes on {@link HotSpotVMConfigAccess} to {@linkplain #isPresent check} when retrieving VM
 * configuration values that they are only present in the VM iff expected to be.
 */
public class GraalHotSpotVMConfigAccess {

    protected final HotSpotVMConfigAccess access;
    private final Map<String, Long> vmAddresses;
    private final Map<String, Long> vmConstants;
    private final Map<String, VMField> vmFields;

    GraalHotSpotVMConfigAccess(HotSpotVMConfigAccess access, Platform platform) {
        this.access = access;
        HotSpotVMConfigStore store = access.getStore();
        this.vmAddresses = store.getAddresses();
        this.vmConstants = store.getConstants();
        this.vmFields = store.getFields();
        this.osName = platform.osName();
        this.osArch = platform.archName();
    }

    public HotSpotVMConfigStore getStore() {
        return access.getStore();
    }

    /**
     * Name for current OS. Will be a value in {@link Platform#KNOWN_OS_NAMES}.
     */
    public final String osName;

    /**
     * Name for current CPU architecture. Will be a value in {@link Platform#KNOWN_ARCHITECTURES}.
     */
    public final String osArch;

    private final List<String> missing = new ArrayList<>();
    private final List<String> unexpected = new ArrayList<>();

    /**
     * Records an error if {@code map.contains(name) != expectPresent}. That is, it's an error if
     * {@code value} is unexpectedly present in the VM or is unexpectedly absent from the VM.
     *
     * @param expectPresent the expectation that the value is present in the VM
     */
    private boolean isPresent(String name, Map<String, ?> map, boolean expectPresent) {
        if (map.containsKey(name)) {
            if (!expectPresent) {
                recordError(name, unexpected, String.valueOf(map.get(name)));
            }
            return true;
        }
        if (expectPresent) {
            recordError(name, missing, null);
        }

        return false;
    }

    // Only defer errors while in the GraalHotSpotVMConfig
    // constructor until reportErrors() is called.
    private boolean deferErrors = this instanceof GraalHotSpotVMConfig;

    private void recordError(String name, List<String> list, String unexpectedValue) {
        String message = name;
        if (deferErrors) {
            StackTraceElement[] trace = new Exception().getStackTrace();
            for (StackTraceElement e : trace) {
                if (e.getClassName().equals(GraalHotSpotVMConfigAccess.class.getName())) {
                    // Skip methods in GraalHotSpotVMConfigAccess
                    continue;
                }
                // Looking for the field assignment in a constructor
                if (e.getMethodName().equals("<init>")) {
                    message += " at " + e;
                    break;
                }
            }
        }
        if (unexpectedValue != null) {
            message += " [value: " + unexpectedValue + "]";
        }
        list.add(message);
        if (!deferErrors) {
            reportErrors();
        }
    }

    protected void reportErrors() {
        deferErrors = false;
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            String runtime = String.format("JDK %s %s-%s (java.home=%s, java.vm.name=%s, java.vm.version=%s)",
                            getSavedProperty("java.specification.version"), osName, osArch,
                            getSavedProperty("java.home"),
                            getSavedProperty("java.vm.name"),
                            getSavedProperty("java.vm.version"));
            List<String> messages = new ArrayList<>();
            if (!missing.isEmpty()) {
                messages.add(String.format("VM config values missing that should be present in %s:%n    %s", runtime,
                                missing.stream().sorted().collect(Collectors.joining(System.lineSeparator() + "    "))));
            }
            if (!unexpected.isEmpty()) {
                messages.add(String.format("VM config values not expected to be present in %s:%n    %s", runtime,
                                unexpected.stream().sorted().collect(Collectors.joining(System.lineSeparator() + "    "))));
            }
            reportError(String.join(System.lineSeparator(), messages));
        }
    }

    /**
     * Name of system property that can be used to change behavior of reporting config errors.
     */
    private static final String JVMCI_CONFIG_CHECK_PROP_NAME = "debug.jdk.graal.jvmciConfigCheck";

    static void reportError(String rawErrorMessage) {
        String value = getSavedProperty(JVMCI_CONFIG_CHECK_PROP_NAME);
        if ("ignore".equals(value)) {
            return;
        }
        boolean warn = "warn".equals(value);
        Formatter message = new Formatter().format(rawErrorMessage);
        String javaHome = getSavedProperty("java.home");
        String vmName = getSavedProperty("java.vm.name");
        if (warn) {
            message.format("%nSet the %s system property to \"ignore\" to suppress ", JVMCI_CONFIG_CHECK_PROP_NAME);
            message.format("this warning and continue execution.%n");
        } else {
            message.format("%nSet the %s system property to \"ignore\" to suppress ", JVMCI_CONFIG_CHECK_PROP_NAME);
            message.format("this error or to \"warn\" to emit a warning and continue execution.%n");
        }
        message.format("Currently used Java home directory is %s.%n", javaHome);
        message.format("Currently used VM configuration is: %s%n", vmName);
        if (warn) {
            System.err.println(message);
        } else {
            throw new JVMCIError(message.toString());
        }
    }

    /**
     * @see HotSpotVMConfigAccess#getAddress(String, Long)
     */
    public long getAddress(String name, Long notPresent, boolean expectPresent) {
        if (isPresent(name, vmAddresses, expectPresent)) {
            return access.getAddress(name, notPresent);
        }
        return notPresent;
    }

    /**
     * @see HotSpotVMConfigAccess#getAddress(String)
     */
    public long getAddress(String name) {
        if (isPresent(name, vmAddresses, true)) {
            return access.getAddress(name, 0L);
        }
        return 0L;
    }

    /**
     * @see HotSpotVMConfigAccess#getConstant(String, Class, Object)
     */
    public <T> T getConstant(String name, Class<T> type, T notPresent, boolean expectPresent) {
        if (isPresent(name, vmConstants, expectPresent)) {
            return access.getConstant(name, type, notPresent);
        }
        return notPresent;
    }

    /**
     * @see HotSpotVMConfigAccess#getConstant(String, Class)
     */
    public <T> T getConstant(String name, Class<T> type) {
        if (isPresent(name, vmConstants, true)) {
            return access.getConstant(name, type);
        }
        return getDefault(type);
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldOffset(String, Class, String, Object)
     */
    public <T> T getFieldOffset(String name, Class<T> type, String cppType, T notPresent, boolean expectPresent) {
        if (isPresent(name, vmFields, expectPresent)) {
            return access.getFieldOffset(name, type, cppType, notPresent);
        }
        return notPresent;
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldOffset(String, Class, String)
     */
    public <T> T getFieldOffset(String name, Class<T> type, String cppType) {
        if (isPresent(name, vmFields, true)) {
            return access.getFieldOffset(name, type, cppType);
        }
        return getDefault(type);
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldOffset(String, Class)
     */
    public <T> T getFieldOffset(String name, Class<T> type) {
        if (isPresent(name, vmFields, true)) {
            return access.getFieldOffset(name, type);
        }
        return getDefault(type);
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldAddress(String, String)
     */
    public long getFieldAddress(String name, String cppType) {
        if (isPresent(name, vmFields, true)) {
            return access.getFieldAddress(name, cppType);
        }
        return 0L;
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldAddress(String, String)
     */
    public long getFieldAddress(String name, String cppType, long notPresent, boolean expectPresent) {
        if (isPresent(name, vmFields, expectPresent)) {
            return access.getFieldAddress(name, cppType);
        }
        return notPresent;
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldValue(String, Class, String, Object)
     */
    public <T> T getFieldValue(String name, Class<T> type, String cppType, T notPresent, boolean expectPresent) {
        if (isPresent(name, vmFields, expectPresent)) {
            return access.getFieldValue(name, type, cppType, notPresent);
        }
        return notPresent;
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldValue(String, Class, String)
     */
    public <T> T getFieldValue(String name, Class<T> type, String cppType) {
        if (isPresent(name, vmFields, true)) {
            return access.getFieldValue(name, type, cppType);
        }
        return getDefault(type);
    }

    /**
     * @see HotSpotVMConfigAccess#getFieldValue(String, Class)
     */
    public <T> T getFieldValue(String name, Class<T> type) {
        if (isPresent(name, vmFields, true)) {
            return access.getFieldValue(name, type);
        }
        return getDefault(type);
    }

    /**
     * @see HotSpotVMConfigAccess#getFlag(String, Class)
     */
    public <T> T getFlag(String name, Class<T> type) {
        try {
            return access.getFlag(name, type);
        } catch (JVMCIError e) {
            recordError(name, missing, null);
            return getDefault(type);
        }
    }

    /**
     * @see HotSpotVMConfigAccess#getFlag(String, Class, Object)
     */
    public <T> T getFlag(String name, Class<T> type, T notPresent, boolean expectPresent) {
        if (expectPresent) {
            return getFlag(name, type);
        }
        // Expecting flag not to be present
        if (Assertions.assertionsEnabled()) {
            // There's more overhead for checking unexpectedly
            // present flag values due to the fact that private
            // JVMCI method (i.e., jdk.vm.ci.hotspot.CompilerToVM.getFlagValue)
            // is needed to determine whether a flag value is available.
            // As such, only incur the overhead when running with assertions enabled.
            try {
                T value = access.getFlag(name, type, null);
                // Flag value present -> fail
                recordError(name, unexpected, String.valueOf(value));
            } catch (JVMCIError e) {
                // Flag value not present -> pass
            }
        }
        return access.getFlag(name, type, notPresent);
    }

    private static <T> T getDefault(Class<T> type) {
        if (type == Boolean.class) {
            return type.cast(Boolean.FALSE);
        }
        if (type == Byte.class) {
            return type.cast((byte) 0);
        }
        if (type == Integer.class) {
            return type.cast(0);
        }
        if (type == Long.class) {
            return type.cast(0L);
        }
        if (type == String.class) {
            return type.cast(null);
        }
        throw new JVMCIError("Unsupported VM config value type: " + type.getName());
    }
}
