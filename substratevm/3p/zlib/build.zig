const std = @import("std");

pub fn build(b: *std.Build) !void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    const lib = b.addStaticLibrary(.{
        .name = "z",
        .target = target,
        .optimize = optimize,
    });
    lib.linkLibC();
    lib.addIncludePath(b.path("src"));
    lib.installHeadersDirectory(b.path("src"), "", .{});

    var flags = std.ArrayList([]const u8).init(b.allocator);
    defer flags.deinit();
    try flags.appendSlice(&.{
        "-DHAVE_SYS_TYPES_H",
        "-DHAVE_STDINT_H",
        "-DHAVE_STDDEF_H",
        "-DZ_HAVE_UNISTD_H",
    });
    lib.addCSourceFiles(.{ .files = srcs, .flags = flags.items });
    b.installArtifact(lib);
}

const srcs = &.{
    "src/adler32.c",
    "src/compress.c",
    "src/crc32.c",
    "src/deflate.c",
    "src/gzclose.c",
    "src/gzlib.c",
    "src/gzread.c",
    "src/gzwrite.c",
    "src/inflate.c",
    "src/infback.c",
    "src/inftrees.c",
    "src/inffast.c",
    "src/trees.c",
    "src/uncompr.c",
    "src/zutil.c",
};
