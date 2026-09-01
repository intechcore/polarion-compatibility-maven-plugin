package com.intechcore.polarion.compatibility;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds minimal class files and jars for the scanner tests.
 */
final class ClassFiles {

    private static final int MAGIC = 0xCAFEBABE;
    private static final int MAJOR_VERSION = 61;

    private ClassFiles() {
    }

    /**
     * Builds a class file that carries the given strings as UTF-8 constants.
     *
     * <p>Only the header and the constant pool are written. The scanner stops reading there.</p>
     */
    static byte[] withConstants(String... constants) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(bytes)) {
            data.writeInt(MAGIC);
            data.writeShort(0);
            data.writeShort(MAJOR_VERSION);
            data.writeShort(constants.length + 1);
            for (String constant : constants) {
                data.writeByte(1);
                data.writeUTF(constant);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    static Path writeClass(Path directory, String binaryName, String... constants) throws IOException {
        Path file = directory.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, withConstants(constants));
        return file;
    }

    static Path writeJar(Path directory, String jarName, String binaryName, String... constants) throws IOException {
        Path jar = directory.resolve(jarName);
        Files.createDirectories(directory);
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("META-INF/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(binaryName.replace('.', '/') + ".class"));
            zip.write(withConstants(constants));
            zip.closeEntry();
        }
        return jar;
    }
}
