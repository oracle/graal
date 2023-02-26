#
# Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#

import os.path
import subprocess
import tempfile
import mx
import mx_fetchjdk
from mx import VC
from mx_bisect import BuildSteps
import shutil


class BuildStepsGraalVMStrategy(BuildSteps):
    _mx_path = mx._mx_path
    _jdk_home = None
    _tmp_dir = None

    def __init__(self):
        BuildSteps.__init__(self)

    # create specified copies of repo for parallel cmd execution
    def clone_repo(self, process_number=1):
        tmp_dir_prefix = 'graal_tmp_'
        self._tmp_dir = tempfile.mkdtemp(prefix=tmp_dir_prefix)
        cloned_path = self._clone()
        paths = self._copy(cloned_path, process_number)
        mx.log(str(paths))
        return paths

    def git_log_filtering_strategy(self):
        mx.log('Commits strategy from GraalVM')
        git_log_strategy = ['git', 'log', '--first-parent']
        return git_log_strategy

    def default_start_date(self):
        default_start_date = '2020-01-22'
        mx.log('default_start_date: ' + default_start_date)
        return default_start_date

    def after_checkout(self, jdk_distribution, paths):
        self._forceimports(paths)
        self._fetch_jdk(jdk_distribution, paths)

    def get_default_setup_cmd(self):
        return 'mx clean; mx build'

    def get_default_prepare_cmd(self):
        return ''

    def update_env_variables(self, path, env_variables):
        try:
            graalvm_home = subprocess.check_output(['mx', 'graalvm-home'], cwd=path, env=env_variables,
                                                   universal_newlines=True).strip()
            if graalvm_home:
                env_variables['GRAALVM_HOME'] = graalvm_home
                mx.logv('graalvm-home: ' + str(graalvm_home))
        except subprocess.CalledProcessError:
            pass

    def clean_tmp_files(self):
        if self._tmp_dir and os.path.exists(self._tmp_dir):
            shutil.rmtree(self._tmp_dir)

    def _clone(self):
        self.clean_tmp_files()
        current_path = os.getcwd()
        git, repo_path = VC.get_vc_root(os.getcwd())
        relative_path = current_path[len(repo_path) + 1:]
        git_url = git.default_pull(os.getcwd())
        repo_name = git_url[git_url.rfind('/') + 1: git_url.rfind('.')]
        repo_dist = os.path.join(self._tmp_dir, repo_name)
        git._clone(git_url, repo_dist)
        return repo_name, relative_path

    def _copy(self, cloned_path, process_number):
        cloned_repo = os.path.join(self._tmp_dir, cloned_path[0])
        paths = [None] * process_number
        for i in range(process_number):
            path = os.path.join(self._tmp_dir, 'g' + str(i))
            paths[i] = os.path.join(path, cloned_path[0], cloned_path[1])
            os.mkdir(path)
            copy_cmd = ["cp", "-aR", cloned_repo, path]
            proc = subprocess.Popen(copy_cmd)
            mx_dir = os.path.dirname(self._mx_path)
            copy_cmd = ["cp", "-aR", mx_dir, path]
            subprocess.Popen(copy_cmd)
        proc.communicate()
        return paths

    def _forceimports(self, paths):
        new_env = os.environ.copy()
        arr = [None] * len(paths)
        for i in range(len(paths)):
            new_env['MX_PRIMARY_SUITE_PATH'] = paths[i]
            proc = subprocess.Popen([self._mx_path, 'sforceimports'], env=new_env, cwd=paths[i])
            arr[i] = proc
        for proc in arr:
            proc.communicate()

    def _fetch_jdk(self, jdk_id, paths):
        if not self._mx_path:
            self._mx_path = mx.get_jdk().home
        old_quiet = mx._opts.quiet
        mx._opts.quiet = True
        _, repo_path = VC.get_vc_root(paths[0])
        try:
            # Change directory so that fetch-jdk finds common.json and jdk-binaries.json in repo_path
            old_cwd = os.getcwd()
            os.chdir(repo_path)
            jdk_home = mx_fetchjdk.fetch_jdk(['--to', tempfile.gettempdir(), '--jdk-id', jdk_id, ''])
            self._jdk_home = jdk_home
            os.environ['JAVA_HOME'] = jdk_home
        except (OSError, IOError) as err:
            mx.log(str(err))
            self._jdk_home = mx.get_jdk().home
        finally:
            os.chdir(old_cwd)
            mx._opts.quiet = old_quiet
