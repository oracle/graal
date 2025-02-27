/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.serviceprovider;

import static java.lang.Thread.currentThread;
import static org.graalvm.nativeimage.ImageInfo.inImageBuildtimeCode;
import static org.graalvm.nativeimage.ImageInfo.inImageRuntimeCode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import jdk.graal.compiler.core.ArchitectureSpecific;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.vm.ci.code.Architecture;
import org.graalvm.nativeimage.ImageInfo;

import jdk.graal.compiler.debug.GraalError;
import jdk.internal.misc.VM;
import jdk.vm.ci.meta.EncodedSpeculationReason;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.services.Services;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Interface to functionality that abstracts over which JDK version Graal is running on.
 */
public final class GraalServices {

    /**
     * Returns true if current runtime is in libgraal. Note that this is more specific than
     * {@link ImageInfo#inImageRuntimeCode()}. The latter will return true when executing any native
     * image, not just libgraal.
     */
    public static boolean isInLibgraal() {
        return Services.IS_IN_NATIVE_IMAGE;
    }

    /**
     * The set of services available in libgraal.
     */
    private static final Map<Class<?>, List<?>> libgraalServices;

    @Platforms(Platform.HOSTED_ONLY.class)
    private static Class<?> loadClassOrNull(String name) {
        try {
            return GraalServices.class.getClassLoader().loadClass(name);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Gets a name for the current architecture that is compatible with
     * {@link Architecture#getName()}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    private static String getJVMCIArch() {
        String rawArch = getSavedProperty("os.arch");
        return switch (rawArch) {
            case "x86_64" -> "AMD64";
            case "amd64" -> "AMD64";
            case "aarch64" -> "aarch64";
            case "riscv64" -> "riscv64";
            default -> throw new GraalError("Unknown or unsupported arch: %s", rawArch);
        };
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("unchecked")
    private static void addProviders(String arch, Class<?> service) {
        List<Object> providers = (List<Object>) GraalServices.libgraalServices.computeIfAbsent(service, key -> new ArrayList<>());
        for (Object provider : ServiceLoader.load(service, GraalServices.class.getClassLoader())) {
            if (provider instanceof ArchitectureSpecific as && !as.getArchitecture().equals(arch)) {
                // Skip provider for another architecture
                continue;
            }
            providers.add(provider);
        }
    }

    static {
        LibGraalSupport libgraal = LibGraalSupport.INSTANCE;
        if (libgraal != null) {
            libgraalServices = new HashMap<>();
            String arch = getJVMCIArch();
            libgraal.getClassModuleMap().keySet().stream()//
                            .map(GraalServices::loadClassOrNull)//
                            .filter(c -> c != null && c.getAnnotation(LibGraalService.class) != null)//
                            .forEach(service -> addProviders(arch, service));
        } else {
            libgraalServices = null;
        }
    }

    private GraalServices() {
    }

    /**
     * Gets an {@link Iterable} of the providers available for {@code service}. When called within
     * libgraal, {@code service} must be a {@link LibGraalService} annotated service type.
     */
    @SuppressWarnings("unchecked")
    public static <S> Iterable<S> load(Class<S> service) {
        if (inImageRuntimeCode() || libgraalServices != null) {
            List<?> list = libgraalServices.get(service);
            if (list == null) {
                throw new InternalError(String.format("No %s providers found in libgraal (missing %s annotation on %s?)",
                                service.getName(), LibGraalService.class.getName(), service.getName()));
            }
            return (Iterable<S>) list;
        }
        return load0(service);
    }

    /**
     * An escape hatch for calling {@link System#getProperties()} without falling afoul of
     * {@code VerifySystemPropertyUsage}.
     *
     * @param justification explains why {@link #getSavedProperties()} cannot be used
     */
    public static Properties getSystemProperties(String justification) {
        if (justification == null || justification.isEmpty()) {
            throw new IllegalArgumentException("non-empty justification required");
        }
        return System.getProperties();
    }

    /**
     * Gets an unmodifiable copy of the system properties in their state at system initialization
     * time. This method must be used instead of calling {@link Services#getSavedProperties()}
     * directly for any caller that will end up in libgraal.
     *
     * @see VM#getSavedProperties
     */
    public static Map<String, String> getSavedProperties() {
        if (inImageBuildtimeCode()) {
            // Avoid calling down to JVMCI native methods as they will fail to
            // link in a copy of JVMCI loaded by a LibGraalLoader.
            return jdk.internal.misc.VM.getSavedProperties();
        }
        return Services.getSavedProperties();
    }

    /**
     * Helper method equivalent to {@link #getSavedProperties()}{@code .getOrDefault(name, def)}.
     */
    public static String getSavedProperty(String name, String def) {
        return getSavedProperties().getOrDefault(name, def);
    }

    /**
     * Helper method equivalent to {@link #getSavedProperties()}{@code .get(name)}.
     */
    public static String getSavedProperty(String name) {
        return getSavedProperties().get(name);
    }

    private static <S> Iterable<S> load0(Class<S> service) {
        Module module = GraalServices.class.getModule();
        // Graal cannot know all the services used by another module
        // (e.g. enterprise) so dynamically register the service use now.
        if (!module.canUse(service)) {
            module.addUses(service);
        }

        return () -> {
            ModuleLayer layer = module.getLayer();
            Iterator<S> iterator = ServiceLoader.load(layer, service).iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public S next() {
                    S provider = iterator.next();
                    // Allow Graal extensions to access JVMCI
                    openJVMCITo(provider.getClass());
                    return provider;
                }

                @Override
                public void remove() {
                    iterator.remove();
                }
            };
        };
    }

    /**
     * Opens all JVMCI packages to the module of a given class. This relies on JVMCI already having
     * opened all its packages to the module defining {@link GraalServices}.
     *
     * @param other all JVMCI packages will be opened to the module defining this class
     */
    static void openJVMCITo(Class<?> other) {
        if (inImageRuntimeCode()) {
            return;
        }

        Module jvmciModule = JVMCI_MODULE;
        Module otherModule = other.getModule();
        if (jvmciModule != otherModule) {
            for (String pkg : jvmciModule.getPackages()) {
                if (!jvmciModule.isOpen(pkg, otherModule)) {
                    // JVMCI initialization opens all JVMCI packages
                    // to Graal which is a prerequisite for Graal to
                    // open JVMCI packages to other modules.
                    JVMCI.getRuntime();

                    jvmciModule.addOpens(pkg, otherModule);
                }
            }
        }
    }

    /**
     * Gets the provider for {@code service} for which at most one provider must be available. When
     * called within libgraal, {@code service} must be a {@link LibGraalService} annotated service
     * type.
     *
     * @param service the service whose provider is being requested
     * @param required specifies if an {@link InternalError} should be thrown if no provider of
     *            {@code service} is available
     * @return the requested provider if available else {@code null}
     */
    public static <S> S loadSingle(Class<S> service, boolean required) {
        assert !service.getName().startsWith("jdk.vm.ci") : "JVMCI services must be loaded via " + Services.class.getName();
        Iterable<S> providers = load(service);
        S singleProvider = null;
        try {
            for (Iterator<S> it = providers.iterator(); it.hasNext();) {
                singleProvider = it.next();
                if (it.hasNext()) {
                    S other = it.next();
                    throw new InternalError(String.format("Multiple %s providers found: %s, %s", service.getName(), singleProvider.getClass().getName(), other.getClass().getName()));
                }
            }
        } catch (ServiceConfigurationError e) {
            // If the service is required we will bail out below.
        }
        if (singleProvider == null) {
            if (required) {
                throw new InternalError(String.format("No provider for %s found", service.getName()));
            }
        }
        return singleProvider;
    }

    /**
     * Gets the class file bytes for {@code c}.
     */
    public static InputStream getClassfileAsStream(Class<?> c) throws IOException {
        String classfilePath = c.getName().replace('.', '/') + ".class";
        return c.getModule().getResourceAsStream(classfilePath);
    }

    private static final Module JVMCI_MODULE = Services.class.getModule();

    /**
     * A JVMCI package dynamically exported to trusted modules.
     */
    private static final String JVMCI_RUNTIME_PACKAGE = "jdk.vm.ci.runtime";
    static {
        assert JVMCI_MODULE.getPackages().contains(JVMCI_RUNTIME_PACKAGE);
    }

    /**
     * Determines if invoking {@link Object#toString()} on an instance of {@code c} will only run
     * trusted code.
     */
    public static boolean isToStringTrusted(Class<?> c) {
        if (inImageRuntimeCode()) {
            return true;
        }

        Module module = c.getModule();
        Module jvmciModule = JVMCI_MODULE;
        if (module == jvmciModule || jvmciModule.isOpen(JVMCI_RUNTIME_PACKAGE, module)) {
            // Can access non-statically-exported package in JVMCI
            return true;
        }
        return false;
    }

    /**
     * Creates an encoding of the context objects representing a speculation reason.
     *
     * @param groupId
     * @param groupName
     * @param context the objects forming a key for the speculation
     */
    static SpeculationReason createSpeculationReason(int groupId, String groupName, Object... context) {
        SpeculationEncodingAdapter adapter = new SpeculationEncodingAdapter();
        Object[] flattened = adapter.flatten(context);
        return new EncodedSpeculationReason(groupId, groupName, flattened);
    }

    /**
     * Gets a unique identifier for this execution such as a process ID or a
     * {@linkplain #getGlobalTimeStamp() fixed timestamp}.
     */
    public static String getExecutionID() {
        return Long.toString(ProcessHandle.current().pid());
    }

    private static final GlobalAtomicLong globalTimeStamp = new GlobalAtomicLong("GLOBAL_TIME_STAMP", 0L);

    /**
     * Gets a time stamp for the current process. This method will always return the same value for
     * the current VM execution.
     */
    public static long getGlobalTimeStamp() {
        if (globalTimeStamp.get() == 0L) {
            globalTimeStamp.compareAndSet(0L, milliTimeStamp());
        }
        return globalTimeStamp.get();
    }

    /**
     * Returns the current time in milliseconds. This is to guard against the incorrect use of
     * {@link System#currentTimeMillis()} for measuring elapsed time since it is affected by changes
     * to the system clock.
     */
    public static long milliTimeStamp() {
        return System.currentTimeMillis();
    }

    /**
     * Returns an approximation of the total amount of memory, in bytes, allocated in heap memory
     * for the thread of the specified ID. The returned value is an approximation because some Java
     * virtual machine implementations may use object allocation mechanisms that result in a delay
     * between the time an object is allocated and the time its size is recorded.
     * <p>
     * If the thread of the specified ID is not alive or does not exist, this method returns
     * {@code -1}. If thread memory allocation measurement is disabled, this method returns
     * {@code -1}. A thread is alive if it has been started and has not yet died.
     * <p>
     * If thread memory allocation measurement is enabled after the thread has started, the Java
     * virtual machine implementation may choose any time up to and including the time that the
     * capability is enabled as the point where thread memory allocation measurement starts.
     *
     * @param id the thread ID of a thread
     * @return an approximation of the total memory allocated, in bytes, in heap memory for a thread
     *         of the specified ID if the thread of the specified ID exists, the thread is alive,
     *         and thread memory allocation measurement is enabled; {@code -1} otherwise.
     *
     * @throws IllegalArgumentException if {@code id} {@code <=} {@code 0}.
     * @throws UnsupportedOperationException if the Java virtual machine implementation does not
     *             {@linkplain #isThreadAllocatedMemorySupported() support} thread memory allocation
     *             measurement.
     */
    public static long getThreadAllocatedBytes(long id) {
        if (jmx == null) {
            throw new UnsupportedOperationException();
        }
        return jmx.getThreadAllocatedBytes(id);
    }

    /**
     * Convenience method for calling {@link #getThreadAllocatedBytes(long)} with the id of the
     * current thread.
     */
    public static long getCurrentThreadAllocatedBytes() {
        return getThreadAllocatedBytes(getCurrentThreadId());
    }

    /**
     * Gets the identifier of {@code thread}.
     *
     * This method abstracts over how the identifier is retrieved in the context of JDK-8284161.
     */
    @SuppressWarnings("deprecation" /* JDK-8284161 */)
    public static long getThreadId(Thread thread) {
        return thread.getId();
    }

    /**
     * Gets the identifier of the current thread.
     *
     * This method abstracts over how the identifier is retrieved in the context of JDK-8284161.
     */
    @SuppressWarnings("deprecation" /* JDK-8284161 */)
    public static long getCurrentThreadId() {
        return getThreadId(currentThread());
    }

    /**
     * Returns the total CPU time for the current thread in nanoseconds. The returned value is of
     * nanoseconds precision but not necessarily nanoseconds accuracy. If the implementation
     * distinguishes between user mode time and system mode time, the returned CPU time is the
     * amount of time that the current thread has executed in user mode or system mode.
     *
     * @return the total CPU time for the current thread if CPU time measurement is enabled;
     *         {@code -1} otherwise.
     *
     * @throws UnsupportedOperationException if the Java virtual machine does not
     *             {@linkplain #isCurrentThreadCpuTimeSupported() support} CPU time measurement for
     *             the current thread
     */
    public static long getCurrentThreadCpuTime() {
        if (jmx == null) {
            throw new UnsupportedOperationException();
        }
        return jmx.getCurrentThreadCpuTime();
    }

    /**
     * Determines if the Java virtual machine implementation supports thread memory allocation
     * measurement.
     */
    public static boolean isThreadAllocatedMemorySupported() {
        if (jmx == null) {
            return false;
        }
        return jmx.isThreadAllocatedMemorySupported();
    }

    /**
     * Determines if the Java virtual machine supports CPU time measurement for the current thread.
     */
    public static boolean isCurrentThreadCpuTimeSupported() {
        if (jmx == null) {
            return false;
        }
        return jmx.isCurrentThreadCpuTimeSupported();
    }

    /**
     * Gets the input arguments passed to the Java virtual machine which does not include the
     * arguments to the {@code main} method. This method returns an empty list if there is no input
     * argument to the Java virtual machine.
     * <p>
     * Some Java virtual machine implementations may take input arguments from multiple different
     * sources: for examples, arguments passed from the application that launches the Java virtual
     * machine such as the 'java' command, environment variables, configuration files, etc.
     * <p>
     * Typically, not all command-line options to the 'java' command are passed to the Java virtual
     * machine. Thus, the returned input arguments may not include all command-line options.
     *
     * @return the input arguments to the JVM or {@code null} if they are unavailable
     */
    public static List<String> getInputArguments() {
        if (jmx == null) {
            return null;
        }
        return jmx.getInputArguments();
    }

    /**
     * Returns the fused multiply add of the three arguments; that is, returns the exact product of
     * the first two arguments summed with the third argument and then rounded once to the nearest
     * {@code float}.
     */
    public static float fma(float a, float b, float c) {
        return Math.fma(a, b, c);
    }

    /**
     * Returns the fused multiply add of the three arguments; that is, returns the exact product of
     * the first two arguments summed with the third argument and then rounded once to the nearest
     * {@code double}.
     */
    public static double fma(double a, double b, double c) {
        return Math.fma(a, b, c);
    }

    /**
     * Gets the update-release counter for the current Java runtime.
     *
     * @see java.lang.Runtime.Version
     */
    public static int getJavaUpdateVersion() {
        return Runtime.version().update();
    }

    private static final JMXService jmx = loadSingle(JMXService.class, libgraalServices != null);
}
