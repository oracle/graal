#
# Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
import itertools
import os
import re
import shlex
import shutil
import tempfile
import urllib.request
import zipfile
from argparse import Action, ArgumentError, ArgumentParser, RawDescriptionHelpFormatter
from pathlib import Path
from urllib.parse import quote

import mx
import mx_urlrewrites
import mx_sdk_vm
import mx_sdk_vm_impl

_suite = mx.suite('sdk')
_JTREG_TEST = re.compile(r'(?<![\w$])@test\b')
_JTREG_RUN = re.compile(r'(?<![\w$])@run\b')


class _JavaOptionAction(Action):
    """Collects jtreg-style Java options while preserving command-line order."""

    def __call__(self, parser, namespace, values, option_string=None):
        """Appends one option or shell-split options to the parsed namespace in input order."""
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


def _run_verbose(command, cwd=None, **kwargs):
    """Logs a command and its working directory, then forwards it and keyword arguments to mx.run."""
    location = Path(cwd) if cwd else Path.cwd()
    mx.log(f"# Running in {location}:")
    mx.log(f"{mx.list_to_cmd_line(command)}")
    return mx.run(command, cwd=cwd, **kwargs)


def _make_path(path):
    """Returns an absolute path with POSIX separators so make preserves it in recipes."""
    return Path(path).resolve().as_posix()


def _get_labsjdk_ce_version(graalvm_home):
    """Returns the LabsJDK CE artifact version and JVMCI tag from the selected GraalVM."""
    release_file = Path(graalvm_home) / 'release'
    try:
        runtime_version = mx_sdk_vm.parse_release_file(str(release_file)).get('JAVA_RUNTIME_VERSION')
    except (OSError, ValueError, TypeError) as error:
        mx.abort(f"Error reading JAVA_RUNTIME_VERSION from {release_file}: {error}")
    if not runtime_version:
        mx.abort(f"JAVA_RUNTIME_VERSION is missing from {release_file}")
    if 'jvmci-' not in runtime_version:
        mx.abort(f"JAVA_RUNTIME_VERSION '{runtime_version}' from {release_file} does not contain a JVMCI tag")
    jvmci_index = runtime_version.index('jvmci-')
    jvmci_tag = runtime_version[jvmci_index:]
    version_prefix = runtime_version[:jvmci_index]
    # LabsJDK CE artifact versions omit the Oracle release's -LTS marker.
    if version_prefix.endswith('-LTS-'):
        version_prefix = version_prefix[:-len('-LTS-')] + '-'
    return f'ce-{version_prefix}{jvmci_tag}', jvmci_tag


def _is_url_reachable(url):
    """Checks whether `url` can be opened using mx's verification mode."""
    # Verification does not write the destination, but mx includes it in failure messages.
    return mx.download(os.devnull, [url], verifyOnly=True, abortOnError=False)

def _prebuilt_labsjdk_test_image_dir(version):
    """Returns the cache directory for a test image matching a version and platform."""
    platform = f'{mx.get_os()}-{mx.get_arch()}'
    return Path(_suite.dir) / 'mxbuild' / f'prebuilt-labsjdk-test-image-{version}-{platform}'


def _get_prebuilt_labsjdk_test_image(version, jvmci_tag):
    """Downloads a platform-specific prebuilt test image if available, otherwise returns None."""
    test_image_dir = _prebuilt_labsjdk_test_image_dir(version)
    if test_image_dir.exists():
        return test_image_dir
    platform = f'{mx.get_os()}-{mx.get_arch()}'
    archive_name = f'labsjdk-{version}-debug-tests-{platform}.tar.gz'
    base_url = f"https://github.com/graalvm/labs-openjdk/releases/download/{jvmci_tag}"
    url = f'{base_url}/labsjdk-{quote(version, safe="")}-debug-tests-{platform}.tar.gz'
    if not _is_url_reachable(url):
        new_url = mx_urlrewrites.rewriteurl(url)
        if url != new_url:
            if not _is_url_reachable(new_url):
                mx.log(f"Automatic URLs for pre-built test image are unreachable: {url} and {new_url}")
                return None
            url = new_url
        else:
            mx.log(f"Automatic URL for pre-built test image is unreachable: {url}")
            return None

    mx.log(f"Downloading LabsJDK test image from {url}...")
    test_image_dir.parent.mkdir(parents=True, exist_ok=True)
    # Download and unpack in a temporary directory before exposing the image to later runs.
    with tempfile.TemporaryDirectory(prefix='labsjdk-test-image-', dir=str(test_image_dir.parent)) as staging_dir:
        staging_dir = Path(staging_dir)
        archive = staging_dir / archive_name
        extracted = staging_dir / 'extracted'
        extracted.mkdir()
        mx.download(str(archive), [url], verbose=True)
        mx.Extractor.create(str(archive)).extract(str(extracted))
        entries = list(extracted.iterdir())
        if not entries:
            mx.abort(f'The LabsJDK test image archive is empty: {url}')
        image_root = entries[0] if len(entries) == 1 and entries[0].is_dir() else extracted
        image_root.rename(test_image_dir)
    return test_image_dir


def _git_commit(repo, rev):
    """Resolves a Git revision to its commit hash, returning None when it cannot be resolved."""
    out = mx.OutputCapture()
    err = mx.OutputCapture()
    rc = mx.run(['git', '-C', str(repo), 'rev-parse', '--verify', '--quiet', f'{rev}^{{commit}}'], out=out, err=err, nonZeroIsFatal=False)
    return out.data.strip() if rc == 0 else None


def _git_status(repo):
    """Returns Git porcelain status including the branch and tracked and untracked changes."""
    out = mx.OutputCapture()
    mx.run(['git', '-C', str(repo), 'status', '--porcelain=v1', '--branch'], out=out, nonZeroIsFatal=True)
    return out.data


def _test_image_git_state(labs_openjdk):
    """Returns the checkout's HEAD and working tree status used to invalidate a built test image."""
    head_commit = _git_commit(labs_openjdk, 'HEAD')
    if not head_commit:
        mx.abort(f"Could not determine the current git commit for {labs_openjdk}")
    # Include both HEAD and dirty state so clean commit changes cannot reuse a stale image.
    return f"HEAD={head_commit}\n{_git_status(labs_openjdk)}"


def _check_labsjdk_jvmci_tag(labs_openjdk, expected_tag):
    """Requires HEAD to match the selected GraalVM's JVMCI tag, with checkout recovery instructions.

    The checkout is not modified, and working tree changes are allowed when HEAD matches the tag.
    """
    labs_openjdk = Path(labs_openjdk)

    head_commit = _git_commit(labs_openjdk, 'HEAD')
    tag_commit = _git_commit(labs_openjdk, f'refs/tags/{expected_tag}')
    if head_commit and tag_commit and head_commit == tag_commit:
        return

    status = _git_status(labs_openjdk) if head_commit else ''
    dirty = any(line and not line.startswith('##') for line in status.splitlines())
    recovery_commands = []
    if not tag_commit:
        recovery_commands.append(['git', 'fetch', '--tags'])
    if dirty:
        recovery_commands.append(['git', 'stash', 'push', '--include-untracked'])
    recovery_commands.append(['git', 'reset', '--hard', expected_tag])
    recovery_command = ' && '.join(mx.list_to_cmd_line(command) for command in recovery_commands)

    reason = f"is not checked out at expected JVMCI tag {expected_tag}"
    if not head_commit:
        reason = "is not a valid git checkout"
    elif not tag_commit:
        reason += " (the tag is not available locally)"
    mx.abort(f"""LabsJDK directory {labs_openjdk} {reason}.
Run this command from the LabsJDK directory to fetch and check out the required tag:
  {recovery_command}""" + ("\nThe stash preserves tracked and untracked changes; restore them later with `git stash pop`." if dirty else ""))


def _test_image_dir(labs_openjdk, conf_name):
    """Returns the test image directory for a LabsJDK build configuration."""
    return Path(labs_openjdk) / 'build' / conf_name / 'images' / 'test'


def _test_image_status_stamp(labs_openjdk, conf_name):
    """Returns the file recording the Git state used to build the local test image."""
    return _test_image_dir(labs_openjdk, conf_name) / '.run-jdk-test-git-status'


def _resolve_jtreg_executable():
    """Finds jtreg in JTREG_HOME/bin before PATH, returning None when neither provides it."""
    jtreg_home = mx.get_env('JTREG_HOME')
    search_path = os.environ.get('PATH', '')
    if jtreg_home:
        # Prefer an explicitly selected jtreg installation over any ambient PATH entry.
        search_path = str(Path(jtreg_home) / 'bin') + os.pathsep + search_path
    return shutil.which('jtreg', path=search_path)


def _get_labsjdk_make(labs_openjdk, conf_name):
    """Gets the make executable selected by the LabsJDK configuration."""
    spec = Path(labs_openjdk) / 'build' / conf_name / 'spec.gmk'
    try:
        contents = spec.read_text(encoding='utf-8')
    except (OSError, UnicodeError) as error:
        mx.abort(f'Could not read LabsJDK configuration {spec}: {error}')
    match = re.search(r'^MAKE[ \t]*:?=[ \t]*(.*)$', contents, re.MULTILINE)
    if not match or not match.group(1).strip():
        mx.abort(f'Could not find the MAKE executable in {spec}')
    make = match.group(1).strip()
    if mx.get_os() == 'windows' and make.startswith('/'):
        # Native Windows Python cannot execute the Cygwin path stored in spec.gmk.
        output = mx.OutputCapture()
        mx.run(['cygpath', '-w', make], out=output, nonZeroIsFatal=True)
        make = output.data.strip()
    return make


def _ensure_test_image(labs_openjdk, conf_name, make):
    """Builds a test image when its recorded Git state differs, then writes the new state stamp."""
    status = _test_image_git_state(labs_openjdk)
    stamp = _test_image_status_stamp(labs_openjdk, conf_name)
    if stamp.exists():
        with stamp.open(encoding='utf-8') as fp:
            if fp.read() == status:
                mx.log(f"Skipping make test-image; {stamp} matches the current labsjdk git status")
                return

    # Build the JDK test image when the repo state has changed since the last build.
    _run_verbose([make, 'test-image', f'CONF_NAME={conf_name}'], cwd=str(labs_openjdk), nonZeroIsFatal=True)
    stamp.parent.mkdir(parents=True, exist_ok=True)
    with stamp.open('w', encoding='utf-8') as fp:
        fp.write(status)


def _check_labsjdk_configuration(labs_openjdk, conf_name):
    """Ensures spec.gmk exists, configuring LabsJDK through JIB or an available jtreg installation.

    JIB_SERVER selects JIB configuration; otherwise JTREG_HOME/bin or PATH must provide jtreg.
    Missing configurations are created automatically.
    """
    spec = Path(labs_openjdk) / 'build' / conf_name / 'spec.gmk'
    if spec.exists():
        return

    mx.warn(f"{spec} does not exist")

    jib_server = mx.get_env("JIB_SERVER")
    if jib_server:
        init_command = ['bash', 'bin/jib.sh', 'configure', '--', f'--with-conf-name={conf_name}']
        mx.log(f"""Initializing the JDK configuration by changing to {labs_openjdk} and running `{mx.list_to_cmd_line(init_command)}`.""")
        mx.log(f"JIB_SERVER is set to {jib_server}")
    else:
        jtreg = _resolve_jtreg_executable()
        if not jtreg:
            mx.abort("Could not find a jtreg executable for configuring labs-openjdk. Update PATH to include jtreg or set JTREG_HOME so that JTREG_HOME/bin contains jtreg, then try again.")
        init_command = ['bash', 'configure', f'--with-conf-name={conf_name}', f'--with-jtreg={jtreg}']
        mx.log(f"""Initializing the JDK configuration by changing to {labs_openjdk} and running `{mx.list_to_cmd_line(init_command)}`. If this fails due to missing dependencies, consult {Path(labs_openjdk) / 'doc' / 'building.md'}.""")

    mx.run(init_command, nonZeroIsFatal=True, cwd=str(labs_openjdk))


def _is_jtreg_test(source):
    """Checks whether `source` contains both jtreg tags in comments."""
    try:
        with Path(source).open(encoding='utf-8') as fp:
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


def _expand_test_selectors(tests):
    """Expands selector files and HTTP(S) URLs using the first word of each non-comment, non-empty line."""
    selectors = []
    for test in tests:
        if test.startswith('file:'):
            selector_file = Path(test[len('file:'):])
            try:
                contents = selector_file.read_text(encoding='utf-8')
            except (OSError, UnicodeError) as error:
                mx.abort(f'Could not read JDK test selector file {selector_file}: {error}')
        elif test.startswith(('http://', 'https://')):
            try:
                with urllib.request.urlopen(test, timeout=30) as response:
                    contents = response.read().decode('utf-8')
            except (OSError, ValueError) as error:
                mx.abort(f'Could not retrieve JDK test selectors from {test}: {error}')
        else:
            selectors.append(test)
            continue
        # Files and URLs share the same selector format, including ProblemList columns.
        for line in contents.splitlines():
            fields = line.split()
            if not fields or fields[0].startswith('#'):
                continue
            # Preserve #id test variants; only a leading # denotes a comment.
            selectors.append(fields[0])
    if not selectors:
        mx.abort('No JDK tests selected by the supplied selector files or URLs')
    return selectors


def _graal_test_sources(tests):
    """Gets existing file and directory arguments that should be staged for jtreg."""
    repo_root = (Path(_suite.dir) / '..').resolve()
    sources = []
    for test in tests:
        selector_path, _, _ = test.partition('#')
        path = Path(selector_path)
        if len(path.parts) > 1 and path.parts[0] == 'graalvm-tests':
            # Map exported staging selectors back to their source in a fresh LabsJDK checkout.
            source = repo_root.joinpath(*path.parts[1:]).resolve()
        else:
            source = path.resolve()
        if source.is_file() or source.is_dir():
            sources.append((test, source))
    return sources


def _validate_graal_test_sources(tests):
    """Validates Graal test sources and returns their repository-relative paths."""
    repo_root = (Path(_suite.dir) / '..').resolve()
    validated = []
    for argument, source in _graal_test_sources(tests):
        try:
            relative = source.relative_to(repo_root)
        except ValueError:
            mx.abort(f"Test source {argument} is outside the Graal repository {repo_root}")

        if source.is_file():
            if not _is_jtreg_test(source):
                mx.abort(f"Test source {argument} is not a valid jtreg test: it must contain @test and @run in comments")
        else:
            valid_test = any(
                _is_jtreg_test(path)
                for path in source.rglob('*')
                if path.is_file()
            )
            if not valid_test:
                mx.abort(f"Test directory {argument} does not contain a valid jtreg test")
        validated.append((argument, source, relative))
    return validated


def _stage_graal_test_sources(labs_openjdk, tests, validated=None):
    """Copies validated Graal tests and returns selectors for the jtreg invocation."""
    if validated is None:
        validated = _validate_graal_test_sources(tests)
    stage_root = Path(labs_openjdk) / 'test' / 'hotspot' / 'jtreg' / 'graalvm-tests'
    if validated and stage_root.exists():
        mx.warn(f"The graalvm-tests staging directory {stage_root} already exists; removing it before copying Graal tests")
        shutil.rmtree(stage_root)
    staged_tests = {argument: argument for argument in tests}
    for argument, source, relative in validated:
        destination = stage_root / relative
        if source.is_file():
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(source, destination, dirs_exist_ok=True)
        _, has_variant, variant = argument.partition('#')
        staged_selector = (Path('graalvm-tests') / relative).as_posix()
        staged_tests[argument] = staged_selector + (f'#{variant}' if has_variant else '')
    return [staged_tests[test] for test in tests]


def _make_unique_log_path(logs_dir, name):
    """Gets a non-existing top-level path for a copied test log."""
    destination = logs_dir / name
    counter = 0
    while destination.exists():
        destination = logs_dir / f'{name}_{counter}'
        counter += 1
    return destination


def _path_contains(parent, child):
    """Returns whether child is the same as or nested under parent."""
    parent = Path(parent).resolve()
    child = Path(child).resolve()
    try:
        child.relative_to(parent)
        return True
    except ValueError:
        return False


def _paths_overlap(first, second):
    """Returns whether either path is the same as or contains the other path."""
    first = Path(first).resolve()
    second = Path(second).resolve()
    return _path_contains(first, second) or _path_contains(second, first)


def _validate_logs_dir(logs_dir, input_paths):
    """Rejects a log destination that could delete inputs when it is replaced."""
    if not logs_dir:
        return None
    logs_dir = Path(logs_dir).expanduser().resolve()
    if logs_dir.exists() and not logs_dir.is_dir():
        mx.abort(f"--logs destination {logs_dir} exists and is not a directory")
    working_directory = Path.cwd().resolve()
    if _path_contains(logs_dir, working_directory):
        mx.abort(f"--logs directory {logs_dir} contains the current working directory {working_directory}")
    repo_root = Path(_suite.dir).parent.resolve()
    if _path_contains(logs_dir, repo_root):
        mx.abort(f"--logs directory {logs_dir} contains the Graal repository {repo_root}")
    if _path_contains(repo_root, logs_dir):
        relative_logs_dir = logs_dir.relative_to(repo_root).as_posix()
        out = mx.OutputCapture()
        rc = mx.run(
            ['git', '-C', str(repo_root), '--literal-pathspecs', 'ls-files', '-z', '--', relative_logs_dir],
            out=out,
            nonZeroIsFatal=False,
        )
        if rc != 0:
            mx.abort(f"Could not check tracked files under --logs destination {logs_dir}")
        if out.data:
            mx.abort(f"--logs destination {logs_dir} contains tracked files")
    for input_path in input_paths:
        input_path = Path(input_path).resolve()
        if _paths_overlap(logs_dir, input_path):
            mx.abort(f"--logs directory {logs_dir} overlaps runner input {input_path}; choose a separate output directory")
    return logs_dir


def _get_jtreg_test_result(contents):
    """Returns a JTR's authoritative result and selector from its testresult section."""
    result_section = contents.partition('#-----testresult-----')[2].partition('#section:')[0]
    result = None
    result_match = re.search(r'^execStatus=([^\.\r\n]+)', result_section, re.MULTILINE)
    if result_match:
        result = result_match.group(1)
    test_match = re.search(r'^test=(.+)$', result_section, re.MULTILINE)
    test = test_match.group(1) if test_match else None
    return result, test


def _copy_test_logs(logs_dir, build_dir):
    """Lists all failing selectors, copies up to 25 test logs, and archives diagnostics in a 25 MB ZIP."""
    try:
        shutil.rmtree(logs_dir, ignore_errors=True)
        logs_dir.mkdir(parents=True, exist_ok=True)
        archive_path = logs_dir / 'all-output.zip'
        paths = sorted(path for directory in (build_dir / 'test-support', build_dir / 'test-results')
                       for path in directory.rglob('*') if path.is_file() and path.suffix != '.class')
        failing_tests = set()
        failures = {}
        for path in paths:
            if path.suffix != '.jtr':
                continue
            try:
                # Result metadata precedes action output, which is only needed for selected failures.
                with path.open(encoding='utf-8') as source:
                    header = ''.join(itertools.takewhile(lambda line: not line.startswith('#section:'), source))
                result, test = _get_jtreg_test_result(header)
            except (OSError, UnicodeError) as error:
                mx.warn(f'Could not inspect JDK test result file {path}: {error}; preserving it in diagnostics')
                result, test = None, None
            if result == 'Passed':
                continue
            if result is None:
                mx.warn(f'Could not determine the authoritative result in {path}; preserving it in diagnostics')
            if test:
                # jtreg escapes # in the stored test property, including variant selectors.
                test = test.replace(r'\#', '#').rstrip('\r')
                failing_tests.add(test)
            else:
                mx.warn(f'Could not find the JDK test selector in {path}')
            failures.setdefault(test or path.as_posix(), []).append(path)
        # The selector list remains complete even when diagnostics exceed the archive size limit.
        (logs_dir / 'failures.txt').write_text(''.join(test + '\n' for test in sorted(failing_tests)), encoding='utf-8')
        # Configuration failures may have no test results, so retain their diagnostics separately.
        configuration_logs = (build_dir.parent.parent / 'config.log', build_dir / 'configure-support' / 'config.log')
        for path in configuration_logs:
            if path.is_file():
                shutil.copy2(path, _make_unique_log_path(logs_dir, path.name))
        with zipfile.ZipFile(archive_path, 'w', compression=zipfile.ZIP_DEFLATED):
            pass
        archived = set()
        copied_logs = 0
        for test in sorted(failures):
            for result_path in failures[test]:
                try:
                    contents = result_path.read_text(encoding='utf-8')
                except (OSError, UnicodeError):
                    contents = ''
                # Include test-local files and diagnostics named in the JTR, such as child-process logs.
                test_dir = result_path.with_suffix('.d')
                normalized_contents = contents.replace('\\\\', '/').replace('\\', '/')
                related = [path for path in paths if path.suffix != '.jtr' and
                           (test_dir in path.parents or path.as_posix() in normalized_contents or
                            (path.name in contents and (path.parent == result_path.parent or
                                                       re.match(r'(?:hs_err_pid|pid-)\d+', path.name))))]
                for path in [result_path] + related:
                    if path in archived:
                        continue
                    # Stage the append so the closed ZIP, including its directory, never exceeds the cap.
                    with tempfile.TemporaryFile() as candidate:
                        with archive_path.open('rb') as previous:
                            shutil.copyfileobj(previous, candidate)
                        with zipfile.ZipFile(candidate, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
                            archive.write(path, path.relative_to(build_dir.parent).as_posix())
                        candidate.seek(0, os.SEEK_END)
                        if candidate.tell() > 25_000_000:
                            mx.log('Stopped collecting JDK test diagnostics at the 25 MB archive limit')
                            return
                        candidate.seek(0)
                        with archive_path.open('wb') as destination:
                            shutil.copyfileobj(candidate, destination)
                    archived.add(path)
                    # Limit individual CI attachments without truncating the downloadable archive.
                    if copied_logs < 25 and (path.name.startswith('hs_err_pid') or path.suffix == '.jtr'):
                        destination = _make_unique_log_path(logs_dir, path.name)
                        shutil.copy2(path, destination)
                        mx.log(f'Copied JDK test log {destination}')
                        copied_logs += 1
    except OSError as error:
        mx.warn(f'Could not copy JDK test logs from {build_dir}: {error}')


@mx.command(_suite.name, 'run-jdk-test')
def run_jdk_test(args):
    """Run JDK tests from a LabsJDK checkout against a selected GraalVM.

Build the GraalVM first, then run this command from the same suite and mx
environment. The LabsJDK checkout's HEAD must match the JVMCI tag reported by
the selected GraalVM; this command does not clone, fetch, or reset the checkout.
A prebuilt test image is used only for a clean LabsJDK checkout; otherwise a
local test image is built when no explicit image is supplied.

An existing LabsJDK configuration is reused. If configuration is missing,
JIB_SERVER selects configuration through bin/jib.sh; otherwise JTREG_HOME/bin
or PATH must provide jtreg. Missing configurations are created automatically
without prompting. The make executable recorded in that configuration's
spec.gmk runs the tests.

Without an explicit test image, a cached or downloadable LabsJDK test image is
preferred for a clean checkout. If no automatic download URL is reachable or
the checkout has local changes, make test-image builds one locally. Crema
(-svm) is selected when available; otherwise the first KNOWN VM in the
GraalVM's jvm.cfg is used. Additional Java options apply to jtreg test JVMs,
not necessarily to tools or child JVMs started by a test.

Examples (from the graal/vm suite, with LABSJDK_DIR pointing to the matching checkout):
  mx --env ce-next --native-images=lib:jvm build
  mx --env ce-next --native-images=lib:jvm run-jdk-test \\
    --labs-openjdk-dir "$LABSJDK_DIR" --logs jdk-test-logs java/lang

  # Rerun failures without excluding them; preserve the original logs.
  mx --env ce-next --native-images=lib:jvm run-jdk-test \\
    --labs-openjdk-dir "$LABSJDK_DIR" --logs jdk-test-rerun-logs \\
    file:jdk-test-logs/failures.txt

  # Rerun failures directly from a CI log URL containing a selector list.
  mx --env ce-next --native-images=lib:jvm run-jdk-test \\
    --labs-openjdk-dir "$LABSJDK_DIR" --logs jdk-test-rerun-logs \\
    https://ci.example.com/path/to/failures.txt

  # Force HotSpot for jtreg test JVMs, even when Crema is the default.
  mx --env ce-next --native-images=lib:jvm run-jdk-test \\
    --labs-openjdk-dir "$LABSJDK_DIR" --vm server \\
    java/lang/Math/IntegralPowTest.java

For EE, use the graal-enterprise/vm-enterprise suite with --env ee-next.
Use mx run-jdk-test --help for the complete option descriptions.
"""
    # mx.command wraps the function; use its original docstring for argparse as well as mx help.
    parser = ArgumentParser(prog='mx run-jdk-test', description=run_jdk_test.command_function.__doc__, formatter_class=RawDescriptionHelpFormatter)
    parser.add_argument('--labs-openjdk-dir', required=True, metavar='DIR', help=(
        'LabsJDK source checkout containing make/. HEAD must match the selected GraalVM JVMCI tag. '
        'Relative paths are resolved from the current directory; the checkout is not reset automatically.'))
    parser.add_argument('--test-image-dir', default=os.environ.get('TEST_IMAGE_DIR', ''), metavar='DIR', help=(
        'prebuilt test image directory passed as TEST_IMAGE_DIR to make test-only, '
        'skipping automatic download and make test-image. Defaults to the TEST_IMAGE_DIR '
        'environment variable; if unset or empty, selects an automatic image for a clean checkout or builds one locally.'))
    parser.add_argument('--conf-name', default=os.environ.get('CONF_NAME', 'graalvm-test'), metavar='NAME', help=(
        'LabsJDK build configuration under <labs-openjdk-dir>/build/NAME, passed as CONF_NAME to make. '
        'Reuses spec.gmk or configures it if missing. Defaults to the CONF_NAME environment variable, '
        'or graalvm-test if unset.'))
    parser.add_argument('--exclude', metavar='FILE', help=(
        'additional jtreg ProblemList.txt file, passed as EXTRA_PROBLEM_LISTS alongside the standard JDK exclusions. '
        'Entries specify test selectors, issue keys, and platforms. Omit this option when reproducing an excluded failure.'))
    parser.add_argument('--logs', metavar='DIR', help=(
        'replace DIR with collected logs after configuration/test execution, including configuration or test failures. '
        'Copies at most 25 non-passing .jtr files or hs_err_pid logs individually and '
        'archives diagnostics for failing tests in all-output.zip, stopping before it would exceed 25 MB. '
        'Also writes config.log when available '
        'and all sorted unique failing selectors in failures.txt. The failure list is empty if no failing selectors were found. '
        'Without this option, results remain only in the LabsJDK build directory.'))
    parser.add_argument('--vm', metavar='NAME', help=(
        'choose a KNOWN VM entry from the tested GraalVM jvm.cfg; accepts server or -server, svm or -svm. '
        'Defaults to svm when available, otherwise the first KNOWN entry. Selecting svm forwards -svm '
        'and enables HotSpot option compatibility. Selecting server forwards -server '
        'to force HotSpot when Crema is the default.'))
    parser.add_argument('-g', '--graalvm-home', metavar='DIR', help=(
        'already-built GraalVM to test, passed as JDK_UNDER_TEST. Defaults to the GraalVM '
        'resolved by the active mx suite/environment. This command does not build GraalVM.'))
    parser.add_argument('--java-option', dest='java_options', action=_JavaOptionAction, default=[], metavar='OPTION', help=(
        'append one Java option to TEST_OPTS JAVA_OPTIONS for jtreg test JVMs. Repeat to add options '
        'in command-line order after automatic Crema options. Use --java-option=-Xmx2g for values '
        'starting with a dash; no shell splitting is performed.'))
    parser.add_argument('--java-options', dest='java_options', action=_JavaOptionAction, metavar='OPTIONS', help=(
        'append a quoted string of Java options, split with shlex using shell-style quoting, '
        'to TEST_OPTS JAVA_OPTIONS. May be repeated or mixed with --java-option; input order is preserved. '
        'Example: --java-options="-Xmx2g -ea". These options are not automatically forwarded to test-created child JVMs.'))
    parser.add_argument('tests', nargs='+', metavar='TEST', help=(
        'one or more JDK test selectors (directories, test files, or test variants such as Foo.java#id0). '
        'file:PATH or an HTTP(S) URL reads UTF-8 content and takes the first word of each non-empty line '
        'whose first word does not start with #, '
        'ignoring remaining columns; accepts ProblemList.txt and failures.txt, preserves variant suffixes, '
        'and can be mixed with ordinary selectors. Contents are selectors, not recursive file or URL includes; '
        'URLs use a 30-second network timeout. Relative file paths use the current directory. '
        'Existing local files/directories must be inside the Graal repository '
        'and contain @test and @run in comments; they are copied into LabsJDK test/hotspot/jtreg/graalvm-tests, '
        'replacing previous staged sources.'))
    parsed_args = parser.parse_args(args)
    tests = _expand_test_selectors(parsed_args.tests)

    labs_openjdk = Path(parsed_args.labs_openjdk_dir).resolve()
    if not labs_openjdk.is_dir():
        mx.abort(f"{labs_openjdk} is not a directory")
    if not (labs_openjdk / 'make').exists():
        mx.abort(f"{labs_openjdk} does not look like a labs-openjdk checkout: missing make/")

    graalvm_home = Path(parsed_args.graalvm_home or mx_sdk_vm_impl.resolve_graalvm_home()).resolve()
    labsjdk_version, jvmci_tag = _get_labsjdk_ce_version(graalvm_home)
    _check_labsjdk_jvmci_tag(labs_openjdk, jvmci_tag)

    test_image_dir = Path(parsed_args.test_image_dir).expanduser().resolve() if parsed_args.test_image_dir else None
    prebuilt_image_dir = _prebuilt_labsjdk_test_image_dir(labsjdk_version)
    build_dir = labs_openjdk / 'build' / parsed_args.conf_name
    validated_graal_tests = _validate_graal_test_sources(tests)
    selector_file_paths = [
        Path(test[5:]).expanduser().resolve()
        for test in parsed_args.tests
        if test.startswith('file:')
    ]
    input_paths = [labs_openjdk, graalvm_home, build_dir, prebuilt_image_dir, Path(__file__), Path(_suite.dir).parent / '.git']
    input_paths.extend(source for _, source, _ in validated_graal_tests)
    input_paths.extend(selector_file_paths)
    if test_image_dir:
        input_paths.append(test_image_dir)
    if parsed_args.exclude:
        input_paths.append(Path(parsed_args.exclude).expanduser().resolve())
    logs_dir = _validate_logs_dir(parsed_args.logs, input_paths)

    # Check before staging test sources so only pre-existing checkout changes disable the prebuilt image.
    labsjdk_dirty = any(line and not line.startswith('##') for line in _git_status(labs_openjdk).splitlines())
    staged_tests = _stage_graal_test_sources(labs_openjdk, tests, validated_graal_tests)
    build_dir = labs_openjdk / 'build' / parsed_args.conf_name
    # Collect diagnostics even when configuring the JDK fails before test execution.
    try:
        # Discard previous results so a setup failure cannot report stale test failures.
        shutil.rmtree(build_dir / 'test-support', ignore_errors=True)
        shutil.rmtree(build_dir / 'test-results', ignore_errors=True)
        _check_labsjdk_configuration(labs_openjdk, parsed_args.conf_name)
        make = _get_labsjdk_make(labs_openjdk, parsed_args.conf_name)

        if not test_image_dir:
            if labsjdk_dirty:
                mx.log('Skipping the prebuilt LabsJDK test image because the checkout has local changes')
            else:
                test_image_dir = _get_prebuilt_labsjdk_test_image(labsjdk_version, jvmci_tag)

        if not test_image_dir:
            _ensure_test_image(labs_openjdk, parsed_args.conf_name, make)

        # Path-based test selectors can be passed through the TEST make variable.
        test_selection = ' '.join(test.replace('\\', '/') for test in staged_tests)
        test_command = [
            make,
            'test-only',
            f'TEST={test_selection}',
            f'JDK_UNDER_TEST={_make_path(graalvm_home)}',
            f'CONF_NAME={parsed_args.conf_name}',
            'CONF_CHECK=auto',
        ]
        if test_image_dir:
            test_command.append(f'TEST_IMAGE_DIR={_make_path(test_image_dir)}')

        jtreg_keywords = ["VERBOSE=summary"] # One line of output per test: status, test name
        if parsed_args.exclude:
            jtreg_keywords.append(f'EXTRA_PROBLEM_LISTS={_make_path(parsed_args.exclude)}')
        test_command.append("JTREG=" + ";".join(jtreg_keywords))

        jvm_cfg = Path(mx_sdk_vm_impl._get_jvm_cfg(graalvm_home))
        candidate_vms = [line.split()[0] for line in jvm_cfg.read_text(encoding='utf-8').splitlines() if line.startswith("-") and "KNOWN" in line]
        assert candidate_vms, f"Could not find any KNOWN VM in {jvm_cfg}"
        selected_vm = candidate_vms[0]
        svm_is_default = selected_vm == "-svm"

        java_options = []
        vm_options = []

        if parsed_args.vm:
            # Accept both "server" and "-server" while keeping the make value explicit.
            selected_vm = parsed_args.vm if parsed_args.vm.startswith('-') else f'-{parsed_args.vm}'
            assert selected_vm in candidate_vms, f"Could not find {selected_vm} in {jvm_cfg}"
            java_options.append(selected_vm)
        elif "-svm" in candidate_vms:
            # Use crema libjvm, if available, when --vm is not specified
            selected_vm = "-svm"

        if selected_vm == "-svm":
            # Enable the HotSpot compatibility options for the JDK test harness invocation.
            java_options.append("-DCREMA_HOTSPOT_OPTION_COMPATIBILITY=true")

        if svm_is_default:
            # Enable the HotSpot compatibility options for all JDK tools (e.g. javac)
            # when -svm is the default VM config.
            vm_options = ["-DCREMA_HOTSPOT_OPTION_COMPATIBILITY=true"]

        java_options.extend(parsed_args.java_options)

        test_opts = []
        if java_options:
            test_opts.append(f"JAVA_OPTIONS={' '.join(java_options)}")
        if vm_options:
            test_opts.append(f"VM_OPTIONS={' '.join(vm_options)}")

        if test_opts:
            test_opts = ";".join(test_opts)
            test_command.append(f"TEST_OPTS={test_opts}")

        _run_verbose(test_command, cwd=str(labs_openjdk), nonZeroIsFatal=True)
    finally:
        if logs_dir:
            _copy_test_logs(logs_dir, build_dir)
