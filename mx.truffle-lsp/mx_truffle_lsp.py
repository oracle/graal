import mx, mx_gate, mx_subst, os


def lsp(args):
    dists = [d for s in mx._suites.values() for d in s.dists if isinstance(d, mx.ClasspathDependency)]
    dists = [d for d in dists if not d.remoteName().endswith("TEST")] # skip test languages
    print(dists)
    vm_args, server_args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    vm_args += mx.get_runtime_jvm_args(dists)
    vm_args.append("de.hpi.swa.trufflelsp.launcher.GraalLanguageServerLauncher")
    return mx.run_java(vm_args + server_args)


_commands = {
    'lsp' : [lsp, ''],
}


mx.update_commands(mx.suite("truffle-lsp"), _commands)
