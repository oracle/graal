package com.oracle.svm.agentproxy;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * A Java agent that acts as a proxy to delegate to one or more target agents.
 *
 * <p>
 * This agent is intended to be attached to a JVM and will forward the
 * {@code premain} call to each
 * configured target agent. The target agent's {@code premain} method is
 * expected to be
 * {@code public static}. The {@link Instrumentation} instance passed to each
 * target agent is
 * wrapped via {@link InstrumentationProxy} so that
 * {@link java.lang.instrument.ClassFileTransformer} registrations go through
 * {@link ClassFileTransformerProxy}.
 *
 * <h2>Agent argument format</h2>
 * <p>
 * The {@code agentArgs} parameter supports two forms:
 * <ul>
 * <li>{@code @<file>} – read commands from a file, one per line, each in the
 * form
 * {@code <className>@<jarPath>[:<options>]}.</li>
 * <li>{@code target=<className>@<jarPath>[:<options>][,target=<className>@<jarPath>[:<options>]]…}
 * – specify one or more target agents inline, separated by
 * {@code ,target=}.</li>
 * </ul>
 * <p>
 * In both forms each individual agent entry uses the same syntax:
 * {@code <className>@<jarPath>[:<options>]}, where {@code @<jarPath>} is
 * optional (the class is
 * then looked up via the current class loader).
 */
public class ProxyAgent {

    /**
     * Prefix used in the inline {@code target=} argument form to introduce each
     * agent entry.
     * Multiple agents are separated by {@code ,} followed immediately by this
     * prefix.
     */
    static final String TARGET_PREFIX = "target=";

    /**
     * Describes a single target agent to be invoked.
     *
     * @param premainClass fully-qualified class name of the target agent
     * @param jarPath      absolute path to the agent jar, used to build a dedicated
     *                     {@link URLClassLoader} for loading the agent class; may
     *                     be {@code null}
     *                     when not provided
     * @param opts         option string forwarded to the target agent's
     *                     {@code premain}, may be
     *                     {@code null}
     */
    record AgentCommand(String premainClass, String jarPath, String opts) {
    }

    /**
     * Parses a single agent entry of the form
     * {@code <className>@<jarPath>[:<options>]} and
     * returns the corresponding {@link AgentCommand}.
     *
     * <p>
     * The {@code @<jarPath>} part is optional. When absent,
     * {@link AgentCommand#jarPath()} is
     * {@code null} and the agent class will be resolved via the current class
     * loader. The
     * {@code :<options>} part is also optional; when absent,
     * {@link AgentCommand#opts()} is
     * {@code null}.
     *
     * @param entry a single agent entry string
     * @return the parsed {@link AgentCommand}
     * @throws RuntimeException if the entry is malformed
     */
    static AgentCommand parseAgentCommand(String entry) {
        // Split on '@' to separate <className> from <jarPath>[:<options>]
        int atPos = entry.indexOf('@');
        String className;
        String jarPath = null;
        String opts = null;
        if (atPos == -1) {
            // No jar path: entire entry is the class name (no options either in this form)
            className = entry;
        } else {
            className = entry.substring(0, atPos);
            String rest = entry.substring(atPos + 1);
            // Split <jarPath>[:<options>] on the first ':'
            int colonPos = rest.indexOf(':');
            if (colonPos == -1) {
                jarPath = rest;
            } else {
                jarPath = rest.substring(0, colonPos);
                opts = rest.substring(colonPos + 1);
            }
        }
        return new AgentCommand(className, jarPath, opts);
    }

    /**
     * Agent entry point invoked by the JVM before the application's {@code main}
     * method.
     *
     * <p>
     * Parses {@code agentArgs} to build a list of {@link AgentCommand}s and then
     * invokes the
     * {@code premain} method of each target agent in order.
     *
     * @param agentArgs the agent argument string supplied via {@code -javaagent}
     * @param inst      the {@link Instrumentation} instance provided by the JVM
     * @throws RuntimeException if argument parsing fails, a target class cannot be
     *                          found, or
     *                          invoking a target {@code premain} throws an
     *                          exception
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        List<AgentCommand> targetAgents = new ArrayList<>();

        if (agentArgs.startsWith("@")) {
            // Read target agent commands from an option file, one entry per line.
            // Each line has the form: <className>@<absoluteJarPath>[:<options>]
            String optionFile = agentArgs.substring(1);
            try (BufferedReader br = new BufferedReader(new FileReader(optionFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    targetAgents.add(parseAgentCommand(line));
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot load ProxyAgent because failed to load the specified option file "
                        + optionFile, e);
            }

        } else if (agentArgs.startsWith(TARGET_PREFIX)) {
            // Parse one or more inline target agents.
            // The full string has the form:
            // target=<entry1>[,target=<entry2>[,target=<entry3>]…]
            // Strip the leading "target=" prefix, then split on ",target=" so that every
            // resulting segment is a plain entry: <className>@<jarPath>[:<options>]
            String withoutPrefix = agentArgs.substring(TARGET_PREFIX.length());
            for (String entry : withoutPrefix.split("," + TARGET_PREFIX)) {
                if (!entry.isEmpty()) {
                    targetAgents.add(parseAgentCommand(entry));
                }
            }
            if (targetAgents.isEmpty()) {
                throw new RuntimeException(
                        "Cannot load ProxyAgent because no valid target entries found in: " + agentArgs);
            }
        } else {
            throw new RuntimeException(
                    "Cannot load ProxyAgent: unrecognized agent argument format: \"" + agentArgs + "\". " +
                            "Expected either \"@<optionFile>\" or one or more inline agents in the form " +
                            "\"" + TARGET_PREFIX + "<className>@<jarPath>[:<options>]" +
                            "[," + TARGET_PREFIX + "<className>@<jarPath>[:<options>]]…\".");
        }

        targetAgents.forEach(entry -> {
            String className = entry.premainClass();
            String jarPath = entry.jarPath();
            String opts = entry.opts();

            // Load the target agent class using a dedicated URLClassLoader constructed from
            // the
            // jar path embedded in the command. This keeps the agent jar off the builder
            // JVM's
            // -cp so it does not appear in builderURILocations, which would otherwise
            // trigger a
            // spurious "Class-path entry contains class that is part of the image builder"
            // warning when the same class also exists on the image classpath.
            Class<?> targetAgentClass;
            try {
                ClassLoader classLoader;
                if (jarPath != null && !jarPath.isEmpty()) {
                    URL jarUrl;
                    try {
                        jarUrl = new File(jarPath).toURI().toURL();
                    } catch (MalformedURLException e) {
                        throw new RuntimeException("Invalid target agent jar path: " + jarPath, e);
                    }
                    classLoader = new URLClassLoader(new URL[] { jarUrl }, ProxyAgent.class.getClassLoader());
                } else {
                    classLoader = ProxyAgent.class.getClassLoader();
                }
                targetAgentClass = Class.forName(className, true, classLoader);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Can't find the proxy target class " + className, e);
            }

            // Locate the target agent's premain method. Prefer the two-argument form
            // (String, Instrumentation); fall back to the one-argument form (String) if
            // absent.
            Method premain;
            try {
                premain = targetAgentClass.getDeclaredMethod("premain", String.class, Instrumentation.class);
            } catch (NoSuchMethodException e) {
                try {
                    premain = targetAgentClass.getDeclaredMethod("premain", String.class);
                } catch (NoSuchMethodException e1) {
                    throw new RuntimeException("Can't find premain method in " + targetAgentClass, e1);
                }
            }

            // Build the argument array for the target premain invocation.
            // premain is public static, so no instance is needed (pass null as the
            // receiver).
            // When two arguments are expected, wrap the Instrumentation via
            // InstrumentationProxy
            // so that ClassFileTransformer registrations are intercepted by
            // ClassFileTransformerProxy.
            Object[] targetArgs;
            if (premain.getParameterCount() == 1) {
                targetArgs = new Object[1];
            } else {
                targetArgs = new Object[2];
                targetArgs[1] = InstrumentationProxy.createProxy(inst);
            }
            targetArgs[0] = opts;

            try {
                // Invoke as a static method (null receiver).
                premain.invoke(null, targetArgs);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
