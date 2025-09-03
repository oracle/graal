/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.runtime;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Level;

import org.graalvm.options.OptionMap;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ModuleTable;
import com.oracle.truffle.espresso.impl.SuppressFBWarnings;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.redefinition.RedefinitionException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.standard.Target_sun_instrument_InstrumentationImpl;

public final class JavaAgents extends ContextAccessImpl {
    @CompilationFinal(dimensions = 1) private JavaAgent[] agents;
    private boolean hasTransformers = false;
    private boolean hasReTransformers = false;
    private boolean hasNativePrefixes = false;

    private JavaAgents(EspressoContext context, List<String> agentOptions) {
        super(context);
        agents = initializeAgents(agentOptions);
    }

    @TruffleBoundary
    public static JavaAgents createJavaAgents(EspressoContext context, OptionMap<String> agents) {
        List<String> sortedOptions = new ArrayList<>();
        int expectedSize = agents.entrySet().size();
        // we need to preserve the order of agents, so we have to follow the numbered keys
        int expectedIndex = 0;
        while (expectedIndex < expectedSize) {
            String agentArgument = agents.get(Integer.toString(expectedIndex++));
            if (agentArgument == null) {
                // unexpected failure to sort the agent options, so let's add debug output
                context.getLogger().warning("unexpected agent options discovered");
                for (Map.Entry<String, String> entry : agents.entrySet()) {
                    context.getLogger().warning("agent entry: " + entry.getKey() + " -> " + entry.getValue());
                }
                throw context.abort("error while parsing Java agent options");
            }
            sortedOptions.add(agentArgument);
        }
        return new JavaAgents(context, sortedOptions);
    }

    private JavaAgent[] initializeAgents(List<String> agentOptions) {
        List<JavaAgent> addedAgents = new ArrayList<>();
        for (String entry : agentOptions) {
            String[] agentOptionPair = entry.split("=", 2);
            if (agentOptionPair.length == 2) {
                onLoad(agentOptionPair[0], agentOptionPair[1], addedAgents);
            } else {
                onLoad(entry, "", addedAgents);
            }
        }
        return addedAgents.toArray(new JavaAgent[0]);
    }

    private void onLoad(String javaAgentName, String agentOptions, List<JavaAgent> addedAgents) {
        Path jarPath = Paths.get(javaAgentName);
        try {
            JavaAgent javaAgent = addAgent(jarPath, agentOptions);
            if (javaAgent != null) {
                addedAgents.add(javaAgent);
            }
        } catch (IOException e) {
            throw getContext().abort("Error opening zip file or JAR manifest missing : " + jarPath + " due to: " + e.getMessage());
        }
    }

    @TruffleBoundary
    private JavaAgent addAgent(Path jarPath, String agentOptions) throws IOException {
        /*
         * For each Java agent, the jarfile manifest is parsed and the value of the Premain-Class
         * attribute will become the agent's premain class. The jar file is then added to the system
         * class path, and if the Boot-Class-Path attribute is present then all relative URLs in the
         * value are processed to create boot class path segments to append to the boot class path.
         */
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Attributes mainAttributes = jarFile.getManifest().getMainAttributes();

            // Can-Redefine-Classes
            boolean canRedefine = "true".equals(mainAttributes.getValue("Can-Redefine-Classes"));
            // Can-Retransform-Classes
            boolean canRetransform = "true".equals(mainAttributes.getValue("Can-Retransform-Classes"));
            // Can-Set-Native-Method-Prefix
            boolean canSetNativePrefix = "true".equals(mainAttributes.getValue("Can-Set-Native-Method-Prefix"));

            // Premain-Class
            String preMainClass = mainAttributes.getValue("Premain-Class");
            if (preMainClass == null) {
                throw getContext().abort("Failed to find Premain-Class manifest attribute in " + jarFile.getName());
            }
            // Boot-Class-Path
            String appendToBootClassPath = mainAttributes.getValue("Boot-Class-Path");
            // append to the boot classpath if attribute is set
            if (appendToBootClassPath != null) {
                appendToBootClassPath(appendToBootClassPath, jarPath);
            }
            return new JavaAgent(jarPath, agentOptions, preMainClass, canRedefine, canRetransform, canSetNativePrefix);
        }
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "agentJarPath passed always has a parent path.")
    private void appendToBootClassPath(String bootClasspathManifestLine, Path agentJarPath) {
        // parse the line which is a space-separated list of paths where we have to check the
        // validity of each path in turn. We do the validation by means of the logic of URIs.
        String[] paths = bootClasspathManifestLine.split(" +");
        for (String path : paths) {
            // The attribute is specified to be a list of relative URIs so in theory
            // there could be a query component - if so, get rid of it.
            String strippedPath = path;
            int queryIndex = path.indexOf('?');
            if (queryIndex != -1) {
                strippedPath = path.substring(0, queryIndex);
            }
            try {
                URI resolvedURI;
                if (strippedPath.startsWith("/")) {
                    // spec yields this is an absolute path
                    resolvedURI = new URI("file", null, strippedPath, null);
                } else {
                    // resolve against the agent jar directory according to the spec
                    resolvedURI = agentJarPath.getParent().toUri().resolve(strippedPath);
                }
                Path validPath = Path.of(resolvedURI);
                // OK, validation passed. Then append a classpath entry
                getContext().appendBootClasspath(Classpath.createEntry(validPath.toString()));
            } catch (IllegalArgumentException | URISyntaxException e) {
                // URI was not valid, so ignore this path. Print a warning though
                getContext().getLogger().warning("illegal character or invalid path in Boot-Class-Path value: " + path + " in agent: " + agentJarPath);
            } catch (FileSystemNotFoundException | SecurityException e) {
                // OK, we can't append the path
                getContext().getLogger().log(Level.WARNING, "Cannot append: " + path + " from Boot-Class-Path attribute in agent" + agentJarPath, e);
            }
        }
    }

    @TruffleBoundary
    public void startJavaAgents() {
        if (getMeta().sun_instrument_InstrumentationImpl == null) {
            getContext().getLogger().warning("Agent instrumentation is not available in this environment, so no agents will be started.");
            return;
        }
        for (int i = 0; i < agents.length; i++) {
            JavaAgent initializedAgent = agents[i];
            // append the agent jar to the system classpath
            String jarName = initializedAgent.getJarPath().toString();
            Target_sun_instrument_InstrumentationImpl.appendPathToSystemClassLoader(getMeta().toGuestString(jarName), getMeta());

            // create the instrumentation instance
            StaticObject guestInstrument = getAllocator().createNew(getMeta().sun_instrument_InstrumentationImpl);
            if (getContext().getJavaVersion().java9OrLater()) {
                getMeta().sun_instrument_InstrumentationImpl_init.invokeDirectSpecial(
                                guestInstrument,
                                /* agent ID */ (long) i,
                                initializedAgent.canRedefine,
                                initializedAgent.canSetNativePrefix,
                                /* print warning */ false);
            } else {
                getMeta().sun_instrument_InstrumentationImpl_init.invokeDirectSpecial(
                                guestInstrument,
                                /* agent ID */ (long) i,
                                initializedAgent.canRedefine,
                                initializedAgent.canSetNativePrefix);
            }
            initializedAgent.setInstrumentation(guestInstrument);
        }
        // Now we can invoke the helper method in the instrument implementation
        // that invokes the agent premain method for each initialized agent.
        for (JavaAgent agent : agents) {
            try {
                getMeta().sun_instrument_InstrumentationImpl_loadClassAndCallPremain.invokeDirectSpecial(
                                agent.instrumentation,
                                getMeta().toGuestString(agent.preMainClass),
                                getMeta().toGuestString(agent.agentOptions));
            } catch (EspressoException e) {
                // print the guest stack trace
                getMeta().java_lang_Throwable_printStackTrace.invokeDirectVirtual(e.getGuestException());
                throw getContext().abort("javaagent: " + agent.getJarPath() + " failed during premain");
            }
        }
    }

    @TruffleBoundary
    public String jarFile(int agentId) {
        assert agents[agentId] != null;
        return agents[agentId].jarPath.toString();
    }

    public void setHasTransformers(int agentId, boolean has) {
        agents[agentId].setHasTransformer(has);
        if (has) {
            hasTransformers = true;
        } else {
            // slow check if any agent still has transformer
            // if not, clear the "global" hasTransformers
            boolean anyAgentHasTransformers = false;
            for (JavaAgent agent : agents) {
                if (agent.hasTransformer) {
                    anyAgentHasTransformers = true;
                    break;
                }
            }
            if (!anyAgentHasTransformers) {
                hasTransformers = false;
            }
        }
    }

    public boolean hasTransformers() {
        return hasTransformers || hasReTransformers;
    }

    public void setHasRetransformers(int agentId, boolean has) {
        agents[agentId].setHasRetransformer(has);
        if (has) {
            hasReTransformers = true;
        } else {
            // slow check if any agent still has reTransformer
            // if not, clear the "global" hasReTransformers
            boolean anyAgentHasReTransformers = false;
            for (JavaAgent agent : agents) {
                if (agent.hasReTransformer) {
                    anyAgentHasReTransformers = true;
                    break;
                }
            }
            if (!anyAgentHasReTransformers) {
                hasReTransformers = false;
            }
        }
    }

    public boolean isRetransformSupported(int agentId) {
        return agents[agentId].canRetransform;
    }

    public void retransformClasses(Klass[] klasses) throws RedefinitionException {
        RedefineInfo[] redefineInfos = new RedefineInfo[klasses.length];
        for (int i = 0; i < klasses.length; i++) {
            Klass klass = klasses[i];
            redefineInfos[i] = new RedefineInfo(klass, retransformClass(klass));
        }
        // install the new bytes by redefinition
        getContext().getClassRedefinition().redefineClasses(redefineInfos, false);
    }

    private byte[] retransformClass(Klass klass) {
        byte[] retransformBytes = getContext().getRegistries().getClassRegistry(klass.getDefiningClassLoader()).getRetransformBytes(klass);
        return applyRetransformers(klass.mirror(), klass.module().module(), klass.getDefiningClassLoader(), klass.getType(), klass.protectionDomain(), retransformBytes);
    }

    public byte[] transformClass(Klass klass, byte[] bytes) {
        byte[] retransformBytes = applyTransformers(klass.mirror(), klass.module().module(), klass.getDefiningClassLoader(), klass.getType(), klass.protectionDomain(), bytes);
        return applyRetransformers(klass.mirror(), klass.module().module(), klass.getDefiningClassLoader(), klass.getType(), klass.protectionDomain(), retransformBytes);
    }

    public byte[] applyTransformers(
                    StaticObject classBeingRedefined,
                    StaticObject module,
                    StaticObject loader,
                    Symbol<Type> typeOrNull,
                    StaticObject protectionDomain,
                    byte[] bytes) {
        if (!hasTransformers) {
            // either no transformers added by agents, or we're in the bootstrap process
            return bytes;
        }
        // get the guest class internal class name once outside the loop
        StaticObject className = typeOrNull == null ? StaticObject.NULL : getMeta().toGuestString(TypeSymbols.typeToName(typeOrNull));
        StaticObject wrappedBytes;

        wrappedBytes = StaticObject.wrap(bytes, getMeta());
        for (JavaAgent agent : agents) {
            // short-circuit if no transformers registered with this agent - no reason to call
            // into the guest
            if (agent.hasTransformer) {
                StaticObject result = agent.transformClass(getMeta(), classBeingRedefined, module, loader, className, protectionDomain, wrappedBytes, false);
                if (StaticObject.notNull(result)) {
                    wrappedBytes = result;
                }
            }
        }
        return wrappedBytes.unwrap(getLanguage());
    }

    public byte[] applyRetransformers(
                    StaticObject classBeingRedefined,
                    StaticObject module,
                    StaticObject loader,
                    Symbol<Type> typeOrNull,
                    StaticObject protectionDomain,
                    byte[] bytes) {
        if (!hasReTransformers) {
            // either no retransformers added by agents, or we're in the bootstrap process
            return bytes;
        }

        // get the guest class internal class name once outside the loop
        StaticObject className = typeOrNull == null ? StaticObject.NULL : getMeta().toGuestString(TypeSymbols.typeToName(typeOrNull));
        StaticObject wrappedBytes = StaticObject.wrap(bytes, getMeta());
        // then transformation applies all retransformation capable transformers
        for (JavaAgent agent : agents) {
            // short-circuit if no retransformers registered with this agent - no reason to call
            // into the guest
            if (agent.hasReTransformer) {
                StaticObject result = agent.transformClass(getMeta(), classBeingRedefined, module, loader, className, protectionDomain, wrappedBytes, true);
                if (StaticObject.notNull(result)) {
                    wrappedBytes = result;
                }
            }
        }
        return wrappedBytes.unwrap(getLanguage());
    }

    public void setNativePrefixes(int agentId, Symbol<Name>[] prefixes) {
        agents[agentId].nativePrefixes = prefixes;
        hasNativePrefixes = true;
    }

    @TruffleBoundary
    public Symbol<Name> resolveMethodNameFromPrefixes(Symbol<Name> originalName) {
        if (!hasNativePrefixes) {
            return originalName;
        }
        ByteSequence resolvedName = originalName;
        // starting from the original method name which might include one or more prefixes,
        // strip the prefix in order, for each agent, if present
        Symbol<Name>[] allNativePrefixes = getAllNativePrefixes();
        for (int i = allNativePrefixes.length - 1; i >= 0; i--) {
            Symbol<Name> prefix = allNativePrefixes[i];
            if (resolvedName.contentStartsWith(prefix)) {
                resolvedName = resolvedName.subSequence(prefix.length(), resolvedName.length());
            }
        }
        return getContext().getNames().getOrCreate(resolvedName);
    }

    @TruffleBoundary
    @SuppressWarnings({"unchecked"})
    public Symbol<Name>[] getAllNativePrefixes() {
        if (!hasNativePrefixes) {
            return Symbol.EMPTY_ARRAY;
        }
        ArrayList<Symbol<Name>> prefixes = new ArrayList<>();
        for (JavaAgent agent : agents) {
            prefixes.addAll(Arrays.asList(agent.nativePrefixes));
        }
        return prefixes.toArray(Symbol.EMPTY_ARRAY);
    }

    public boolean hasNativePrefixes() {
        return hasNativePrefixes;
    }

    public boolean shouldEnableRedefinition() {
        // check if at least one agent can redefine or retransform
        for (JavaAgent agent : agents) {
            if (agent.canRedefine || agent.canRetransform) {
                return true;
            }
        }
        return false;
    }

    public void grantReadAccessToUnnamedModules(ModuleTable.ModuleEntry module) {
        if (module != null && module.isNamed() && !module.hasDefaultReads()) {
            synchronized (module) {
                if (module.hasDefaultReads()) {
                    // we lost the race, so nothing to do
                    return;
                }
                getContext().getMeta().jdk_internal_module_Modules_transformedByAgent.invokeDirectStatic(module.module());
                // no reason to call into guest for a module more than once,
                // so flip the hasDefaultReads flag
                module.setHasDefaultReads();
            }
        }
    }

    private static final class JavaAgent {
        private final Path jarPath;
        private final String agentOptions;
        private final String preMainClass;
        private final boolean canRedefine;
        private final boolean canRetransform;
        private final boolean canSetNativePrefix;
        private boolean hasTransformer;
        private boolean hasReTransformer;
        private StaticObject instrumentation;
        @SuppressWarnings({"unchecked"}) private Symbol<Name>[] nativePrefixes = Symbol.EMPTY_ARRAY;

        private JavaAgent(Path jarPath, String agentOptions, String preMainClass, boolean canRedefine, boolean canRetransform, boolean canSetNativePrefix) {
            this.jarPath = jarPath;
            this.agentOptions = agentOptions;
            this.preMainClass = preMainClass;
            this.canRedefine = canRedefine;
            this.canRetransform = canRetransform;
            this.canSetNativePrefix = canSetNativePrefix;
        }

        public Path getJarPath() {
            return jarPath;
        }

        public void setInstrumentation(StaticObject instrument) {
            this.instrumentation = instrument;
        }

        public void setHasTransformer(boolean has) {
            this.hasTransformer = has;
        }

        public void setHasRetransformer(boolean has) {
            this.hasReTransformer = has;
        }

        public StaticObject transformClass(Meta meta, StaticObject classBeingRedefined, StaticObject module, StaticObject loader, @JavaType(String.class) StaticObject className,
                        StaticObject protectionDomain, StaticObject bytes, boolean isRetransformer) {
            if (meta.getContext().getJavaVersion().java9OrLater()) {
                return (StaticObject) meta.sun_instrument_InstrumentationImpl_transform.invokeDirectSpecial(
                                instrumentation,
                                module,
                                loader,
                                className,
                                classBeingRedefined,
                                protectionDomain,
                                bytes,
                                isRetransformer);
            } else { // no modules in JDK 8
                return (StaticObject) meta.sun_instrument_InstrumentationImpl_transform.invokeDirectSpecial(
                                instrumentation,
                                loader,
                                className,
                                classBeingRedefined,
                                protectionDomain,
                                bytes,
                                isRetransformer);
            }
        }
    }
}
