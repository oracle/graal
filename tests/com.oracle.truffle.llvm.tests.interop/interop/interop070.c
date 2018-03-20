int globalInt;

int *returnPointerToGlobal() {
  return &globalInt;
}

int returnGlobal() {
  return globalInt;
}

int main() {
  return 0;
}
