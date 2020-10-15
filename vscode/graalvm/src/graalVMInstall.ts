/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as https from 'https';
import * as cp from 'child_process';
import * as decompress from 'decompress';
import * as utils from './utils';

const GITHUB_URL: string = 'https://github.com';
const GRAALVM_RELEASES_URL: string = GITHUB_URL + '/graalvm/graalvm-ce-builds/releases';
const GRAALVM_DEV_RELEASES_URL: string = GITHUB_URL + '/graalvm/graalvm-ce-dev-builds/releases';
const LINUX_LINK_REGEXP: RegExp = /<a href="\/graalvm\/graalvm-\S*-builds\/releases\/download\/\S*\/graalvm-ce-java\S*-linux-amd64-\S*"/gmi;
const MAC_LINK_REGEXP: RegExp = /<a href="\/graalvm\/graalvm-\S*-builds\/releases\/download\/\S*\/graalvm-ce-java\S*-(darwin|macos)-amd64-\S*"/gmi;
const WINDOWS_LINK_REGEXP: RegExp = /<a href="\/graalvm\/graalvm-\S*-builds\/releases\/download\/\S*\/graalvm-ce-java\S*-windows-amd64-\S*"/gmi;
const YES: string = 'Yes';
const NO: string = 'No';
const INSTALL: string = 'Install ';
const OPTIONAL_COMPONENTS: string = 'Optional GraalVM Components';

const CONFIG_INSTALLATIONS = 'installations';
function getGVMInsts(gvmConfig?: vscode.WorkspaceConfiguration): string[] {
	return utils.getGVMConfig(gvmConfig).get(CONFIG_INSTALLATIONS) as string[];
}

function setGVMInsts(gvmConfig: vscode.WorkspaceConfiguration, installations: string[]): Thenable<void> {
	return gvmConfig.update(CONFIG_INSTALLATIONS, installations, true);
}

async function getGU(graalVMHome?: string): Promise<string> {
    graalVMHome = graalVMHome || utils.getGVMHome();
    if (graalVMHome) {
        if (! await getGraalVMVersion(graalVMHome)) {
            throw new Error(`Missing GraalVM Installation. ${graalVMHome}`);
        }
    }
    const executablePath = utils.findExecutable('gu', graalVMHome);
    if (executablePath) {
        return executablePath;
    }
    throw new Error("Cannot find runtime 'gu' within your GraalVM installation.");
}

export async function installGraalVM(storagePath?: string, GVMpath?: string): Promise<void> {
    try {
        const selected = await selectGraalVMRelease(storagePath, await getGraalVMReleases(), GVMpath);
        if (selected) {
            const downloadedFile = await dowloadGraalVMRelease(selected.url, selected.location);
            const targetDir = path.dirname(downloadedFile);
            const name = await extractGraalVM(downloadedFile, targetDir, GVMpath ? 1 : 0);
            fs.unlinkSync(downloadedFile);
            if (name) {
                let graalVMHome = GVMpath ? GVMpath : path.join(targetDir, name);
                if (process.platform === 'darwin') {
                    graalVMHome = path.join(graalVMHome, 'Contents', 'Home');
                }
                updateGraalVMLocations(graalVMHome);
                checkForMissingComponents(graalVMHome);
            }
        }
    } catch (err) {
        vscode.window.showErrorMessage(err.message);
    }
}

function _callIdGVMHome(component: string | Component, homeFolder: string | undefined, fnc: (id: string, graalVMHome: string) => Promise<void>): Promise<void>{
    if (component instanceof Component) {
        return fnc(component.componentId, component.installation.home);
    } else {
        return fnc(component, homeFolder || utils.getGVMHome());
    }
}

function _createFSWatcher(graalVMHome: string) {
    const watcher: fs.FSWatcher = fs.watch(path.join(graalVMHome, 'bin'), () => {
        if (graalVMHome === utils.getGVMHome()) {
            vscode.commands.executeCommand('extension.graalvm.refreshLanguageConfigurations');
        }
        vscode.commands.executeCommand('extension.graalvm.refreshInstallations');
        watcher.close();
    });
}

export async function installGraalVMComponent(component: string | Component, homeFolder?: string): Promise<void> {
    _callIdGVMHome(component, homeFolder, _installGraalVMComponent);
}

export async function uninstallGraalVMComponent(component: string | Component, homeFolder?: string): Promise<void> {
    _callIdGVMHome(component, homeFolder, _uninstallGraalVMComponent);
}

async function _installGraalVMComponent(componentId: string | undefined, graalVMHome: string): Promise<void> {
    changeGraalVMComponent(graalVMHome, componentId ? [componentId] : await selectAvailableComponents(graalVMHome), 'install');
    _createFSWatcher(graalVMHome);
}

async function _uninstallGraalVMComponent(componentId: string | undefined, graalVMHome: string): Promise<void> {
    changeGraalVMComponent(graalVMHome, componentId ? [componentId] : await selectInstalledComponents(graalVMHome), 'remove');
    _createFSWatcher(graalVMHome);
}

async function changeGraalVMComponent(graalVMHome: string, componentIds: string[], action: string): Promise<void> {
    const executablePath = await getGU(graalVMHome);
    let terminal: vscode.Terminal | undefined = vscode.window.activeTerminal;
    if (!terminal) {
        terminal = vscode.window.createTerminal();
    }
    terminal.show();
    const exec = executablePath.replace(/(\s+)/g, '\\$1');
    const proxy = utils.getConf('http').get('proxy') as string;
    if (proxy) {
        terminal.sendText(componentIds.map(id => `env http_proxy=${proxy} ${exec} ${action} ${id}`).join(';'));
    } else {
        terminal.sendText(componentIds.map(id => `${exec} ${action} ${id}`).join(';'));
    }
}

export async function addExistingGraalVM(): Promise<void> {
    const uri = await vscode.window.showOpenDialog({
        canSelectMany: false,
        canSelectFiles: false,
        canSelectFolders: true,
        openLabel: 'Add GraalVM installation'
    });
    if (uri && uri.length === 1) {
        const graalVMHome = uri[0].fsPath;
        if (graalVMHome) {
            updateGraalVMLocations(graalVMHome);
            checkForMissingComponents(graalVMHome);
        }
    } else {
        throw new Error('No GraalVM Installation selected.');
    }
}

export async function selectInstalledGraalVM(graalVMHome?: string): Promise<void> {
    if (!graalVMHome) {
        const vms: vscode.QuickPickItem[] = (await findGraalVMs()).map(item => {
            return {label: item.name, detail: item.path};
        });
        const selected = await vscode.window.showQuickPick(vms, { matchOnDetail: true, placeHolder: 'Select GraalVM Home' });
        if (selected) {
            graalVMHome = selected.detail;
        }
    }
    if (graalVMHome) {
        const graalVMVersion = await getGraalVMVersion(graalVMHome);
        if (graalVMVersion) {
            const gr = utils.getGVMConfig();
            await gr.update('home', graalVMHome, true);
            const insts = getGVMInsts(gr);
            if (!insts.includes(graalVMHome)) {
                insts.unshift(graalVMHome);
                await setGVMInsts(gr, insts);
            }
            vscode.window.showInformationMessage(`Set "${graalVMVersion}" as default Java?`, YES, NO).then(select => {
                if(select === YES){
                    utils.getConf('java').update('home', graalVMHome, true);
                }
            });
            checkForMissingComponents(graalVMHome);
        }
    }
}

export async function getGraalVMVersion(homeFolder: string): Promise<string | undefined> {
    return new Promise<string | undefined>(resolve => {
        if (homeFolder && fs.existsSync(homeFolder)) {
            const executable: string | undefined = utils.findExecutable('java', homeFolder);
            if (executable) {
                cp.execFile(executable, ['-version'], { encoding: 'utf8' }, (_error, _stdout, stderr) => {
                    if (stderr) {
                        let javaVersion: string | undefined;
                        let graalVMVersion: string | undefined;
                        stderr.split('\n').forEach((line, idx) => {
                            switch (idx) {
                                case 0:
                                    const javaInfo: string[] | null = line.match(/version\s+\"(\S+)\"/);
                                    if (javaInfo && javaInfo.length > 1) {
                                        javaVersion = javaInfo[1];
                                    }
                                    break;
                                case 2:
                                    const vmInfo = line.match(/(GraalVM.*)\s+\(/);
                                    if (vmInfo && vmInfo.length > 1) {
                                        graalVMVersion = vmInfo[1];
                                    }
                                    break;
                            }
                        });
                        if (javaVersion && graalVMVersion) {
                            if (javaVersion.startsWith('1.')) {
                                javaVersion = javaVersion.slice(2);
                            }
                            let i = javaVersion.indexOf('.');
                            javaVersion = javaVersion.slice(0, i);
                            resolve(`${graalVMVersion}, Java ${javaVersion}`);
                        } else {
                            resolve();
                        }
                    } else {
                        resolve();
                    }
                });
            } else {
                resolve();
            }
        } else {
            resolve();
        }
    });
}

async function getGraalVMReleases(): Promise<any> {
    return Promise.all([
        getGraalVMReleaseURLs(GRAALVM_RELEASES_URL),
        getGraalVMReleaseURLs(GRAALVM_DEV_RELEASES_URL)
    ]).then(urls => {
        const merged: string[] = Array.prototype.concat.apply([], urls);
        if (merged.length === 0) {
            throw new Error(`No GraalVM installable found for platform ${process.platform}`);
        }
        const releases: any = {
            ce: {}
        };
        merged.forEach(releaseUrl => {
            const version: string[] | null = releaseUrl.match(/\d+\.\d+\.\d+(-dev)?/);
            if (version && version.length > 0) {
                const graalvmVarsion: string = version[0];
                let releasesVersion = releases.ce[graalvmVarsion];
                if (!Object.keys(releases.ce).find(key => graalvmVarsion.endsWith('-dev') ? key.endsWith('-dev') : graalvmVarsion.slice(0, 2) === key.slice(0, 2))) {
                    releases.ce[graalvmVarsion] = releasesVersion = {};
                }
                if (releasesVersion) {
                    const javaVersion: string[] | null = releaseUrl.match(/java(\d+)/);
                    if (javaVersion && javaVersion.length > 0) {
                        let releasesJavaVersion = releasesVersion[javaVersion[0]];
                        if (!releasesJavaVersion) {
                            releasesVersion[javaVersion[0]] = releaseUrl;
                        }
                    }
                }
            }
        });
        return releases;
    });
}

async function getGraalVMReleaseURLs(releasesURL: string): Promise<string[]> {
    return new Promise<string[]>((resolve, reject) => {
        https.get(releasesURL, res => {
            const { statusCode } = res;
            const contentType = res.headers['content-type'] || '';
            let error;
            if (statusCode !== 200) {
                error = new Error(`Request Failed.\nStatus Code: ${statusCode}`);
            } else if (!/^text\/html/.test(contentType)) {
                error = new Error(`Invalid content-type.\nExpected text/html but received ${contentType}`);
            }
            if (error) {
                reject(error);
                res.resume();
            } else {
                let rawData: string = '';
                res.on('data', chunk => { rawData += chunk; });
                res.on('end', () => {
                    let regex;
                    if (process.platform === 'linux') {
                        regex = LINUX_LINK_REGEXP;
                    } else if (process.platform === 'darwin') {
                        regex = MAC_LINK_REGEXP;
                    } else if (process.platform === 'win32') {
                        regex = WINDOWS_LINK_REGEXP;
                    }
                    const ret = [];
                    if (regex) {
                        let match;
                        while ((match = regex.exec(rawData)) !== null) {
                            ret.push(GITHUB_URL + match[0].substring(9, match[0].length - 1));
                        }
                    }
                    resolve(ret);
                });
            }
        }).on('error', e => {
            reject(e);
        }).end();
    });
}

async function selectGraalVMRelease(defaultLocation: string | undefined, releaseInfos: any, GVMpath?: string): Promise<{url: string, location: string} | undefined> {

    interface State {
		title: string;
		step: number;
		totalSteps: number;
		graalVMVersion: vscode.QuickPickItem;
		javaVersion: vscode.QuickPickItem;
		location: string;
	}

	async function collectInputs() {
		const state = {} as Partial<State>;
		await utils.MultiStepInput.run(input => pickGraalVMVersion(input, state));
		return state as State;
	}

	const title = 'Download & Install GraalVM';

	async function pickGraalVMVersion(input: utils.MultiStepInput, state: Partial<State>) {
		state.graalVMVersion = await input.showQuickPick({
			title,
			step: 1,
			totalSteps: GVMpath ? 2 : 3,
			placeholder: 'Pick a GraalVM version',
			items: Object.keys(releaseInfos.ce).map(label => ({ label })),
			activeItem: state.graalVMVersion,
			shouldResume: () => Promise.resolve(false)
		});
		return (input: utils.MultiStepInput) => pickJavaVersion(input, state);
	}

	async function pickJavaVersion(input: utils.MultiStepInput, state: Partial<State>) {
		state.javaVersion = await input.showQuickPick({
			title,
			step: 2,
			totalSteps: GVMpath ? 2 : 3,
			placeholder: 'Pick a Java version',
			items: state.graalVMVersion ? Object.keys(releaseInfos.ce[state.graalVMVersion.label]).map(label => ({ label })) : [],
			activeItem: state.javaVersion,
			shouldResume: () => Promise.resolve(false)
		});
		return (input: utils.MultiStepInput) => location(input, state);
	}

	async function location(input: utils.MultiStepInput, state: Partial<State>) {
		state.location = GVMpath ? GVMpath : await input.showInputBox({
			title,
			step: 3,
			totalSteps: 3,
			value: state.location || (defaultLocation ? defaultLocation : ''),
			prompt: 'Select destination folder',
			validate: () => Promise.resolve(undefined),
			shouldResume: () => Promise.resolve(false)
		});
	}

    const state = await collectInputs();
    return state.graalVMVersion && state.javaVersion && state.location ? {url: releaseInfos.ce[state.graalVMVersion.label][state.javaVersion.label], location: state.location} : undefined;
}

async function dowloadGraalVMRelease(releaseURL: string, storagePath: string | undefined): Promise<string> {
    const base: string = path.basename(releaseURL);
    return vscode.window.withProgress<string>({
        location: vscode.ProgressLocation.Notification,
        title: `Downloading ${base} ...`,
        cancellable: true
    }, (progress, token) => {
        return new Promise<string>((resolve, reject) => {
            if (storagePath) {
                fs.mkdirSync(storagePath, {recursive: true});
                const filePath: string = path.join(storagePath, base);
                const file: fs.WriteStream = fs.createWriteStream(filePath);
                const request = function (url: string) {
                    https.get(url, res => {
                        const { statusCode } = res;
                        if (statusCode === 302) {
                            if (res.headers.location) {
                                request(res.headers.location);
                            }
                        } else {
                            let error;
                            const contentType = res.headers['content-type'] || '';
                            const length = parseInt(res.headers['content-length'] || '0');
                            if (statusCode !== 200) {
                                error = new Error(`Request Failed.\nStatus Code: ${statusCode}`);
                            } else if (!/^application\/octet-stream/.test(contentType)) {
                                error = new Error(`Invalid content-type.\nExpected text/html but received ${contentType}`);
                            }
                            if (error) {
                                reject(error);
                                res.resume();
                            } else {
                                token.onCancellationRequested(() => {
                                    reject();
                                    res.destroy();
                                    fs.unlinkSync(filePath);
                                });
                                res.pipe(file);
                                if (length) {
                                    const percent = length / 100;
                                    let counter = 0;
                                    let progressCounter = 0;
                                    res.on('data', chunk => {
                                        counter += chunk.length;
                                        let f = Math.floor(counter / percent);
                                        if (f > progressCounter) {
                                            progress.report({ increment: f - progressCounter });
                                            progressCounter = f;
                                        }
                                    });
                                }
                                res.on('end', () => {
                                    resolve(filePath);
                                });
                            }
                        }
                    }).on('error', e => {
                        reject(e);
                    });
                };
                request(releaseURL);
            }
        });
    });
}

async function extractGraalVM(downloadedFile: string, targetDir: string, depth: number = 0): Promise<string | undefined> {
    return vscode.window.withProgress<string | undefined>({
        location: vscode.ProgressLocation.Notification,
        title: "Installing GraalVM..."
    }, async (_progress, _token) => {
        const files = await decompress(downloadedFile, targetDir, {strip: depth });
        if (files.length === 0) {
            return undefined;
        }
        const idx = files[0].path.indexOf('/');
        return idx < 0 ? files[0].path : files[0].path.slice(0, idx);
    });
}

export function deleteFolder(folder: string) {
    if (fs.existsSync(folder)) {
        fs.readdirSync(folder).forEach((file, _index) => {
            var curPath: string = path.join(folder, file);
            if (fs.lstatSync(curPath).isDirectory()) {
                deleteFolder(curPath);
            } else {
                fs.unlinkSync(curPath);
            }
        });
        fs.rmdirSync(folder);
    }
}

export async function findGraalVMs(): Promise<{name: string, path: string}[]> {
    const paths: string[] = [];
    addPathToJava(path.normalize(utils.getGVMHome()), paths);
    const installations = getGVMInsts().map(inst => path.normalize(inst));
    installations.forEach(installation => addPathToJava(installation, paths, true));
    addPathsToJavaIn('/opt', paths);
    if (process.env.GRAALVM_HOME) {
        addPathToJava(path.normalize(process.env.GRAALVM_HOME), paths);
    }
    if (process.env.JAVA_HOME) {
        addPathToJava(path.normalize(process.env.JAVA_HOME), paths);
    }
    if (process.env.PATH) {
        process.env.PATH.split(':')
            .filter(p => path.basename(p) === 'bin')
            .forEach(p => addPathToJava(path.dirname(p), paths));
    }
    const vms: {name: string, path: string}[] = [];
    for (let i = 0; i < paths.length; i++) {
        const version = await getGraalVMVersion(paths[i]);
        if (version) {
            vms.push({name: version, path: paths[i]});
        }
    }
    return vms;
}

export async function removeGraalVMInstallation(homeFolder?: string): Promise<number> {
    if (!homeFolder) {
        return -1;
    }
    const index = await _removeGraalVMInstallation(homeFolder);
    return vscode.window.showInformationMessage(`Do you want to delete GraalVM installation files from: ${homeFolder}`, YES, NO).then(selected => {
        if (selected === YES) {
            deleteFolder(homeFolder);
        }
    }).then(() => index);

}

async function _removeGraalVMInstallation(homeFolder: string): Promise<number> {
    const gr = utils.getGVMConfig();
    const installations = getGVMInsts(gr);
    const index = installations.indexOf(homeFolder);
    if (index > -1) {
        installations.splice(index, 1);
        await setGVMInsts(gr, installations);
    }
    const home = gr.get('home') as string;
    if (home === homeFolder) {
        await gr.update('home', undefined, true);
    }
    return index;
}

async function addGraalVMInstallation(homeFolder: string, index?: number): Promise<void> {
    const gr = utils.getGVMConfig();
    const installations = getGVMInsts(gr);
    if (installations.includes(homeFolder)) {
        return;
    }
    if (index && index > -1) {
        installations.splice(index, 0, homeFolder);  
    } else {
        installations.push(homeFolder);
    }
    await setGVMInsts(gr, installations);
}

const REINSTALL: string = 'Reinstall';
const CHANGE: string = 'Change';
const REMOVE: string = 'Remove';
export async function repairGraalVMInstallation(homeFolder?: string){
    if (homeFolder) {
        if (! await getGraalVMVersion(homeFolder)) {
            vscode.window.showInformationMessage(`GraalVM Installation not found at path: "${homeFolder}".`, REINSTALL, CHANGE, REMOVE).then(async out => {
                switch (out) {
                    case REINSTALL:
                        vscode.commands.executeCommand('extension.graalvm.installGraalVM', homeFolder).then(() =>
                        vscode.commands.executeCommand('extension.graalvm.refreshInstallations'));
                        break;
                    case CHANGE:
                        let index = -1;
                        removeGraalVMInstallation(homeFolder).then(async (ind) => {
                            index = ind;
                            await addExistingGraalVM();
                        }).then(() =>
                        vscode.commands.executeCommand('extension.graalvm.refreshInstallations')).catch(() => 
                        addGraalVMInstallation(homeFolder, index));
                        break;
                    case REMOVE:
                        removeGraalVMInstallation(homeFolder).then(() =>
                        vscode.commands.executeCommand('extension.graalvm.refreshInstallations'));
                        break;
                }
            });
        }
    } else {
		findGraalVMs().then(vms => {
			const filtered = vms.filter(vm => vm.name === 'Missing');
			if (filtered.length === 1) {
				filtered.forEach(vm => repairGraalVMInstallation(vm.path));
			} else if (filtered.length > 1) {
				vscode.window.showQuickPick(filtered.map(vm => vm.path), {placeHolder: 'Pick GraalVM Installation to Repair.' }).then(vm => {
					if (vm) {
						repairGraalVMInstallation(vm);
					}
				});
			}
		});
    }
}

function updateGraalVMLocations(homeFolder: string) {
    const gr = utils.getGVMConfig();
    const installations = getGVMInsts(gr);
    if (!installations.find(item => item === homeFolder)) {
        getGraalVMVersion(homeFolder).then(version => {
            if (version) {
                installations.push(homeFolder);
                setGVMInsts(gr, installations);
                const graalVMHome = utils.getGVMHome(gr);
                if (!graalVMHome) {
                    gr.update('home', homeFolder, true);

                } else if (graalVMHome !== homeFolder) {
                    vscode.window.showInformationMessage(`Set ${version} as active GraalVM?`, YES, NO).then(value => {
                        if (value === YES) {
                            selectInstalledGraalVM(homeFolder);
                        }
                    });
                }
            } else {
                vscode.window.showErrorMessage('Failed to add the selected GraalVM installation');
            }
        });
    }
}

async function checkForMissingComponents(homeFolder: string): Promise<void> {
    const available = await getAvailableComponents(homeFolder);
    const components = available.filter(availableItem => !availableItem.installed);
    if (components.length > 1) {
        const itemText = INSTALL + OPTIONAL_COMPONENTS;
        return vscode.window.showInformationMessage('Optional GraalVM components are not installed in your GraalVM.', itemText).then(value => {
            switch (value) {
                case itemText:
                    return vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', undefined, homeFolder);
            }
            return;
        });
    } else if (components.length === 1) {
        const itemText = INSTALL + components[0].detail;
        return vscode.window.showInformationMessage(components[0].detail + ' is not installed in your GraalVM.', itemText).then(value => {
            switch (value) {
                case itemText:
                    return vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', components[0].label, homeFolder);
            }
            return;
        });
    }
}

function addPathsToJavaIn(folder: string, paths: string[]) {
    if (folder && fs.existsSync(folder) && fs.statSync(folder).isDirectory) {
        fs.readdirSync(folder).map(f => path.join(folder, f)).map(p => {
            if (process.platform === 'darwin') {
                let homePath: string = path.join(p, 'Contents', 'Home');
                return fs.existsSync(homePath) ? homePath : p;
            }
            return p;
        }).filter(p => fs.statSync(p).isDirectory()).forEach(p => addPathToJava(p, paths));
    }
}

function addPathToJava(folder: string, paths: string[], removeOnEmpty: boolean = false) {
    if (!paths.find(p => p === folder)) {
        const executable: string | undefined = utils.findExecutable('java', folder);
        if (executable) {
            paths.push(folder);
        } else if (removeOnEmpty) {
            _removeGraalVMInstallation(folder);
        }
    }
}

async function selectAvailableComponents(graalVMHome: string): Promise<string[]> {
    return new Promise<string[]>((resolve, reject) => {
        getAvailableComponents(graalVMHome).then(available => {
            const components = available.filter(availableItem => !availableItem.installed);
            if (components.length > 0) {
                vscode.window.showQuickPick(components, { placeHolder: 'Select GraalVM components to install', canPickMany: true }).then(selected => {
                    if (selected) {
                        resolve(selected.map(component => component.label));
                    } else {
                        reject(new Error('No GraalVM component to install.'));
                    }
                });
            } else {
                reject(new Error('No GraalVM component to install.'));
            }
        });
    });
}

async function selectInstalledComponents(graalVMHome: string): Promise<string[]> {
    return new Promise<string[]>((resolve, reject) => {
        getAvailableComponents(graalVMHome).then(available => {
            const components = available.filter(availableItem => availableItem.installed);
            if (components.length > 0) {
                vscode.window.showQuickPick(components, { placeHolder: 'Select GraalVM components to remove', canPickMany: true }).then(selected => {
                    if (selected) {
                        resolve(selected.map(component => component.label));
                    } else {
                        reject(new Error('No GraalVM component to remove.'));
                    }
                });
            } else {
                reject(new Error('No GraalVM component to remove.'));
            }
        });
    });
}

async function getAvailableComponents(graalVMHome: string): Promise<{label: string, detail: string, installed?: boolean}[]> {
    return new Promise<{label: string, detail: string, installed?: boolean}[]>((resolve, reject) => {
        getGU(graalVMHome).then(executablePath => {
            cp.execFile(executablePath, ['list'], async (error, stdout, _stderr) => {
                if (error) {
                    reject(error);
                } else {
                    const installed: {label: string, detail: string, installed?: boolean}[] = processsGUOutput(stdout);
                    const proxy = utils.getConf('http').get('proxy');
                    const fnc = async (error: any, stdout: string, _stderr: any) => {
                        if (error) {
                            notifyConnectionProblem();
                            reject({error: error, list: installed.map(inst => {inst.installed = true; return inst; }) });
                        } else {
                            const available: {label: string, detail: string, installed?: boolean}[] = processsGUOutput(stdout);
                            available.forEach(avail => {
                                const found = installed.find(item => item.label === avail.label);
                                avail.installed = found ? true : false;
                            });
                            resolve(available);
                        }
                    };
                    if(proxy){
                        cp.exec(`env http_proxy=${proxy} ${executablePath} available`, fnc);
                    } else {
                        cp.execFile(executablePath, ['available'], fnc);
                    }
                }
            });
        }).catch(error => reject(error));
    });
}

async function notifyConnectionProblem(){
    const select = await vscode.window.showWarningMessage("Could not resolve GraalVM components. Check your connection and verify proxy settings.", 'Setup proxy');
    if (select === 'Setup proxy') {
        setupProxy();
    }
}

export function setupProxy() {
    const http = utils.getConf('http');
    const proxy = http.get('proxy') as string;
    vscode.window.showInputBox(
        {
            prompt: 'Input proxy settings.',
            placeHolder: '<http(s)>://<host>:<port>',
            value: proxy
        }
    ).then(out => {
        if (out) {
            if (proxy !== out) {
                http.update('proxy', out, true).then(() => 
                vscode.commands.executeCommand('extension.graalvm.refreshInstallations'));
            }
        }
    });
}

function processsGUOutput(stdout: string): {label: string, detail: string}[] {
    const components: {label: string, detail: string}[] = [];
    let header: boolean = true;
    stdout.split('\n').forEach(line => {
        if (header) {
            if (line.startsWith('-----')) {
                header = false;
            }
        } else {
            const info: string[] | null = line.match(/(\S+( \S)?)+/g);
            if (info && info.length > 3) {
                components.push({ label: info[0], detail: info[2] });
            }
        }
    });
    return components;
}

export class InstallationNodeProvider implements vscode.TreeDataProvider<vscode.TreeItem> {

	private _onDidChangeTreeData: vscode.EventEmitter<vscode.TreeItem | undefined | null> = new vscode.EventEmitter<vscode.TreeItem | undefined | null>();
	readonly onDidChangeTreeData: vscode.Event<vscode.TreeItem | undefined | null> = this._onDidChangeTreeData.event;

	refresh(): void {
		this._onDidChangeTreeData.fire();
	}

	getTreeItem(element: vscode.TreeItem): vscode.TreeItem {
		return element;
	}

	getChildren(element?: vscode.TreeItem): vscode.ProviderResult<vscode.TreeItem[]> {
		if (element instanceof Installation) {
            return getAvailableComponents(element.home).then(components => {
                const ret: vscode.TreeItem[] = [new InstallationFolder(element.home)];
                components.forEach((comp: { detail: string; label: string; installed?: boolean; }) => {
                    ret.push(new Component(element, comp.detail, comp.label, comp.installed));
                });
                return ret;
            }).catch(out => {
                const ret: vscode.TreeItem[] = [new InstallationFolder(element.home)];
                if (out.list) {
                    out.list.forEach((comp: { detail: string; label: string; installed?: boolean; }) => 
                        ret.push(new Component(element, comp.detail, comp.label, comp.installed)));
                }
                ret.push(new ConnectionError('Could not resolve components', out.error.message));
                return ret;
            });
		} else {
            const graalVMHome = utils.getGVMHome();
            const insts = getGVMInsts();
            return findGraalVMs().then(vms => {
                return vms.map(item => new Installation(item.name, vscode.TreeItemCollapsibleState.Collapsed, item.path, item.path === graalVMHome, insts.includes(item.path)));
            });
		}
	}
}

export class Installation extends vscode.TreeItem {

	constructor(
        public readonly label: string,
        public readonly collapsibleState: vscode.TreeItemCollapsibleState,
        public readonly home: string,
        private readonly active: boolean,
        private readonly fromConf: boolean
	) {
        super(label, collapsibleState);
        if (active) {
            this.description = '(active)';
        }
	}

    iconPath = new vscode.ThemeIcon(this.active ? "vm-active" : "vm");

    contextValue = this.fromConf ? this.active ? 'graalvmInstallationActive' : 'graalvmInstallation' : 'graalvmInstallationOut';
}

export class InstallationFolder extends vscode.TreeItem {
    
    iconPath =  new vscode.ThemeIcon("folder-opened");

    contextValue = 'graalvmInstallationFolder';
}

export class Component extends vscode.TreeItem {

	constructor(
        public readonly installation: Installation,
        public readonly label: string,
        public readonly componentId: string,
        private readonly installed?: boolean
	) {
		super(label);
        if (installed) {
            this.description = '(installed)';
        }
	}

    iconPath = new vscode.ThemeIcon("extensions");

    contextValue = this.installed ? 'graalvmComponentInstalled' : 'graalvmComponent';
}
export class ConnectionError extends vscode.TreeItem {
	constructor(
        public readonly label: string,
        public readonly message: string,
	) {
        super(label);
    }
    
    iconPath = new vscode.ThemeIcon("error");

    contextValue = 'graalvmConnectionError';
}