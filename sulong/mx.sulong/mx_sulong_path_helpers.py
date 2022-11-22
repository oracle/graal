#
# Copyright (c) 2022, Oracle and/or its affiliates.
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors may be used to
# endorse or promote products derived from this software without specific prior written
# permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
#

import mx
import os
import shutil

def ensure_dirs(path, *paths):
    """
    Join paths to path using os.path.join and then ensures that the
    resulting path exists. Returns the name of the path.
    """
    path = os.path.join(path, *paths)
    os.makedirs(path, exist_ok=True)
    return path

def ensure_copy(src, dst):
    if not os.path.isfile(src):
        mx.abort(f"The function mx_sulong_path_helpers.ensure_copy currently only supports files, but {src} is not a file.")
    ensure_dirs(os.path.dirname(dst))
    shutil.copyfile(src, dst)
    return dst

def ensure_all_copy(src, dst):
    for root, _, files in os.walk(src):
        reldir = os.path.relpath(root, start=src)
        for name in files:
            ensure_copy(os.path.join(root, name), os.path.join(dst, reldir, name))
    return dst

def ensure_symlink_file(src, dst):
    if not os.path.exists(dst):
        os.symlink(src, dst)
        return dst
    elif os.path.islink(dst):
        resolved = os.readlink(dst)
        if not os.path.samefile(resolved, src):
            mx.warn(f"Possibly incorrect symlink {dst} points to {resolved} rather than {src}.")
        return dst
    else:
        shutil.copyfile(src, dst)
        return dst

def ensure_symlink_folder(src, dst):
    if not os.path.exists(dst):
        os.symlink(src, dst, target_is_directory=True)
        return dst
    else:
        return dst

def ensure_symlink(src, dst):
    if os.path.isdir(src):
        return ensure_symlink_folder(src, dst)
    else:
        return ensure_symlink_file(src, dst)
