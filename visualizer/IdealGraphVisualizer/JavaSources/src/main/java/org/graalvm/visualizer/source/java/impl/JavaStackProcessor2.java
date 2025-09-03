/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.source.java.impl;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NODE_SOURCE_POSITION;

import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.visualizer.source.FileKey;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.ProcessorContext;
import org.graalvm.visualizer.source.spi.StackProcessor;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.queries.SourceForBinaryQuery;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

import jdk.graal.compiler.graphio.parsing.LocationStackFrame;
import jdk.graal.compiler.graphio.parsing.LocationStratum;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.graphio.parsing.model.Properties;

/**
 *
 */
public class JavaStackProcessor2 implements StackProcessor {
    private static final String CLASS_EXTENSION_SUFFIX = ".class";
    private static final String JAVA_EXTENSION_SUFFIX = ".java";
    private static final Pattern FRAME_PATTERN = Pattern.compile("(?:at )?(.*)\\((.*):([0-9]+)\\)");

    private ProcessorContext ctx;

    private final Map<String, Object> resolveCache = new HashMap<>();

    private static final Object UNRESOLVED = "unresolved";

    private ClassPath proxySource;
    private ClassPath proxyCompile;

    public JavaStackProcessor2() {
    }

    @Override
    public void attach(ProcessorContext ctx) {
        this.ctx = ctx;
        // initialize classpaths
        Set<FileObject> roots = GlobalPathRegistry.getDefault().getSourceRoots();
        proxySource = ClassPathSupport.createClassPath(roots.toArray(new FileObject[roots.size()]));

        Set<ClassPath> compilePaths = GlobalPathRegistry.getDefault().getPaths(ClassPath.COMPILE);
        proxyCompile = ClassPathSupport.createProxyClassPath(compilePaths.toArray(new ClassPath[compilePaths.size()]));
    }

    @Override
    public List<Location> processStack(InputNode node) {
        Properties props = node.getProperties();
        String s = props.getString(PROPNAME_NODE_SOURCE_POSITION, null);
        if (s == null) {
            return null;
        }
        Object o = props.get(PROPNAME_NODE_SOURCE_POSITION, Object.class);
        LocationStackFrame sf = null;

        if (o instanceof LocationStackFrame) {
            sf = (LocationStackFrame) o;
        }
        return new T(node, s, sf).process();
    }

    private class T {
        private final InputNode node;
        private final String stackTrace;
        private final LocationStackFrame stackTop;
        private LocationStackFrame stackFrame;

        private final List<Location> result = new ArrayList<>();

        private String methodName;
        private String className;
        private String spec;
        private int lineno;
        private int depth;

        public T(InputNode node, String stackTrace, LocationStackFrame stackTop) {
            this.stackTrace = stackTrace;
            this.node = node;
            this.stackTop = stackTop;
            stackFrame = stackTop;
        }

        private void reset() {
            spec = null;
            methodName = null;
            className = null;
            lineno = -1;
        }

        private LocationStratum findStratum(LocationStackFrame se) {
            for (LocationStratum stratum : se.getStrata()) {
                if ("Java".equals(stratum.language)) {
                    return stratum;
                }
            }
            return null;
        }

        private void addLineLocation(String frameString) {
            Matcher m = null;
            if (stackFrame == null) {
                m = FRAME_PATTERN.matcher(frameString);
                if (!m.find()) {
                    return;
                }
            }
            String methodSpec;
            String filename;

            if (stackFrame != null) {
                methodSpec = stackFrame.getFullMethodName();
                LocationStratum stratum = findStratum(stackFrame);
                if (stratum == null) {
                    return;
                }
                filename = stratum.uri != null ? stratum.uri : stratum.file;
                lineno = stratum.line;
                if (filename == null) {
                    return;
                }
            } else {
                String lineString;
                methodSpec = m.group(1);
                filename = m.group(2);
                lineString = m.group(3);
                lineno = Integer.parseInt(lineString);
            }
            String basename = null;

            if (filename.endsWith(JAVA_EXTENSION_SUFFIX)) {
                basename = filename.substring(0, filename.length() - 5);
            }
//            assert filename.endsWith(JAVA_EXTENSION_SUFFIX); // NOI18N
            this.spec = frameString.substring(3);

            int methodDot = methodSpec.lastIndexOf('.');
            if (methodDot == -1) {
                return;
            }
            String fqn = methodSpec.substring(0, methodDot);
            this.methodName = methodSpec.substring(methodDot + 1);
            this.className = fqn;

            int $Pos = fqn.indexOf('$'); // NOI18N
            String outername = fqn;
            if ($Pos > 0) {
                outername = fqn.substring(0, $Pos);
            }
            int dotPos = outername.lastIndexOf('.'); // NOI18N
            if (basename == null) {
                basename = dotPos > 0 ? outername.substring(dotPos + 1) : outername;
            }
            String resource;
            // strip the class name and substitute filename - handles toplevel package private classes.
            if (dotPos == -1) {
                resource = basename.replace('.', '/');
            } else {
                resource = outername.substring(0, dotPos + 1).replace('.', '/') + basename;
            }

            String javaSource = resource + JAVA_EXTENSION_SUFFIX;

            Object o = resolveCache.get(javaSource);
            if (o instanceof FileObject) {
                addResolvedLocation(javaSource, (FileObject) o);
                return;
            } else if (o != null) {
                addResolvedLocation(javaSource, null);
                return;
            }
            FileObject src = proxySource.findResource(javaSource);

            if (src != null) {
                addResolvedLocation(javaSource, src);
                return;
            }

            // second try: try to find a class, then get its source: this will work also for libraries with sources
            FileObject clazz = proxyCompile.findResource(resource + CLASS_EXTENSION_SUFFIX);
            if (clazz != null) {
                FileObject ownerRoot = proxyCompile.findOwnerRoot(clazz);
                URL ownerURL = URLMapper.findURL(ownerRoot, URLMapper.INTERNAL);
                if (ownerURL != null) {
                    SourceForBinaryQuery.Result2 res = SourceForBinaryQuery.findSourceRoots2(ownerURL);
                    proxySource = ClassPathSupport.createClassPath(res.getRoots());
                    src = proxySource.findResource(javaSource);
                    if (src != null) {
                        addResolvedLocation(javaSource, src);
                        return;
                    }
                }
            }

            // we have an unresolved file
            addResolvedLocation(javaSource, null);
        }

        @NbBundle.Messages({
                "# adds HTML tags to method name",
                "# {0} - class name",
                "# {1} - metod name",
                "HtmlFormat_MethodName={0}.<b>{1}()</b>",
                "# Adds decoration to unresolved locations",
                "# {0} - the formatted name",
                "HtmlFormat_Unresolved=<strike>{0}</strike>"
        })
        private void addResolvedLocation(String filename, FileObject source) {
            resolveCache.putIfAbsent(filename, source == null ? UNRESOLVED : source);
            Location lastloc = null;
            JavaLocationInfo prevInfo = null;
            if (!result.isEmpty()) {
                // get method name info from the nested call, if any
                lastloc = result.get(0);
                prevInfo = lastloc.getSpecificInfo(JavaLocationInfo.class);
            }
            JavaLocationInfo javaInfo = new JavaLocationInfo(-1, this.className, this.methodName, null);
            if (prevInfo != null) {
                javaInfo.callToMethod(prevInfo.getClassName(), prevInfo.getMethodName());
            } else {
                // possibly a field access ?
                String fieldRef = node.getProperties().getString("field", null); // NOI18N
                String methodRef = node.getProperties().getString("targetMethod", null); // NOI18N

                if (fieldRef != null) {
                    int dot = fieldRef.lastIndexOf('.'); // NOI18N
                    if (dot != -1) {
                        javaInfo.referToField(fieldRef.substring(0, dot), fieldRef.substring(dot + 1));
                    }
                } else if (methodRef != null) {
                    int dot = methodRef.lastIndexOf('.'); // NOI18N
                    if (dot != -1) {
                        javaInfo.callToMethod(methodRef.substring(0, dot), methodRef.substring(dot + 1));
                    }
                }
            }

            FileKey fk = ctx.file(filename, source);
            Location newLoc = new Location(spec, fk, lineno, -1, -1, lastloc, depth, -1);
            ctx.attachInfo(newLoc, javaInfo);
            javaInfo.withLocation(newLoc);
            result.add(newLoc);
        }

        public List<Location> process() {
            String[] lines = stackTrace.split("\n");
            int i = 0;
            while (stackFrame != null || i < lines.length) {
                addLineLocation(lines[i]);
                if (stackFrame != null) {
                    stackFrame = stackFrame.getParent();
                }
                i++;
                depth++;
                reset();
            }
            return result;
        }
    }

    @ServiceProvider(service = StackProcessor.Factory.class, position = 10000)
    public final static class Factory implements StackProcessor.Factory {
        @Override
        public String[] getLanguageIDs() {
            return new String[]{"text/x-java"}; // NOI18N
        }

        @Override
        public StackProcessor createProcessor(ProcessorContext ctx) {
            return new JavaStackProcessor2();
        }
    }
}
