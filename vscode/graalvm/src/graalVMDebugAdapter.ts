/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import { ChromeDebugAdapter, ChromeDebugSession, Crdp, ErrorWithMessage, logger } from 'vscode-chrome-debug-core';
import { DebugProtocol } from 'vscode-debugprotocol';
import { OutputEvent } from 'vscode-debugadapter';

import * as path from 'path';
import * as cp from 'child_process';
import * as os from 'os';

import { ILaunchRequestArguments, IAttachRequestArguments } from './graalVMDebugInterfaces';

export class GraalVMDebugAdapter extends ChromeDebugAdapter {
    private static TIMEOUT = 5000;

    private _childProcessId: number = 0;
    private _supportsRunInTerminalRequest: boolean | undefined;
    private _lastEarlyNodeMsgSeen: boolean | undefined;
    private _captureStdOutput: boolean | undefined;
    private _killChildProcess: boolean = true;

    public initialize(args: DebugProtocol.InitializeRequestArguments): DebugProtocol.Capabilities {
        this._supportsRunInTerminalRequest = args.supportsRunInTerminalRequest;
        const capabilities = super.initialize(args);
        return capabilities;
    }

    public async launch(args: ILaunchRequestArguments): Promise<void> {
        if (!args.breakOnLoadStrategy) {
            args.breakOnLoadStrategy = 'regex';
        }
        if (args.console && args.console !== 'internalConsole' && typeof args._suppressConsoleOutput === 'undefined') {
            args._suppressConsoleOutput = true;
        }

        await super.launch(args);

        if (args.graalVMLaunchInfo.args.find(arg => arg.startsWith('--lsp'))) {
            this._killChildProcess = false;
        }

        this._captureStdOutput = args.outputCapture === 'std';

        if (args.console === 'integratedTerminal' && this._supportsRunInTerminalRequest) {
            const termArgs: DebugProtocol.RunInTerminalRequestArguments = {
                kind: args.console === 'integratedTerminal' ? 'integrated' : 'external',
                title: 'GraalVM Debug Console',
                cwd: args.graalVMLaunchInfo.cwd,
                args: [args.graalVMLaunchInfo.exec].concat(args.graalVMLaunchInfo.args || [])
            };
            await this.launchInTerminal(termArgs);
            if (args.noDebug) {
                this.terminateSession('cannot track process');
            }
        } else if (!args.console || args.console === 'internalConsole') {
            await this.launchInInternalConsole(args.graalVMLaunchInfo.exec, args.graalVMLaunchInfo.args, args.graalVMLaunchInfo.cwd);
        } else {
            throw new ErrorWithMessage({
                id: 2028,
                format: "Unknown console type '{consoleType}'.",
                variables: { consoleType: args.console }
            });
        }

        if (!this._killChildProcess && this._childProcessId > 0) {
            this._session.sendEvent(new OutputEvent('childProcessID', 'telemetry', {'pid': this._childProcessId}));
        }

        if (!args.noDebug) {
            await this.doAttach(args.graalVMLaunchInfo.port, undefined, args.address, args.timeout, undefined, args.extraCRDPChannelPort);
            this._session.sendEvent(new OutputEvent('Debugger attached.\n', 'stderr'));
        }
    }

    public async attach(args: IAttachRequestArguments): Promise<void> {
        try {
            if (!args.breakOnLoadStrategy) {
                args.breakOnLoadStrategy = 'regex';
            }
            if (typeof args.enableSourceMapCaching !== 'boolean') {
                args.enableSourceMapCaching = true;
            }
            return super.attach(args);
        } catch (err) {
            if (err.format && err.format.indexOf('Cannot connect to runtime process') >= 0) {
                err.format = 'Ensure GraalVM was launched with --inspect. ' + err.format;
            }
            throw err;
        }
    }

    public async terminateSession(reason: string): Promise<void> {
        this.killChildProcess();
        return super.terminateSession(reason);
    }

    private killChildProcess(): void {
        if (this._killChildProcess && this._childProcessId > 0 && !this._attachMode) {
            const groupPID = -this._childProcessId;
            try {
                process.kill(groupPID, 'SIGINT');
            } catch (e) {
                if (e.message === 'kill ESRCH') {
                    try {
                        process.kill(this._childProcessId, 'SIGINT');
                    } catch (e) {}
                }
            }
            this._childProcessId = 0;
        }
    }

    private launchInTerminal(termArgs: DebugProtocol.RunInTerminalRequestArguments): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this._session.sendRequest('runInTerminal', termArgs, GraalVMDebugAdapter.TIMEOUT, response => {
                if (response.success) {
                    resolve();
                } else {
                    reject(new ErrorWithMessage({
                        id: 2011,
                        format: 'Cannot launch debug target in terminal ({_error}).',
                        variables: { _error: response.message || '' }
                    }));
                    this.terminateSession('terminal error: ' + response.message);
                }
            });
        });
    }

    private launchInInternalConsole(runtimeExecutable: string, launchArgs: string[], cwd?: string): Promise<void> {
        const spawnOpts: cp.SpawnOptions = { cwd, env: process.env, detached: true };
        this.logLaunchCommand(runtimeExecutable, launchArgs);
        const childProcess = cp.spawn(runtimeExecutable, launchArgs, spawnOpts);
        return new Promise<void>((resolve, reject) => {
            this._childProcessId = childProcess.pid;
            childProcess.on('error', (error) => {
                reject(new ErrorWithMessage({
                    id: 2017,
                    format: 'Cannot launch debug target ({_error}).',
                    variables: { _error : error.toString() },
                    showUser: true,
                    sendTelemetry: true
                }));
                const msg = `Child process error: ${error}`;
                logger.error(msg);
                this.terminateSession(msg);
            });
            childProcess.on('exit', () => {
                const msg = 'Target exited';
                logger.log(msg);
                this.terminateSession(msg);
            });
            childProcess.on('close', () => {
                const msg = 'Target closed';
                logger.log(msg);
                this.terminateSession(msg);
            });
            const noDebugMode = (<ILaunchRequestArguments>this._launchAttachArgs).noDebug;
            if (childProcess.stdout) {
                childProcess.stdout.on('data', (data: string) => {
                    if ((noDebugMode || this._captureStdOutput) && !this._launchAttachArgs._suppressConsoleOutput) {
                        let msg = data.toString();
                        this._session.sendEvent(new OutputEvent(msg, 'stdout'));
                    }
                });
            }
            if (childProcess.stderr) {
                childProcess.stderr.on('data', (data: string) => {
                    let msg = data.toString();
                    if (!this._lastEarlyNodeMsgSeen && !noDebugMode) {
                        msg = msg.replace(/^\s*To start debugging, open the following URL in Chrome:\s*$/m, '');
                        let regExp = /^\s*chrome-devtools:\/\/devtools\/bundled\/js_app\.html\?ws=\S*\s*$/m;
                        if (msg.match(regExp)) {
                            msg = msg.replace(regExp, '');
                            this._lastEarlyNodeMsgSeen = true;
                        }
                    }
                    if ((noDebugMode || this._captureStdOutput) && !this._launchAttachArgs._suppressConsoleOutput) {
                        this._session.sendEvent(new OutputEvent(msg, 'stderr'));
                    }
                });
            }
            resolve();
         });
    }

    protected onConsoleAPICalled(params: Crdp.Runtime.ConsoleAPICalledEvent): void {
        this._lastEarlyNodeMsgSeen = true;
        if (!this._captureStdOutput) {
            super.onConsoleAPICalled(params);
        }
    }

    private logLaunchCommand(executable: string, args: string[]) {
        let cli = executable + ' ';
        for (let a of args) {
            if (a.indexOf(' ') >= 0) {
                cli += '\'' + a + '\'';
            } else {
                cli += a;
            }
            cli += ' ';
        }
        logger.warn(cli);
    }
}

ChromeDebugSession.run(ChromeDebugSession.getSession({
    adapter: GraalVMDebugAdapter,
    extensionName: 'graalvm',
    logFilePath: path.resolve(os.tmpdir(), 'vscode-graalvm-debug.txt'),
}));
