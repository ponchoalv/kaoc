#include <stdio.h>
#include <stdlib.h>

// name should always be a valid null terminated c_string
// we do not support buffers (we don't take the length as argument)
int pch_str_len(const char *name)
{
        int size = 0;
        while(*name++) size++;
        return size;
}

const char *say_my_name(const char *name)
{
        char *buf = NULL;
        int len = -1;
        if (!name) name = "stranger";

        // We add to the name "Hi ", 3 characters.
        len = 3 + pch_str_len(name);

        if (len == 2) return NULL;

        buf = malloc((size_t)len+1);
        if (!buf) return NULL;

        snprintf(buf, (size_t)len+1, "Hi %s", name);
        return buf;
}

// We should expose a way to call free from odin (I think)
void free_greeting(const char *greeting)
{
        free((void*)greeting);
}
