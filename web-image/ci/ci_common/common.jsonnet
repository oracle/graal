local common = import '../../../ci/ci_common/common.jsonnet';

local node_map = {
  '22': 'v22.9.0',
};

{
  /*
   * Creates command to prepend the given path to $PATH on the given platform
   */
  prepend_path(path, os):
    local is_windows = os == 'windows';
    local canonical_path = if is_windows then std.strReplace(path, '/', '\\') else path;
    local separator = if is_windows then ';' else ':';
    ['set-export', 'PATH', canonical_path + separator + '$PATH'],

  node(version_tag): {
    local is_windows = self.os == 'windows',
    downloads+: {
      /*
       * On windows, the node executable is at the root of the download
       */
      NODE: { name: 'node', version: node_map[version_tag], platformspecific: true },
    },
    environment+: {
      NODE_EXE: if is_windows then '$NODE\\node.exe' else '$NODE/bin/node',
    },
    setup+: [
      $.prepend_path(if is_windows then '$NODE' else '$NODE/bin', self.os),
      ['node', '--version'],
    ],
  },
  node22: self.node('22'),

  wabt: {
    downloads+: {
      WABT_DIR: { name: 'wabt', version: '1.0.32', platformspecific: true },
    },
    setup+: [
      $.prepend_path('$WABT_DIR/bin', self.os),
      ['wat2wasm', '--version'],
    ],
  },

  binaryen: {
    downloads+: {
      BINARYEN_DIR: { name: 'binaryen', version: 'version_119', platformspecific: true },
    },
    setup+: [
      $.prepend_path('$BINARYEN_DIR/bin', self.os),
      ['wasm-as', '--version'],
    ],
  },

  maven: {
    packages+: {
      maven: '==3.9.10',
    },
  },

  guard_suites: ['<graal>/web-image', '<graal>/substratevm', '<graal>/compiler', '<graal>/sdk', '<graal>/wasm'],
  extra_includes: [],

  catch_test_failures: {
    catch_files+: [
      'Dumping test fail to (?P<filename>.+\\.zip)',
    ],
  },

  gate_prettier: self.node22 {
    setup+: [
      ['npm', 'install', 'prettier'],
    ],
    environment+: {
      PRETTIER_EXE: 'npx prettier',
    },
  },

  eclipse: common.deps.eclipse,
  jdt: common.deps.jdt,
  spotbugs: common.deps.spotbugs,
  svm: common.deps.svm,
}
