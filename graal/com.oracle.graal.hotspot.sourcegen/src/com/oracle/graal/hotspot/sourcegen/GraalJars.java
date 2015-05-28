package com.oracle.graal.hotspot.sourcegen;

import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;

public class GraalJars implements Iterable<ZipEntry> {
    private final List<ZipFile> jars = new ArrayList<>(2);

    public GraalJars() {
        String classPath = System.getProperty("java.class.path");
        for (String e : classPath.split(File.pathSeparator)) {
            if (e.endsWith(File.separatorChar + "graal.jar") || e.endsWith(File.separatorChar + "graal-truffle.jar")) {
                try {
                    jars.add(new ZipFile(e));
                } catch (IOException ioe) {
                    throw new InternalError(ioe);
                }
            }
        }
        if (jars.size() != 2) {
            throw new InternalError("Could not find graal.jar or graal-truffle.jar on class path: " + classPath);
        }
    }

    public Iterator<ZipEntry> iterator() {
        Stream<ZipEntry> entries = jars.stream().flatMap(ZipFile::stream);
        return entries.iterator();
    }

    public InputStream getInputStream(String classFilePath) throws IOException {
        for (ZipFile jar : jars) {
            ZipEntry entry = jar.getEntry(classFilePath);
            if (entry != null) {
                return jar.getInputStream(entry);
            }
        }
        return null;
    }
}