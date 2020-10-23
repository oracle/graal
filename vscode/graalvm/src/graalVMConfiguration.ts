/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';

import { checkForMissingComponents, getInstallConfigurations } from './graalVMInstall';
import { getPythonConfigurations } from './graalVMPython';
import { getRConfigurations } from './graalVMR';
import { getRubyConfigurations } from './graalVMRuby';

let configurations: ConfigurationPickItem[];

export function getConf(key: string): vscode.WorkspaceConfiguration {
	return vscode.workspace.getConfiguration(key);
}

export function getGVMConfig(gvmConfig?: vscode.WorkspaceConfiguration): vscode.WorkspaceConfiguration {
	if (!gvmConfig) {
		gvmConfig = getConf('graalvm');
	}
	return gvmConfig;
}

export function getGVMHome(gvmConfig?: vscode.WorkspaceConfiguration): string {
	return getGVMConfig(gvmConfig).get('home') as string;
}

export async function setGVMHome(graalVMHome: string | undefined, gvmConfig?: vscode.WorkspaceConfiguration): Promise<void> {
	return getGVMConfig(gvmConfig).update('home', graalVMHome, true);
}

const CONFIG_INSTALLATIONS = 'installations';
export function getGVMInsts(gvmConfig?: vscode.WorkspaceConfiguration): string[] {
	return getGVMConfig(gvmConfig).get(CONFIG_INSTALLATIONS) as string[];
}

export function setGVMInsts(gvmConfig: vscode.WorkspaceConfiguration, installations: string[]): Thenable<void> {
	return gvmConfig.update(CONFIG_INSTALLATIONS, installations, true);
}

export async function configureGraalVMHome(graalVMHome: string) {
    gatherConfigurations();
    const gr = getGVMConfig();
    if (graalVMHome !== getGVMHome(gr)) {
        await setGVMHome(graalVMHome, gr);
        checkForMissingComponents(graalVMHome);
        return;        
    }

    await defaultConfig(graalVMHome, gr);
    
    const selected: ConfigurationPickItem[] = await vscode.window.showQuickPick(
        configurations.filter(conf => conf.show(graalVMHome)),
        {canPickMany: true, placeHolder: 'Configure active GraalVM'}) || [];

    selected.forEach(select => select.set(graalVMHome));
}

function gatherConfigurations() {
    if (configurations) {
        return;
    }
    configurations = getInstallConfigurations().concat(
        getPythonConfigurations(), 
        getRubyConfigurations(),
        getRConfigurations());
}

async function defaultConfig(graalVMHome: string, gr: vscode.WorkspaceConfiguration) {
    const insts = getGVMInsts(gr);
    if (!insts.includes(graalVMHome)) {
        insts.push(graalVMHome);
        await setGVMInsts(gr, insts);
    }

    const termConfig = getConf('terminal.integrated');
    let section: string = '';
    if (process.platform === 'linux') {
        section = 'env.linux';
    } else if (process.platform === 'darwin') {
        section = 'env.mac';
    } else if (process.platform === 'win32') {
        section = 'env.windows';
    }

    let env: any = termConfig.get(section);
    env.GRAALVM_HOME = graalVMHome;
    await termConfig.update(section, env, true);
}

export class ConfigurationPickItem implements vscode.QuickPickItem {
    public picked?: boolean;
    public detail?: string;
	constructor (
        public readonly label: string,
        public readonly description: string,
        public readonly show: (graalVMHome: string) => boolean,
        public readonly set: ((graalVMHome: string) => Promise<any>)
	) {}
}