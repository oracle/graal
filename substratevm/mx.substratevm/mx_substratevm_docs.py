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

import os
import difflib
import mx


def _is_launcher(launcher_path):
    with open(launcher_path, 'rb') as fp:
        first_two_bytes = fp.read(2)
        launcher_magic = b'::' if mx.is_windows() else b'#!'
        return first_two_bytes == launcher_magic


def _ensure_native_image_executable():
    import mx_substratevm
    native_image_cmd = mx_substratevm.vm_native_image_path()

    if not os.path.exists(native_image_cmd):
        mx.log('Building GraalVM for config None ...')
        mx_substratevm._run_graalvm_cmd(['build'], None)
        native_image_cmd = mx_substratevm.vm_native_image_path()
        if not os.path.exists(native_image_cmd):
            mx.abort('The built GraalVM does not contain a native-image command')

    if _is_launcher(native_image_cmd):
        mx.log('Building image from launcher ' + native_image_cmd + ' ...')
        verbose_image_build_option = ['--verbose'] if mx.get_opts().verbose else []
        mx.run([native_image_cmd] + verbose_image_build_option + ['--macro:native-image-launcher'])

    return native_image_cmd


def _generate_markdown_options_table():
    native_image_cmd = _ensure_native_image_executable()
    output = mx.OutputCapture()
    mx.run([native_image_cmd, '--print-options=markdown'], out=output)
    return output.data.strip()


def _extract_table_from_markdown(markdown_text):
    lines = markdown_text.split('\n')
    table_start = None
    for i, line in enumerate(lines):
        if line.startswith('| Command | Type |'):
            table_start = i
            break
    if table_start is None:
        mx.abort("Could not find table in generated content")
    return '\n'.join(lines[table_start:]).strip()


def _normalize_table(table_text):
    lines = [line.strip() for line in table_text.split('\n') if line.strip()]
    return '\n'.join(lines)


def _get_option_commands(table_text):
    commands = set()
    for line in table_text.split('\n'):
        if not line.startswith('|'):
            continue
        if line.startswith('| Command |') or line.startswith('|---------'):
            continue
        columns = [column.strip() for column in line.split('|')]
        if len(columns) < 3:
            continue
        command = columns[1]
        if command:
            commands.add(command)
    return commands


def _remove_option_commands(table_text, commands_to_remove):
    filtered = []
    for line in table_text.split('\n'):
        if not line.startswith('|') or line.startswith('| Command |') or line.startswith('|---------'):
            filtered.append(line)
            continue
        columns = [column.strip() for column in line.split('|')]
        command = columns[1] if len(columns) > 1 else ''
        if command not in commands_to_remove:
            filtered.append(line)
    return '\n'.join(filtered).strip()

def verify_build_options_table():
    """
    Verify that the BuildOptions.md table is up-to-date with @Option annotations.
    Uses comment markers like Truffle Options.md for clean verification.
    """
    # Path to BuildOptions.md
    docs_dir = os.path.join(mx.suite('substratevm').dir, '..', 'docs', 'reference-manual', 'native-image')
    build_options_file = os.path.join(docs_dir, 'BuildOptions.md')

    if not os.path.exists(build_options_file):
        mx.abort(f"BuildOptions.md not found at {build_options_file}")

    # Generate current options table
    mx.log("Generating current options table for verification...")
    generated_content = _generate_markdown_options_table()

    # Read existing BuildOptions.md and extract just the table section
    with open(build_options_file, 'r', encoding='utf-8') as f:
        content = f.read()

    # Look for auto-generation markers
    begin_marker = '<!-- BEGIN: build-options-table -->'
    end_marker = '<!-- END: build-options-table -->'

    begin_idx = content.find(begin_marker)
    end_idx = content.find(end_marker)

    if begin_idx == -1 or end_idx == -1:
        mx.log("No auto-generation markers found - using fallback method")
        # Fallback to old method
        table_start = content.find('| Command | Type | Description |')
        if table_start == -1:
            mx.abort("Could not find options table in BuildOptions.md")

        table_end = content.find('\n## ', table_start)
        if table_end == -1:
            table_end = len(content)

        existing_table = content[table_start:table_end].strip()
    else:
        # Extract content between markers
        existing_table = content[begin_idx + len(begin_marker):end_idx].strip()

    generated_table = _extract_table_from_markdown(generated_content)

    existing_normalized = _normalize_table(existing_table)
    generated_normalized = _normalize_table(generated_table)

    if existing_normalized == generated_normalized:
        mx.log("✓ BuildOptions.md table is up-to-date with @Option annotations")
        return True
    else:
        enterprise_suite_available = mx.suite('substratevm-enterprise', fatalIfMissing=False) is not None
        if not enterprise_suite_available:
            existing_commands = _get_option_commands(existing_table)
            generated_commands = _get_option_commands(generated_table)
            extra_in_existing = sorted(existing_commands - generated_commands)

            if extra_in_existing:
                mx.log("WARNING: BuildOptions.md contains options not available in this checkout (likely enterprise-only).")
                mx.log("WARNING: Running from graal/substratevm performs a best-effort check only.")
                preview = ', '.join(extra_in_existing[:8])
                if len(extra_in_existing) > 8:
                    preview += f", ... (+{len(extra_in_existing) - 8} more)"
                mx.log("WARNING: Enterprise-only option rows detected: " + preview)
                mx.log("✓ BuildOptions.md check passed in community compatibility mode")
                return True

        mx.log("ERROR: BuildOptions.md table is out of date!")
        mx.log("")
        mx.log("The table in BuildOptions.md does not match current @Option annotations.")
        mx.log("")
        mx.log("To fix this, run:")
        mx.log("  mx update-build-options-table")
        mx.log("")

        # Show a diff for debugging
        diff = difflib.unified_diff(
            existing_normalized.splitlines(keepends=True),
            generated_normalized.splitlines(keepends=True),
            fromfile='BuildOptions.md (existing)',
            tofile='Generated from @Option annotations',
            n=3
        )

        diff_lines = list(diff)
        if diff_lines:
            mx.log("Differences found:")
            for line in diff_lines[:20]:  # Show first 20 lines of diff
                mx.log(line.rstrip())
            if len(diff_lines) > 20:
                mx.log(f"... and {len(diff_lines) - 20} more lines")

        mx.log("")
        mx.log("Suggested actions:")
        mx.log("1. Run 'mx update-build-options-table' to auto-update the table")
        mx.log("2. Replace the table section in BuildOptions.md with the generated content")
        mx.log("")
        return False


def update_build_options_table():
    """
    Automatically update BuildOptions.md with the current options from @Option annotations.
    Uses comment markers like Truffle Options.md for clean auto-generation.
    """
    # Path to BuildOptions.md
    docs_dir = os.path.join(mx.suite('substratevm').dir, '..', 'docs', 'reference-manual', 'native-image')
    build_options_file = os.path.join(docs_dir, 'BuildOptions.md')

    if not os.path.exists(build_options_file):
        mx.abort(f"BuildOptions.md not found at {build_options_file}")

    # Generate current options table
    mx.log("Generating updated options table...")
    generated_content = _generate_markdown_options_table()

    # Read existing BuildOptions.md
    with open(build_options_file, 'r', encoding='utf-8') as f:
        content = f.read()

    # Look for auto-generation markers (like Truffle uses)
    begin_marker = '<!-- BEGIN: build-options-table -->'
    end_marker = '<!-- END: build-options-table -->'

    begin_idx = content.find(begin_marker)
    end_idx = content.find(end_marker)

    if begin_idx == -1 or end_idx == -1:
        # Fallback: try to find existing table and suggest adding markers
        table_start = content.find('| Command | Type | Description |')
        if table_start == -1:
            mx.abort("Could not find table or auto-generation markers in BuildOptions.md")

        mx.log("Found existing table but no auto-generation markers.")
        mx.log("Consider adding comment markers around the table:")
        mx.log(f"  {begin_marker}")
        mx.log("  [existing table content]")
        mx.log(f"  {end_marker}")

        # For now, use the old method
        table_end = content.find('\n## ', table_start)
        if table_end == -1:
            table_end = len(content)

        # Extract table from generated content (skip markdown header)
        new_table = _extract_table_from_markdown(generated_content)
        new_content = content[:table_start] + new_table + content[table_end:]
    else:
        # Use marker-based replacement (preferred method)
        mx.log("Found auto-generation markers - using clean replacement")

        # Extract table content from generated markdown
        table_content = _extract_table_from_markdown(generated_content)

        # Replace content between markers
        new_content = (content[:begin_idx + len(begin_marker)] +
                   '\n' + table_content + '\n' +
                   content[end_idx:])

    # Write updated content
    with open(build_options_file, 'w', encoding='utf-8') as f:
        f.write(new_content)

    mx.log("✓ Updated BuildOptions.md table with current options")
    return True
