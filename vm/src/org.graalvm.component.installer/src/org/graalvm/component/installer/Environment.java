/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

import org.graalvm.component.installer.model.ComponentRegistry;
import java.io.PrintStream;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Supplier;

/**
 * Implementation of feedback and input for commands.
 */
final class Environment implements Feedback, CommandInput {
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(
                    "org.graalvm.component.installer.Bundle");

    private final String commandName;
    private final LinkedList<String> parameters;
    private final Map<String, String> options;
    private final boolean verbose;
    private final ResourceBundle bundle;
    private PrintStream err = System.err;
    private PrintStream out = System.out;
    private Supplier<ComponentRegistry> registrySupplier;
    private ComponentRegistry localRegistry;
    private boolean stacktraces;
    private Iterable<ComponentParam> fileIterable;
    private Map<Path, String> fileMap = new HashMap<>();
    private boolean allOutputToErr;

    private Path graalHome;

    Environment(String commandName, InstallerCommand cmdInstance, List<String> parameters, Map<String, String> options) {
        this.commandName = commandName;
        this.parameters = new LinkedList<>(parameters);
        this.options = options;
        this.verbose = options.containsKey(Commands.OPTION_VERBOSE);
        this.stacktraces = options.containsKey(Commands.OPTION_DEBUG);
        if (cmdInstance != null) {
            String s = cmdInstance.getClass().getName();
            s = s.substring(0, s.lastIndexOf('.'));
            bundle = ResourceBundle.getBundle(s + ".Bundle"); // NOI18N
        } else {
            bundle = BUNDLE;
        }

        this.fileIterable = new FileIterable(this, this);
    }

    public boolean isAllOutputToErr() {
        return allOutputToErr;
    }

    public void setAllOutputToErr(boolean allOutputToErr) {
        this.allOutputToErr = allOutputToErr;
        if (allOutputToErr) {
            out = err;
        } else {
            out = System.out;
        }
    }

    public void setFileIterable(Iterable<ComponentParam> fileIterable) {
        this.fileIterable = fileIterable;
    }

    @Override
    public ComponentRegistry getRegistry() {
        return registrySupplier.get();
    }

    @Override
    public ComponentRegistry getLocalRegistry() {
        return localRegistry;
    }

    public void setLocalRegistry(ComponentRegistry r) {
        this.localRegistry = r;
        if (this.registrySupplier == null) {
            this.registrySupplier = () -> r;
        }
    }

    public void setComponentRegistry(Supplier<ComponentRegistry> registrySupplier) {
        this.registrySupplier = registrySupplier;
    }

    public void setGraalHome(Path f) {
        this.graalHome = f;

    }

    public void setErr(PrintStream err) {
        this.err = err;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    @Override
    public void error(String bundleKey, Throwable error, Object... args) {
        print(false, bundle, err, bundleKey, args);
        if (stacktraces && error != null) {
            error.printStackTrace(err);
        }
    }

    /**
     * Wraps the error into a {@link FailedOperationException}.
     * 
     * @param bundleKey
     * @param error
     * @param args
     * @return exception which can be thrown
     */
    @Override
    public RuntimeException failure(String bundleKey, Throwable error, Object... args) {
        return new FailedOperationException(createMessage(bundle, bundleKey, args), error);
    }

    @Override
    public void message(String bundleKey, Object... args) {
        print(false, bundle, err, bundleKey, args);
    }

    @Override
    public boolean verbosePart(String bundleKey, Object... args) {
        if (bundleKey != null) {
            print(true, false, bundle, out, bundleKey, args);
        }
        return verbose;
    }

    @Override
    public void output(String bundleKey, Object... args) {
        print(false, bundle, out, bundleKey, args);
    }

    @Override
    public void outputPart(String bundleKey, Object... args) {
        print(false, false, bundle, out, bundleKey, args);
    }

    @Override
    public boolean verboseOutput(String bundleKey, Object... args) {
        if (bundleKey != null) {
            print(true, bundle, out, bundleKey, args);
        }
        return verbose;
    }

    @Override
    public String l10n(String bundleKey, Object... args) {
        return createMessage(bundle, bundleKey, args);
    }

    @Override
    public boolean verbatimOut(String msg, boolean beVerbose) {
        print(beVerbose, true, null, out, msg);
        return beVerbose;
    }

    @Override
    public boolean verbatimPart(String msg, boolean beVerbose) {
        print(beVerbose, false, null, out, msg);
        return beVerbose;
    }

    @Override
    public boolean backspace(int chars, boolean beVerbose) {
        if (beVerbose && !verbose) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars; i++) {
            sb.append('\b');
        }
        verbatimPart(sb.toString(), beVerbose);
        return verbose;
    }

    @Override
    public <T> Feedback withBundle(Class<T> clazz) {
        String s = clazz.getName();
        s = s.substring(0, s.lastIndexOf('.'));
        ResourceBundle localBundle = ResourceBundle.getBundle(s + ".Bundle"); // NOI18N

        return new Feedback() {
            @Override
            public void message(String bundleKey, Object... params) {
                print(false, localBundle, err, bundleKey, params);
            }

            @Override
            public void output(String bundleKey, Object... params) {
                print(false, localBundle, out, bundleKey, params);
            }

            @Override
            public boolean verbosePart(String bundleKey, Object... params) {
                if (bundleKey != null) {
                    print(true, false, localBundle, out, bundleKey, params);
                }
                return verbose;
            }

            @Override
            public boolean verboseOutput(String bundleKey, Object... params) {
                if (bundleKey != null) {
                    print(true, localBundle, out, bundleKey, params);
                }
                return verbose;
            }

            @Override
            public boolean verbatimOut(String msg, boolean verboseOutput) {
                print(verboseOutput, null, out, msg);
                return verboseOutput;
            }

            @Override
            public void error(String key, Throwable t, Object... params) {
                print(false, localBundle, err, key, params);
                if (stacktraces && t != null) {
                    t.printStackTrace(err);
                }
            }

            @Override
            public String l10n(String key, Object... params) {
                return createMessage(localBundle, key, params);
            }

            @Override
            public RuntimeException failure(String key, Throwable t, Object... params) {
                return new FailedOperationException(createMessage(localBundle, key, params), t);
            }

            @Override
            public <X> Feedback withBundle(Class<X> anotherClazz) {
                return Environment.this.withBundle(anotherClazz);
            }

            @Override
            public void outputPart(String bundleKey, Object... params) {
                print(false, false, localBundle, out, bundleKey, params);
            }

            @Override
            public boolean verbatimPart(String msg, boolean verboseOutput) {
                print(verboseOutput, false, null, out, msg);
                return verbose;
            }

            @Override
            public boolean backspace(int chars, boolean beVerbose) {
                return Environment.this.backspace(chars, beVerbose);
            }

            @Override
            public String translateFilename(Path f) {
                return Environment.this.translateFilename(f);
            }

            @Override
            public void bindFilename(Path file, String label) {
                Environment.this.bindFilename(file, label);
            }
        };
    }

    @SuppressWarnings({"unchecked"})
    private static String createMessage(ResourceBundle bundle, String bundleKey, Object... args) {
        if (bundle == null) {
            return bundleKey;
        }
        if (args == null) {
            return bundle.getString(bundleKey);
        }
        for (int i = 0; i < args.length; i++) {
            Object o = args[i];
            if (o instanceof Supplier) {
                Object v = ((Supplier<Object>) o).get();
                if (v == null || v instanceof String) {
                    args[i] = v;
                }
            }
        }
        return MessageFormat.format(
                        bundle.getString(bundleKey),
                        args);
    }

    private void print(boolean beVerbose, ResourceBundle msgBundle, PrintStream stm, String bundleKey, Object... args) {
        if (beVerbose && !this.verbose) {
            return;
        }
        print(beVerbose, true, msgBundle, stm, bundleKey, args);
    }

    private void print(boolean beVerbose, boolean addNewline, ResourceBundle msgBundle, PrintStream stm, String bundleKey, Object... args) {
        if (beVerbose && !this.verbose) {
            return;
        }
        if (addNewline) {
            stm.println(createMessage(msgBundle, bundleKey, args));
            stm.flush();
        } else {
            stm.print(createMessage(msgBundle, bundleKey, args));
            stm.flush();
        }
    }

    @Override
    public Path getGraalHomePath() {
        return graalHome;
    }

    @Override
    public String nextParameter() {
        return parameters.poll();
    }

    @Override
    public String requiredParameter() {
        if (parameters.isEmpty()) {
            throw new FailedOperationException(
                            MessageFormat.format(BUNDLE.getString("ERROR_MissingParameter"), commandName));
        }
        return nextParameter();
    }

    @Override
    public boolean hasParameter() {
        return !parameters.isEmpty();
    }

    @Override
    public Iterable<ComponentParam> existingFiles() {
        return fileIterable;
    }

    @Override
    public String optValue(String optName) {
        return options.get(optName);
    }

    @Override
    public String translateFilename(Path f) {
        return fileMap.getOrDefault(f, f.toString());
    }

    @Override
    public void bindFilename(Path file, String label) {
        fileMap.put(file, label);
    }
}
