#
# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
#

import os
import re
import shlex
import shutil
from argparse import Action, ArgumentError, ArgumentParser
from os.path import commonpath, exists, isdir, isfile, join, relpath

import mx
import mx_sdk_vm_impl

_suite = mx.suite('compiler')
_JTREG_TEST = re.compile(r'(?<![\w$])@test\b')
_JTREG_RUN = re.compile(r'(?<![\w$])@run\b')


class _JavaOptionAction(Action):
    """Collects jtreg-style Java options while preserving command-line order."""

    def __call__(self, parser, namespace, values, option_string=None):
        options = getattr(namespace, self.dest)
        if options is None:
            options = []
        if option_string == '--java-options':
            try:
                options.extend(shlex.split(values))
            except ValueError as error:
                raise ArgumentError(self, f'invalid Java options: {error}') from error
        else:
            options.append(values)
        setattr(namespace, self.dest, options)


def _command_line(command):
    return ' '.join(map(shlex.quote, command))


def _run_verbose(command, cwd=None, **kwargs):
    location = cwd if cwd else os.getcwd()
    mx.log(f"Running in {location}: {_command_line(command)}")
    return mx.run(command, cwd=cwd, **kwargs)


def _configured_labsjdk_jvmci_tag():
    jdk = mx.get_jdk(tag='default')
    out = mx.OutputCapture()
    err = mx.OutputCapture()
    source_path = join(_suite.dir, 'src', 'jdk.graal.compiler', 'src', 'jdk', 'graal', 'compiler', 'hotspot', 'JVMCIVersionCheck.java')
    command = [jdk.java, '-Xlog:disable', source_path, '--as-tag']
    rc = _run_verbose(command, out=out, err=err, nonZeroIsFatal=False)
    if rc != 0:
        mx.abort(f"Could not determine the JVMCI tag for {jdk.home}:\n{err.data.strip()}")
    return out.data.strip()


def _git_commit(repo, rev):
    out = mx.OutputCapture()
    err = mx.OutputCapture()
    rc = mx.run(['git', '-C', repo, 'rev-parse', '--verify', '--quiet', f'{rev}^{{commit}}'], out=out, err=err, nonZeroIsFatal=False)
    return out.data.strip() if rc == 0 else None


def _git_status(repo):
    out = mx.OutputCapture()
    mx.run(['git', '-C', repo, 'status', '--porcelain=v1', '--branch'], out=out, nonZeroIsFatal=True)
    return out.data


def _test_image_git_state(labs_openjdk):
    head_commit = _git_commit(labs_openjdk, 'HEAD')
    if not head_commit:
        mx.abort(f"Could not determine the current git commit for {labs_openjdk}")
    # Include both HEAD and dirty state so clean commit changes cannot reuse a stale image.
    return f"HEAD={head_commit}\n{_git_status(labs_openjdk)}"


def _check_labsjdk_jvmci_tag(labs_openjdk):
    expected_tag = _configured_labsjdk_jvmci_tag()
    if not expected_tag:
        mx.abort("Could not determine the JVMCI tag")

    head_commit = _git_commit(labs_openjdk, 'HEAD')
    if not head_commit:
        mx.warn(f"Could not determine the current git commit for {labs_openjdk}")
        return

    tag_commit = _git_commit(labs_openjdk, f'refs/tags/{expected_tag}')
    if not tag_commit:
        fetch_command = ['git', '-C', labs_openjdk, 'fetch', '--tags']
        fetch_command_line = ' '.join(map(shlex.quote, fetch_command))
        mx.warn(f"{labs_openjdk} is not checked out at expected JVMCI tag {expected_tag}. The tag is not available in the repo. To fetch missing tags, run: {fetch_command_line}")
        return

    if head_commit == tag_commit:
        return

    reset_command = ['git', '-C', labs_openjdk, 'reset', '--hard', expected_tag]
    reset_command_line = ' '.join(map(shlex.quote, reset_command))
    warning = f"{labs_openjdk} is not checked out at expected JVMCI tag {expected_tag}."
    if mx.is_interactive():
        mx.warn(warning)
        if mx.ask_question(f"Run `{reset_command_line}` now", '[yn]', 'n').startswith('y'):
            # Reset only after an explicit interactive confirmation.
            _run_verbose(reset_command, nonZeroIsFatal=True)
    else:
        mx.warn(f"{warning} To reset it, run: {reset_command_line}")


def _test_image_dir(labs_openjdk, conf_name):
    return join(labs_openjdk, 'build', conf_name, 'images', 'test')


def _test_image_status_stamp(labs_openjdk, conf_name):
    return join(_test_image_dir(labs_openjdk, conf_name), '.run-jdk-test-git-status')


def _resolve_jtreg_executable():
    jtreg_home = mx.get_env('JTREG_HOME')
    search_path = os.environ.get('PATH', '')
    if jtreg_home:
        # Prefer an explicitly selected jtreg installation over any ambient PATH entry.
        search_path = join(jtreg_home, 'bin') + os.pathsep + search_path
    return shutil.which('jtreg', path=search_path)


def _ensure_test_image(labs_openjdk, conf_name):
    status = _test_image_git_state(labs_openjdk)
    stamp = _test_image_status_stamp(labs_openjdk, conf_name)
    if exists(stamp):
        with open(stamp, encoding='utf-8') as fp:
            if fp.read() == status:
                mx.log(f"Skipping make test-image; {stamp} matches the current labsjdk git status")
                return

    # Build the JDK test image when the repo state has changed since the last build.
    _run_verbose(['make', 'test-image', f'CONF_NAME={conf_name}'], cwd=labs_openjdk, nonZeroIsFatal=True)
    os.makedirs(os.path.dirname(stamp), exist_ok=True)
    with open(stamp, 'w', encoding='utf-8') as fp:
        fp.write(status)


def _check_labsjdk_configuration(labs_openjdk, conf_name):
    spec = join(labs_openjdk, 'build', conf_name, 'spec.gmk')
    if exists(spec):
        return

    mx.warn(f"{spec} does not exist")

    jib_server = mx.get_env("JIB_SERVER")
    if jib_server:
        init_command = ['bash', 'bin/jib.sh', 'configure', '--', f'--with-conf-name={conf_name}']
        mx.log(f"""Initialize the JDK configuration by changing to {labs_openjdk} and running `{' '.join(map(shlex.quote, init_command))}`.""")
        mx.log(f"JIB_SERVER is set to {jib_server}")
        prompt = "Run the JIB configure command now"
    else:
        jtreg = _resolve_jtreg_executable()
        if not jtreg:
            mx.abort("Could not find a jtreg executable for configuring labs-openjdk. Update PATH to include jtreg or set JTREG_HOME so that JTREG_HOME/bin contains jtreg, then try again.")
        init_command = ['bash', 'configure', f'--with-conf-name={conf_name}', f'--with-jtreg={jtreg}']
        mx.log(f"""Initialize the JDK configuration by changing to {labs_openjdk} and running `{' '.join(map(shlex.quote, init_command))}`. If this fails due to missing dependencies, consult {join(labs_openjdk, 'doc', 'building.md')}.""")
        prompt = "Run the configure command now"

    if mx.is_interactive():
        if mx.ask_question(prompt, '[yn]', 'n').startswith('y'):
            _run_verbose(init_command, nonZeroIsFatal=True, cwd=labs_openjdk)
            return
    mx.abort("Missing configuration")


def _is_jtreg_test(source):
    """Checks whether `source` contains both jtreg tags in comments."""
    try:
        with open(source, encoding='utf-8') as fp:
            contents = fp.read()
    except (OSError, UnicodeError):
        return False
    comments = []
    index = 0
    state = 'code'
    comment_start = None
    while index < len(contents):
        current = contents[index]
        following = contents[index + 1] if index + 1 < len(contents) else ''
        if state == 'code':
            if current == '/' and following == '/':
                state = 'line-comment'
                comment_start = index + 2
                index += 2
                continue
            if current == '/' and following == '*':
                state = 'block-comment'
                comment_start = index + 2
                index += 2
                continue
            if current == '"':
                state = 'string'
            elif current == "'":
                state = 'character'
        elif state == 'line-comment':
            if current in '\r\n':
                comments.append(contents[comment_start:index])
                state = 'code'
        elif state == 'block-comment' and current == '*' and following == '/':
            comments.append(contents[comment_start:index])
            state = 'code'
            index += 2
            continue
        elif state in ('string', 'character'):
            if current == '\\':
                index += 2
                continue
            if (state == 'string' and current == '"') or (state == 'character' and current == "'"):
                state = 'code'
        index += 1
    if state == 'line-comment' and comment_start is not None:
        comments.append(contents[comment_start:])
    elif state == 'block-comment' and comment_start is not None:
        comments.append(contents[comment_start:])
    comment_text = '\n'.join(comments)
    return _JTREG_TEST.search(comment_text) is not None and _JTREG_RUN.search(comment_text) is not None


def _graal_test_sources(tests):
    """Gets existing file and directory arguments that should be staged for jtreg."""
    return [(test, os.path.realpath(os.path.abspath(test))) for test in tests if isfile(test) or isdir(test)]


def _validate_graal_test_sources(tests):
    """Validates Graal test sources and returns their repository-relative paths."""
    repo_root = os.path.realpath(os.path.abspath(join(_suite.dir, '..')))
    validated = []
    for argument, source in _graal_test_sources(tests):
        try:
            in_repo = commonpath((repo_root, source)) == repo_root
        except ValueError:
            in_repo = False
        if not in_repo:
            mx.abort(f"Test source {argument} is outside the Graal repository {repo_root}")

        relative = relpath(source, repo_root)
        if isfile(source):
            if not _is_jtreg_test(source):
                mx.abort(f"Test source {argument} is not a valid jtreg test: it must contain @test and @run in comments")
        else:
            valid_test = any(
                _is_jtreg_test(join(root, filename))
                for root, _, files in os.walk(source)
                for filename in files
                if isfile(join(root, filename))
            )
            if not valid_test:
                mx.abort(f"Test directory {argument} does not contain a valid jtreg test")
        validated.append((argument, source, relative))
    return validated


def _stage_graal_test_sources(labs_openjdk, tests):
    """Copies validated Graal tests and returns selectors for the jtreg invocation."""
    validated = _validate_graal_test_sources(tests)
    stage_root = join(labs_openjdk, 'test', 'hotspot', 'jtreg', 'graalvm-tests')
    if validated and exists(stage_root):
        mx.warn(f"The graalvm-tests staging directory {stage_root} already exists; removing it before copying Graal tests")
        shutil.rmtree(stage_root)
    staged_tests = {argument: argument for argument in tests}
    for argument, source, relative in validated:
        destination = join(stage_root, relative)
        if isfile(source):
            os.makedirs(os.path.dirname(destination), exist_ok=True)
            shutil.copy2(source, destination)
        else:
            os.makedirs(os.path.dirname(destination), exist_ok=True)
            shutil.copytree(source, destination, dirs_exist_ok=True)
        staged_tests[argument] = join('graalvm-tests', relative)
    return [staged_tests[test] for test in tests]


@mx.command(_suite.name, 'run-jdk-test', '<labs-openjdk> <test> [<test> ...]')
def run_jdk_test(args):
    """Run JDK tests from a labs-openjdk checkout against the selected GraalVM."""
    parser = ArgumentParser(prog='mx run-jdk-test', description='Run labs-openjdk tests against the GraalVM selected by the active mx environment.')
    parser.add_argument('labs_openjdk', help='top level directory of a labs-openjdk checkout')
    parser.add_argument('tests', nargs='+', help='JDK test selectors, or existing Graal test files/directories to validate, stage under graalvm-tests, and pass to make as TEST')
    parser.add_argument('--test-image-dir', default=os.environ.get('TEST_IMAGE_DIR', ''), help='TEST_IMAGE_DIR value passed to make test-only')
    parser.add_argument('--conf-name', default=os.environ.get('CONF_NAME', 'graalvm-test'), help='CONF_NAME value passed to make commands')
    parser.add_argument('--vm', help='VM selector to pass to jtreg @run JVMs, for example "server" for -server')
    parser.add_argument('--java-option', dest='java_options', action=_JavaOptionAction, default=[], metavar='OPTION', help='add one Java option to jtreg @run JVMs')
    parser.add_argument('--java-options', dest='java_options', action=_JavaOptionAction, metavar='OPTIONS', help='add shell-split Java options to jtreg @run JVMs')
    parsed_args = parser.parse_args(args)

    labs_openjdk = os.path.abspath(parsed_args.labs_openjdk)
    if not isdir(labs_openjdk):
        mx.abort(f"{labs_openjdk} is not a directory")
    if not exists(join(labs_openjdk, 'make')):
        mx.abort(f"{labs_openjdk} does not look like a labs-openjdk checkout: missing make/")

    staged_tests = _stage_graal_test_sources(labs_openjdk, parsed_args.tests)
    _check_labsjdk_configuration(labs_openjdk, parsed_args.conf_name)
    _check_labsjdk_jvmci_tag(labs_openjdk)

    graalvm_home = mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True)
    mx.log(f"Using JDK_UNDER_TEST={graalvm_home}")

    if not parsed_args.test_image_dir:
        _ensure_test_image(labs_openjdk, parsed_args.conf_name)

    test_selection = ' '.join(staged_tests)
    test_command = [
        'make',
        'test-only',
        f'TEST={test_selection}',
        f'JDK_UNDER_TEST={graalvm_home}',
        f'CONF_NAME={parsed_args.conf_name}',
    ]
    if parsed_args.test_image_dir:
        test_command.append(f'TEST_IMAGE_DIR={parsed_args.test_image_dir}')
    # Enable the HotSpot compatibility options only for the JDK test harness invocation.
    java_options = ["-DCREMA_HOTSPOT_OPTION_COMPATIBILITY=true"]
    if parsed_args.vm:
        # Accept both "server" and "-server" while keeping the make value explicit.
        java_options.append(parsed_args.vm if parsed_args.vm.startswith('-') else f'-{parsed_args.vm}')
    else:
        svm_probe = [join(graalvm_home, 'bin', 'java'), '-svm', '-version']
        if mx.run(svm_probe, out=mx.OutputCapture(), err=mx.OutputCapture(), nonZeroIsFatal=False) == 0:
            java_options.extend(['-svm'])
    java_options.extend(parsed_args.java_options)
    if java_options:
        # Use jtreg Java options so -svm is applied to @run actions but not to tool VMs such as javac for @build.
        test_command.insert(3, f"TEST_OPTS=JAVA_OPTIONS={' '.join(java_options)}")
    _run_verbose(test_command, cwd=labs_openjdk, nonZeroIsFatal=True)
