#
# Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
# pylint: skip-file
from __future__ import absolute_import, division, print_function

import logging
import sys
import re
import gdb

logging.basicConfig(format='%(name)s %(levelname)s: %(message)s')
logger = logging.getLogger('[DebugTest]')


int_rexp = re.compile(r"\d+")
float_rexp = re.compile(r"\d+(.\d+)?")
char_rexp = re.compile(r"'(.|\\.+)'")
string_rexp = re.compile(r'(null|".*")')
boolean_rexp = re.compile(r"(true|false)")
array_rexp = re.compile(r'.+\[\d+]\s*=\s*{.*}')
args_rexp = re.compile(r'.*\(.*\)\s*\((?P<args>.*)\)')
hex_rexp = re.compile(r"[\da-fA-F]+")


def gdb_execute(command: str) -> str:
    gdb.flush()
    logger.info(f'(gdb) {command}')
    exec_string = gdb.execute(command, False, True)
    try:
        gdb.execute("py SVMUtil.prompt_hook()", False, False)  # inject prompt hook for autonomous tests (will otherwise not be called)
    except gdb.error:
        logger.debug("Could not execute prompt hook (SVMUtil was not yet loaded)")  # SVMUtil not yet loaded
    gdb.flush()
    return exec_string


def clear_pretty_printers() -> None:
    logger.info('Clearing pretty printers from objfiles')
    for objfile in gdb.objfiles():
        objfile.pretty_printers = []
    try:
        gdb_execute('py SVMUtil.pretty_print_objfiles.clear()')  # SVMUtil is not visible here - let gdb handle this
    except gdb.error:
        logger.debug("SVMUtil was not yet loaded")  # SVMUtil not yet loaded


def gdb_reload_executable() -> None:
    logger.info('Reloading main executable')
    filename = gdb.objfiles()[0].filename
    gdb_execute('file')  # remove executable to clear symbols
    gdb_execute(f'file {filename}')


def gdb_output(var: str, output_format: str = None) -> str:
    logger.info(f'Print variable {var}')
    return gdb_execute('output{} {}'.format("" if output_format is None else "/" + output_format, var))


def gdb_print(var: str, output_format: str = None) -> str:
    logger.info(f'Print variable {var}')
    return gdb_execute('print{} {}'.format("" if output_format is None else "/" + output_format, var))


def gdb_advanced_print(var: str, output_format: str = None) -> str:
    logger.info(f'Print variable {var}')
    return gdb_execute('p{} {}'.format("" if output_format is None else "/" + output_format, var))


def gdb_set_breakpoint(location: str) -> None:
    logger.info(f"Setting breakpoint at: {location}")
    gdb_execute(f"break {location}")


def gdb_set_param(name: str, value: str) -> None:
    logger.info(f"Setting parameter '{name}' to '{value}'")
    gdb.set_parameter(name, value)


def gdb_get_param(name: str) -> str:
    logger.info(f"Fetching parameter '{name}'")
    return gdb.parameter(name)


def gdb_delete_breakpoints() -> None:
    logger.info("Deleting all breakpoints")
    gdb_execute("delete breakpoints")


def gdb_run() -> None:
    logger.info('Run current program')
    gdb_execute('run')


def gdb_start() -> None:
    logger.info('start current program')
    gdb_execute('start')


def gdb_kill() -> None:
    logger.info('Kill current program')
    try:
        gdb_execute('kill')
    except gdb.error:
        pass  # no running program


def gdb_continue() -> None:
    logger.info('Continue current program')
    gdb_execute('continue')


def gdb_step() -> None:
    logger.info('Sourceline STEP')
    gdb_execute('step')


def gdb_next() -> None:
    logger.info('Sourceline NEXT')
    gdb_execute('next')


def gdb_step_i() -> None:
    logger.info('Machine instruction STEP')
    gdb_execute('stepi')


def gdb_next_i() -> None:
    logger.info('Machine instruction NEXT')
    gdb_execute('nexti')


def gdb_finish() -> None:
    logger.info('Function FINISH')
    gdb_execute('finish')


def set_up_test() -> None:
    logger.info('Set up gdb')
    # gdb setup
    gdb.set_parameter('print array', 'off')  # enforce compact format
    gdb.set_parameter('print pretty', 'off')  # enforce compact format
    gdb.set_parameter('confirm', 'off')  # ensure we can exit easily
    gdb.set_parameter('height', 'unlimited')  # sane console output
    gdb.set_parameter('width', 'unlimited')
    gdb.set_parameter('pagination', 'off')
    gdb.set_parameter('overload-resolution', 'off')


def set_up_gdb_debughelpers() -> None:
    logger.info('Set up gdb-debughelpers')
    # gdb-debughelpers setup
    gdb.set_parameter('svm-print', 'on')
    gdb.set_parameter('svm-print-string-limit', '200')
    gdb.set_parameter('svm-print-element-limit', '10')
    gdb.set_parameter('svm-print-field-limit', '50')
    gdb.set_parameter('svm-print-depth-limit', '1')
    gdb.set_parameter('svm-use-hlrep', 'on')
    gdb.set_parameter('svm-infer-generics', '10')
    gdb.set_parameter('svm-print-address', 'off')
    gdb.set_parameter('svm-selfref-check', 'on')
    gdb.set_parameter('svm-print-static-fields', 'off')
    gdb.set_parameter('svm-complete-static-variables', 'off')

    # set gdb limits for gdb-debughelpers
    gdb.set_parameter('print max-depth', '10')
    gdb.set_parameter('print elements', '100')
    gdb.set_parameter('print characters', '200')


def tear_down_test() -> None:
    logger.info('Tear down')
    sys.exit(0)


def dump_debug_context() -> None:
    logger.info('Dump Debugger Context Begin')
    gdb_execute('print $pc')
    gdb_execute('info scope *$pc')
    gdb_execute('info args')
    gdb_execute('info locals')
    gdb_execute('list')
    gdb_execute('disassemble')
    logger.info('Dump Debugger Context End')
