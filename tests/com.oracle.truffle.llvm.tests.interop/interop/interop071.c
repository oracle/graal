void *global;

void **returnPointerToGlobal() {
  return &global;
}

void *returnGlobal() {
  return global;
}

int main() {
  return 0;
}
