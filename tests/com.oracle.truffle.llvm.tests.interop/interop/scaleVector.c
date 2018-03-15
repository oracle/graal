typedef double vec __attribute__((ext_vector_type(2)));

void scale_vector(double *arr, int len, double scale) {
    vec *vector = (vec*) arr;
    vec vscale = scale;
    int vlen = len / 2;
    int i;
    for (i = 0; i < vlen; i++) {
        vector[i] *= vscale;
    }
    i *= 2;
    if (i < len) {
        arr[i] *= scale;
    }
}
