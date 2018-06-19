package com.oracle.truffle.espresso.runtime;

import java.io.File;

import com.oracle.truffle.espresso.runtime.Classpath.Entry;

/**
 * Encapulates the contents of a file loaded from an {@linkplain Entry entry} on a
 * {@linkplain Classpath classpath}.
 */
public final class ClasspathFile {

    /**
     * The bytes of the file represented by this object.
     */
    public final byte[] contents;

    /**
     * The classpath entry from which the file represented by this object was read.
     */
    public final Entry classpathEntry;

    /**
     * Name of the file relative to {@link #classpathEntry}.
     */
    public final String name;

    /**
     * Creates an object encapsulating the bytes of a file read via a classpath entry.
     *
     * @param contents the bytes of the file that was read
     * @param classpathEntry the entry from which the file was read
     */
    public ClasspathFile(byte[] contents, Entry classpathEntry, String name) {
        this.classpathEntry = classpathEntry;
        this.contents = contents;
        this.name = name;
    }

    @Override
    public String toString() {
        if (classpathEntry.isArchive()) {
            return classpathEntry.file().getAbsolutePath() + '!' + name;
        }
        return classpathEntry.file().getAbsolutePath() + File.separatorChar + name;
    }

    public ClassFormatError classFormatError(String format, Object... args) {
        throw new ClassFormatError(String.format(format, args) + " [in class file " + this + "]");
    }
}
