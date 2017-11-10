typedef int vec4 __attribute__((vector_size(16)));

int main() {
#ifdef __clang__ // TODO: dragonegg uses incompatibe builtins!
  volatile vec4 v1 = { -1, 8, 2, -5 };

  volatile vec4 v3 =__builtin_shufflevector(v1, v1, 3, 2, 1, 0);
  if(v3[0] != -5 || v3[1] != 2 || v3[2] != 8 || v3[3] != -1) {
    return 1;
  }

  volatile vec4 v4 =__builtin_shufflevector(v1, v1, 0, 0, 0, 0);
  if(v4[0] != -1 || v4[1] != -1 || v4[2] != -1 || v4[3] != -1) {
    return 1;
  }
#endif
  return 0;
}
