void assert_true(int val) {
  if (!val) {
    abort();
  }
}

void assert_false(int val) {
  if (val) {
    abort();
  }
}
