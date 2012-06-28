/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.criutils;

import java.io.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;

/**
 * A profiling info snapshot that can be {@linkplain #save(File, File) saved} to
 * and {@linkplain #load(File, CodeCacheProvider) loaded} from a file.
 */
public class SnapshotProfilingInfo implements ProfilingInfo, Serializable {

    private static final long serialVersionUID = -5837615128782960391L;
    private final double[] branchTaken;
    private final double[][] switches;
    private final JavaTypeProfile[] typeProfiles;
    private final ExceptionSeen[] exceptions;
    private final int[] executions;
    private final int[] deopts;

    public SnapshotProfilingInfo(ProfilingInfo other) {
        int codeSize = other.codeSize();
        branchTaken = new double[codeSize];
        switches = new double[codeSize][];
        typeProfiles = new JavaTypeProfile[codeSize];
        exceptions = new ExceptionSeen[codeSize];
        executions = new int[codeSize];
        deopts = new int[DeoptimizationReason.values().length];

        for (int bci = 0; bci < codeSize; bci++) {
            executions[bci] = other.getExecutionCount(bci);
            exceptions[bci] = other.getExceptionSeen(bci);
            branchTaken[bci] = other.getBranchTakenProbability(bci);
            switches[bci] = other.getSwitchProbabilities(bci);
            typeProfiles[bci] = other.getTypeProfile(bci);
        }
        for (DeoptimizationReason reason: DeoptimizationReason.values()) {
            deopts[reason.ordinal()] = other.getDeoptimizationCount(reason);
        }
    }

    @Override
    public int codeSize() {
        return branchTaken.length;
    }

    public double getBranchTakenProbability(int bci) {
        return bci < branchTaken.length ? branchTaken[bci] : -1D;
    }
    public double[] getSwitchProbabilities(int bci) {
        return bci < switches.length ? switches[bci] : null;
    }
    public JavaTypeProfile getTypeProfile(int bci) {
        return bci < typeProfiles.length ? typeProfiles[bci] : null;
    }
    public ExceptionSeen getExceptionSeen(int bci) {
        return bci < exceptions.length ? exceptions[bci] : ExceptionSeen.NOT_SUPPORTED;
    }
    public int getExecutionCount(int bci) {
        return bci < executions.length ? executions[bci] : -1;
    }
    public int getDeoptimizationCount(DeoptimizationReason reason) {
        return deopts[reason.ordinal()];
    }

    @Override
    public String toString() {
        return CodeUtil.profileToString(this, null, "; ");
    }

    /**
     * Deserializes a profile snapshot from a file.
     *
     * @param file a file created by {@link #save(File, File)}
     * @param runtime the runtime used to resolve {@link ResolvedJavaType}s during deserialization
     * @throws ClassNotFoundException
     * @throws IOException
     */
    public static SnapshotProfilingInfo load(File file, CodeCacheProvider runtime) throws ClassNotFoundException, IOException {
        SnapshotProfilingInfo.SnapshotObjectInputStream ois = new SnapshotObjectInputStream(new BufferedInputStream(new FileInputStream(file), (int) file.length()), runtime);
        try {
            return (SnapshotProfilingInfo) ois.readObject();
        } finally {
            ois.close();
        }
    }

    /**
     * Serializes this snapshot to a file.
     *
     * @param file the file to which this snapshot is serialized
     * @param txtFile
     * @throws IOException
     */
    public void save(File file, File txtFile) throws IOException {
        SnapshotProfilingInfo.SnapshotObjectOutputStream oos = new SnapshotObjectOutputStream(new FileOutputStream(file));
        try {
            oos.writeObject(this);
        } finally {
            oos.close();
        }
        if (txtFile != null) {
            PrintStream out = new PrintStream(txtFile);
            try {
                out.println(CodeUtil.profileToString(this, null, CodeUtil.NEW_LINE));
            } finally {
                out.close();
            }
        }
    }

    static class RiResolvedTypePlaceholder implements Serializable {
        private static final long serialVersionUID = -5149982457010023916L;
        final Class javaMirror;
        public RiResolvedTypePlaceholder(Class javaMirror) {
            this.javaMirror = javaMirror;
        }
    }

    static class SnapshotObjectOutputStream extends ObjectOutputStream {
        public SnapshotObjectOutputStream(OutputStream out) throws IOException {
            super(out);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object obj) throws IOException {
            if (obj instanceof ResolvedJavaType) {
                return new RiResolvedTypePlaceholder(((ResolvedJavaType) obj).toJava());
            }
            return obj;
        }
    }

    static class SnapshotObjectInputStream extends ObjectInputStream {
        private final CodeCacheProvider runtime;
        public SnapshotObjectInputStream(InputStream in, CodeCacheProvider runtime) throws IOException {
            super(in);
            enableResolveObject(true);
            this.runtime = runtime;
        }

        @Override
        protected Object resolveObject(Object obj) throws IOException {
            if (obj instanceof SnapshotProfilingInfo.RiResolvedTypePlaceholder) {
                ResolvedJavaType type = runtime.getResolvedJavaType(((SnapshotProfilingInfo.RiResolvedTypePlaceholder) obj).javaMirror);
                return type;
            }
            return obj;
        }
    }
}
