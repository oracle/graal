#!/bin/sh -e

# Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.

# GraalOS does not support inotify, which is used by LinuxWatchService.
# As such, we substitute LinuxFileSystem to use PollingWatchService instead.
#
# PollingWatchService is part of LabsJDK/OpenJDK sources, but it is omitted
# from the JDK build on Linux (see make/modules/java.base/Java.gmk).
# So we include our own copy in com.oracle.svm.graalos.
#
# Additionally, to minimize the changes that must be made to the sources,
# two other related source files are copied.
#
# This script is never executed by the build system.
# Run it manually when an update is necessary.

[ $# = 0 ] || {
	echo "Usage: [JDK_SOURCE=...] $0" >&2
	exit 1
}

this_dir=`dirname "$0"`
this_file=`basename "$0"`

if test -z "$JDK_SOURCE"
then JDK_SOURCE="$this_dir/../../../../labs-openjdk"
elif test -d "$JDK_SOURCE/open"
then JDK_SOURCE="$JDK_SOURCE/open"
fi
jdk_source_tag=`git -C "$JDK_SOURCE" describe --tags --exact-match --match 'jdk-*'`

for f in PollingWatchService AbstractWatchService AbstractWatchKey
do sed '

# preserve the original JDK license header, change the package, and add copy notices
s|^package sun\.nio\.fs;$|package com.oracle.svm.graalos;\
\
// DO NOT MODIFY!\
\
// Copied from JDK - see the `'"$this_file"'` script for details\
// @formatter:off\
\
import com.oracle.svm.shared.util.BasedOnJDKFile;|

# the ExtendedOptions class is not copied - refer to the original using its @Alias
s/ExtendedOptions/Target_sun_nio_fs_ExtendedOptions_GraalOS/g

# add annotations to all top-level classes (i.e. before non-indented lines containing the word "class")
/^[^ ]/ {
s|.*class \([A-Za-z]*\)|@SuppressWarnings("all")\
@BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/'"$jdk_source_tag"'/src/java.base/share/classes/sun/nio/fs/'"$f"'.java")\
\0|
}

' < "$JDK_SOURCE/src/java.base/share/classes/sun/nio/fs/$f.java" > "$this_dir/src/com/oracle/svm/graalos/$f.java"
done
