#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <libelf.h>
#include <gelf.h>
#include <zlib.h>

typedef struct {
    const char *name;
    GElf_Sym sym;
    size_t section_index;
} elf_symbol;

#ifdef DEBUG

#define LOG_DEBUG(format, type, param)   \
{                                        \
   fprintf(stderr, format, (type)param); \
}

#else

#define LOG_DEBUG(format, type, param)

#endif

static elf_symbol *read_symbols(Elf *elf, size_t *symcount) {
    Elf_Scn *scn = NULL;
    GElf_Shdr shdr;
    Elf_Data *data;
    Elf_Data *str_data;
    elf_symbol *symbol_table = NULL;
    size_t symbol_count = 0;

    // Find the .dynsym section
    while ((scn = elf_nextscn(elf, scn)) != NULL) {
        if (gelf_getshdr(scn, &shdr) != &shdr) {
            continue;
        }

        if (shdr.sh_type == SHT_DYNSYM) {
            break;
        }
    }

    if (!scn) {
        fprintf(stderr, "No dynamic symbol table found\n");
        return NULL;
    }

    // Get the symbol table data
    data = elf_getdata(scn, NULL);
    if (!data) {
        fprintf(stderr, "Failed to get symbol table data\n");
        return NULL;
    }

    // Get the string table
    Elf_Scn *str_scn = elf_getscn(elf, shdr.sh_link);
    if (!str_scn) {
        fprintf(stderr, "Failed to get string table section\n");
        return NULL;
    }

    str_data = elf_getdata(str_scn, NULL);
    if (!str_data) {
        fprintf(stderr, "Failed to get string table data\n");
        return NULL;
    }

    // Calculate number of symbols
    symbol_count = shdr.sh_size / shdr.sh_entsize;

    if (symbol_count == 0) {
        fprintf(stderr, "No dynamic symbols found\n");
        return NULL;
    }

    // Allocate symbol table
    symbol_table = (elf_symbol *) malloc(symbol_count * sizeof(elf_symbol));
    if (!symbol_table) {
        fprintf(stderr, "Failed to allocate memory for symbol table\n");
        return NULL;
    }

    // Read all symbols
    for (size_t i = 0; i < symbol_count; i++) {
        GElf_Sym sym;
        if (gelf_getsym(data, i, &sym) != &sym) {
            free(symbol_table);
            fprintf(stderr, "Failed to read symbol at index %zu\n", i);
            return NULL;
        }

        symbol_table[i].sym = sym;
        symbol_table[i].section_index = sym.st_shndx;
        symbol_table[i].name = (const char *)str_data->d_buf + sym.st_name;
    }

    *symcount = symbol_count;
    return symbol_table;
}

static elf_symbol *find_symbol(elf_symbol *symbol_table, size_t symcount, const char *name) {
    for (size_t i = 0; i < symcount; i++) {
        if (symbol_table[i].name && strcmp(symbol_table[i].name, name) == 0) {
            return &symbol_table[i];
        }
    }
    return NULL;
}

static int get_section_data_at_offset(Elf *elf, elf_symbol *sym, uint64_t offset,
                                       size_t size, unsigned char **out_data) {
    Elf_Scn *section;
    GElf_Shdr shdr;
    Elf_Data *section_data;

    section = elf_getscn(elf, sym->section_index);
    if (!section || gelf_getshdr(section, &shdr) != &shdr) {
        fprintf(stderr, "Failed to get section\n");
        return 1;
    }

    section_data = elf_getdata(section, NULL);
    if (!section_data) {
        fprintf(stderr, "Failed to get section data\n");
        return 1;
    }

    if (offset + size > section_data->d_size) {
        fprintf(stderr, "Not enough data to read %zu bytes at offset %lu\n", size, offset);
        return 1;
    }

    *out_data = (unsigned char *)section_data->d_buf + offset;
    return 0;
}

#define CHUNK_SIZE 16384

// inflate data in 'source' with len 'source_len' to the buffer pointed to by
// 'dest'. The output length will be set in 'dest_len'.
static int inflate_gzip(const unsigned char *source, uint64_t source_len,
                        unsigned char **dest, uint64_t *dest_len) {
    z_stream strm;
    int ret;
    size_t output_size = 0;
    size_t output_capacity = CHUNK_SIZE;
    unsigned char *output = malloc(output_capacity);
    unsigned char out_buffer[CHUNK_SIZE];

    if (!output) {
        fprintf(stderr, "Failed to allocate memory\n");
        return 1;
    }

    // Initialize zlib stream
    strm.zalloc = Z_NULL;
    strm.zfree = Z_NULL;
    strm.opaque = Z_NULL;
    strm.avail_in = 0;
    strm.next_in = Z_NULL;

    // Initialize for gzip format (windowBits = 15 + 16 for gzip)
    ret = inflateInit2(&strm, 15 + 16);
    if (ret != Z_OK) {
        fprintf(stderr, "Failed to initialize zlib: %d\n", ret);
        free(output);
        return 1;
    }

    // Set input
    strm.avail_in = source_len;
    strm.next_in = (unsigned char *)source;

    // Decompress until end of stream
    do {
        strm.avail_out = CHUNK_SIZE;
        strm.next_out = out_buffer;

        ret = inflate(&strm, Z_NO_FLUSH);

        switch (ret) {
            case Z_NEED_DICT:
            case Z_DATA_ERROR:
            case Z_MEM_ERROR:
                fprintf(stderr, "Decompression error: %d\n", ret);
                inflateEnd(&strm);
                free(output);
                return 1;
        }

        size_t have = CHUNK_SIZE - strm.avail_out;

        // Resize output buffer if needed
        if (output_size + have > output_capacity) {
            output_capacity *= 2;
            unsigned char *new_output = realloc(output, output_capacity);
            if (!new_output) {
                fprintf(stderr, "Failed to reallocate memory\n");
                inflateEnd(&strm);
                free(output);
                return 1;
            }
            output = new_output;
        }

        // Copy decompressed data to output
        memcpy(output + output_size, out_buffer, have);
        output_size += have;

    } while (strm.avail_out == 0);

    // Cleanup
    inflateEnd(&strm);

    if (ret != Z_STREAM_END) {
        fprintf(stderr, "Incomplete decompression\n");
        free(output);
        return 1;
    }

    *dest = output;
    *dest_len = output_size;

    return 0;
}

#undef CHUNK_SIZE

// Retrieves an embedded SBOM (gzip compressed) from a binary (executable or
// shared library). The executable parameter is the path to the binary. The
// SBOM is streamed to stdout
int extract_sbom(const char* executable) {
    int fd;
    Elf *elf;
    elf_symbol *symbol_table = NULL;
    elf_symbol *sbom_sym, *sbom_size_sym;
    size_t symcount;
    unsigned char *length_data, *compressed_data, *decompressed_data = NULL;
    uint64_t sbom_length, sbom_offset, decompressed_len;
    GElf_Shdr shdr;
    int ret = 1;

    if (elf_version(EV_CURRENT) == EV_NONE) {
        fprintf(stderr, "Failed to initialize libelf: %s\n", elf_errmsg(-1));
        return 1;
    }

    fd = open(executable, O_RDONLY);
    if (fd < 0) {
        fprintf(stderr, "Failed to open file '%s'\n", executable);
        return 1;
    }

    elf = elf_begin(fd, ELF_C_READ, NULL);
    if (!elf || elf_kind(elf) != ELF_K_ELF) {
        fprintf(stderr, "Failed to open ELF file '%s'\n", executable);
        goto cleanup;
    }

    symbol_table = read_symbols(elf, &symcount);
    if (!symbol_table) {
        goto cleanup;
    }

    sbom_sym = find_symbol(symbol_table, symcount, "sbom");
    sbom_size_sym = find_symbol(symbol_table, symcount, "sbom_length");
    if (!sbom_sym || !sbom_size_sym) {
        fprintf(stderr, "Required symbols 'sbom' or 'sbom_length' not found\n");
        goto cleanup;
    }

    // Read sbom_length value (8 bytes)
    Elf_Scn *size_section = elf_getscn(elf, sbom_size_sym->section_index);
    if (!size_section || gelf_getshdr(size_section, &shdr) != &shdr) {
        fprintf(stderr, "Failed to get sbom_length section\n");
        goto cleanup;
    }

    uint64_t size_offset = sbom_size_sym->sym.st_value - shdr.sh_addr;
    if (get_section_data_at_offset(elf, sbom_size_sym, size_offset, 8, &length_data) != 0) {
        goto cleanup;
    }
    memcpy(&sbom_length, length_data, 8);

    // Read compressed SBOM data
    Elf_Scn *sbom_section = elf_getscn(elf, sbom_sym->section_index);
    if (!sbom_section || gelf_getshdr(sbom_section, &shdr) != &shdr) {
        fprintf(stderr, "Failed to get sbom section\n");
        goto cleanup;
    }

    sbom_offset = sbom_sym->sym.st_value - shdr.sh_addr;
    if (get_section_data_at_offset(elf, sbom_sym, sbom_offset, sbom_length, &compressed_data) != 0) {
        goto cleanup;
    }

    LOG_DEBUG("sbom address: 0x%lx\n", unsigned long, sbom_sym->sym.st_value);
    LOG_DEBUG("sbom_length: %lu bytes\n", unsigned long, sbom_length);

    // Decompress and output
    if (inflate_gzip(compressed_data, sbom_length, &decompressed_data, &decompressed_len) != 0) {
        fprintf(stderr, "Failed to decompress data\n");
        goto cleanup;
    }

    fwrite(decompressed_data, decompressed_len, 1, stdout);
    LOG_DEBUG("Successfully extracted %lu bytes of compressed data\n", unsigned long, sbom_length);
    ret = 0;

cleanup:
    free(decompressed_data);
    free(symbol_table);
    if (elf) elf_end(elf);
    if (fd >= 0) close(fd);
    return ret;
}

