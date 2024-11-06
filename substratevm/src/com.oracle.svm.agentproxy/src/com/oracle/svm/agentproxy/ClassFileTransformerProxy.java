package com.oracle.svm.agentproxy;

import jdk.internal.module.ModuleLoaderMap;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/**
 * A JDK dynamic proxy for {@link ClassFileTransformer} that guards GraalVM
 * framework classes from being transformed by target agents during a Native
 * Image build.
 *
 * <p>
 * When a target agent registers a {@link ClassFileTransformer} via
 * {@link java.lang.instrument.Instrumentation#addTransformer},
 * {@link InstrumentationProxy} wraps it with this proxy. Each {@code transform}
 * invocation then checks whether the class belongs to a protected module (JDK
 * boot/platform modules or GraalVM system modules listed in
 * {@link #SYSTEM_MODULES}). If so, the transformation is suppressed by
 * returning {@code null}. Otherwise the call is forwarded to the actual
 * transformer.
 *
 * <p>
 * When {@code DEBUG} mode is enabled, the binary name of each suppressed class
 * is appended (one per line) to the file specified by the system property
 * {@code com.oracle.svm.agentproxy.suppressed.dump.file} for offline
 * verification.
 */
public class ClassFileTransformerProxy implements InvocationHandler {

    /**
     * GraalVM framework module names that must not be transformed by target agents
     * during a Native Image build.
     *
     * <p>
     * This set covers all GraalVM compiler, SDK, and Substrate VM framework modules
     * that are loaded into the host JVM during the build. Transforming any of these
     * modules by a user-registered agent could silently corrupt the compilation
     * pipeline or analysis results.
     *
     * <p>
     * The core subset (compiler, nativeimage, word) is kept in sync with
     * {@code com.oracle.svm.util.HostedModuleSupport#SYSTEM_MODULES}.
     * Additional modules covering the full GraalVM SDK, SVM hosted infrastructure,
     * and Truffle runtime are included here because they are all present in the
     * host JVM classpath during a build and must equally be protected.
     */
    private static final Set<String> SYSTEM_MODULES = Set.of(
            // ---- Enterprise overlays ----
            "com.oracle.graal.graal_enterprise",
            "com.oracle.svm.svm_enterprise",

            // ---- Graal compiler ----
            "jdk.graal.compiler",
            "jdk.graal.compiler.options",
            "jdk.graal.compiler.vmaccess",
            "jdk.graal.compiler.vmaccess.guest",
            "jdk.graal.compiler.hostvmaccess",
            "jdk.graal.compiler.libgraal",
            "jdk.graal.compiler.management",

            // ---- JVMCI ----
            "jdk.internal.vm.ci",

            // ---- GraalVM SDK ----
            "org.graalvm.sdk",
            "org.graalvm.collections",
            "org.graalvm.word",
            "org.graalvm.nativebridge",
            "org.graalvm.jniutils",
            "org.graalvm.polyglot",
            "org.graalvm.launcher",
            "org.graalvm.nativeimage",
            "org.graalvm.nativeimage.libgraal",

            // ---- Substrate VM / Native Image builder ----
            "org.graalvm.nativeimage.base",
            "org.graalvm.nativeimage.builder",
            "org.graalvm.nativeimage.shared",
            "org.graalvm.nativeimage.guest",
            "org.graalvm.nativeimage.guest.staging",
            "org.graalvm.nativeimage.driver",
            "org.graalvm.nativeimage.librarysupport",
            "org.graalvm.nativeimage.objectfile",
            "org.graalvm.nativeimage.configure",
            "org.graalvm.nativeimage.pointsto",
            "org.graalvm.nativeimage.llvm",
            "org.graalvm.nativeimage.foreign",

            // ---- Native Image agents (SVM-hosted) ----
            "org.graalvm.nativeimage.agent.tracing",
            "org.graalvm.nativeimage.agent.jvmtibase",
            "org.graalvm.nativeimage.agent.diagnostics",

            // ---- Truffle ----
            "org.graalvm.truffle.compiler",
            "org.graalvm.truffle.runtime.svm",

            // ---- JDWP server ----
            "com.oracle.svm.jdwp.server");

    /** The actual {@link ClassFileTransformer} registered by the target agent. */
    private ClassFileTransformer target;

    /**
     * When {@code true}, the binary name of each suppressed class is appended to
     * the file specified by {@link #SHADED_DUMP_FILE} for offline verification.
     * Controlled by the system property {@code com.oracle.svm.agentproxy.debug}.
     */
    private static final boolean DEBUG = Boolean
            .parseBoolean(System.getProperty("com.oracle.svm.agentproxy.debug", "false"));

    /**
     * Optional path to a file where the binary names of suppressed classes are
     * appended (one per line) when {@link #DEBUG} is {@code true}. Set via the
     * system property {@code com.oracle.svm.agentproxy.shaded.dump.file}. When the
     * property is absent or empty, no file output is produced.
     */
    private static final String SHADED_DUMP_FILE = System.getProperty("com.oracle.svm.agentproxy.shaded.dump.file", "");

    private ClassFileTransformerProxy(ClassFileTransformer target) {
        this.target = target;
    }

    /**
     * Intercepts both overloads of {@link ClassFileTransformer#transform} and
     * enforces the
     * following policy:
     * <ul>
     * <li>If the class being transformed belongs to a JDK boot module, a JDK
     * platform module,
     * or one of the GraalVM {@link #SYSTEM_MODULES}, the transformation is
     * suppressed
     * (returns {@code null}) to protect the framework from unintended bytecode
     * modifications during the Native Image build.</li>
     * <li>Otherwise the call is forwarded to the actual {@link #target} transformer
     * and its
     * result is returned verbatim.</li>
     * </ul>
     *
     * <p>
     * {@link ClassFileTransformer} defines two {@code transform} overloads:
     * <ol>
     * <li>{@code transform(Module, ClassLoader, String, Class, ProtectionDomain, byte[])}
     * –
     * the first argument is a {@link Module}; class bytes are at index 5.</li>
     * <li>{@code transform(ClassLoader, String, Class, ProtectionDomain, byte[])} –
     * no leading {@link Module} argument; the module is derived from the
     * {@code classBeingRedefined} argument at index 3; class bytes are at index
     * 4.</li>
     * </ol>
     * Both overloads are dispatched here and handled uniformly.
     *
     * @param proxy  the proxy instance the method was invoked on
     * @param method the {@code transform} method being invoked
     * @param args   arguments passed to the method
     * @return the transformed class bytes, or {@code null} if the transformation is
     *         suppressed
     *         or an error occurs
     * @throws Throwable if an unexpected error occurs outside the guarded block
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Module module;
        byte[] originalClass;
        String className;

        // Distinguish the two transform overloads by checking whether the first
        // argument is a Module.
        if (args[0] instanceof Module) {
            // Overload: transform(Module, ClassLoader, String, Class, ProtectionDomain,
            // byte[])
            module = (Module) args[0];
            originalClass = (byte[]) args[5];
            className = (String) args[2];
        } else {
            // Overload: transform(ClassLoader, String, Class, ProtectionDomain, byte[])
            Class<?> classBeingRedefined = (Class<?>) args[3];
            module = classBeingRedefined == null ? null : classBeingRedefined.getModule();
            originalClass = (byte[]) args[4];
            className = (String) args[1];
        }

        String moduleName = module == null ? null : module.getName();
        try {
            if (moduleName != null &&
                    SYSTEM_MODULES.contains(moduleName)) {
                // The class belongs to a protected module; suppress the transformation to
                // prevent
                // target agents from inadvertently modifying GraalVM framework classes.
                if (DEBUG) {
                    // In DEBUG mode, record the suppressed class name for offline verification.
                    recordSuppressedClass(className);
                }
                return null;
            } else {
                // The class is not protected; forward to the actual transformer.
                return (byte[]) method.invoke(target, args);
            }
        } catch (Throwable t) {
            if (DEBUG) {
                t.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Appends the binary name of a suppressed class to {@link #SHADED_DUMP_FILE}
     * (one per line) when the file path is configured. The internal class name
     * (e.g. {@code com/example/Foo}) is converted to binary form
     * (e.g. {@code com.example.Foo}) before writing.
     *
     * @param internalClassName the internal name of the suppressed class
     */
    private static void recordSuppressedClass(String internalClassName) {
        if (SHADED_DUMP_FILE.isEmpty()) {
            return;
        }
        String binaryName = internalClassName.replace("/", ".");
        try {
            Path dumpPath = Paths.get(SHADED_DUMP_FILE);
            Files.writeString(dumpPath, binaryName + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Creates a JDK dynamic proxy for the given {@link ClassFileTransformer}.
     *
     * @param target the actual transformer registered by the target agent
     * @return a proxy that implements {@link ClassFileTransformer} and suppresses
     *         transformations
     *         targeting protected modules
     */
    public static ClassFileTransformer createProxy(ClassFileTransformer target) {
        return (ClassFileTransformer) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[] { ClassFileTransformer.class },
                new ClassFileTransformerProxy(target));
    }
}
