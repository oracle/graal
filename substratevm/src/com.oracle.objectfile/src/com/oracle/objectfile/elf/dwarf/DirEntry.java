package com.oracle.objectfile.elf.dwarf;

// files may be located in a source directory associated
// with a well known substratevm or compiler root package
// in that case the the file's directory path will be something
// like "foo.bar.baz/src/foo/bar/baz/mumble/grumble/bletch"
// i.e. the root package and "src" will be inserted as a prefix
// before the dirs derived from the actual package
// files whose package does not match a well-known root package
// will be listed using the dirs derived from the package
// i.e. simply  "foo/bar/baz/mumble/grumble/bletch"

public class DirEntry
{
    private String path;

    // create an entry for a root package path
    // or a user path not under a root package
    public DirEntry(String path) {
        this.path = path;
    }

    public String getPath()
    {
        return path;
    }
}

