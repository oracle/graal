// #define _GNU_SOURCE is needed for the resolution of the following warnings
//warning: implicit declaration of function ‘pthread_setname_np’ [-Wimplicit-function-declaration]
//warning: implicit declaration of function ‘pthread_getname_np’ [-Wimplicit-function-declaration]
#define _GNU_SOURCE
#include <stdio.h>
#include <pthread.h>

void *set_named_thread(void *data) {
    pthread_t *thread_info = (pthread_t *) data;
    const int setname_rv = pthread_setname_np(*thread_info, "sulong pthread");
    if (setname_rv) {
        printf("Could not set pthread name\n");
    }
    char thread_name[16];
    const int getname_rv = pthread_getname_np(*thread_info, thread_name, 16);
    if (getname_rv) {
        printf("Could not get pthread name\n");
    }
    fprintf(stdout, "My name is '%s'\n", thread_name);

    const pthread_t self = pthread_self();
    const int setname_rv_self = pthread_setname_np(self, "self pthread");
    if (setname_rv_self) {
        printf("Could not set pthread name\n");
    }
    char thread_name_self[16];
    const int getname_rv_self = pthread_getname_np(self, thread_name_self, 16);
    if (getname_rv_self) {
        printf("Could not get pthread name\n");
    }
    fprintf(stdout, "My name is '%s'\n", thread_name_self);
    return NULL;
}

int main() {
    pthread_t thread_idd;
    const int create_rv = pthread_create(&(thread_idd), NULL, &set_named_thread, (void *) &thread_idd);
    if (create_rv) {
        printf("Could not create thread\n");
        return create_rv;
    }
    const int join_rv = pthread_join(thread_idd, NULL);
    if (join_rv) {
        printf("Could not join thread\n");
        return join_rv;
    }
    return 0;
}