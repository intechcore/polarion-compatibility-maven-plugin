package com.intechcore.polarion.compatibility;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassFileReaderTest {

    @Test
    void readUtf8Constants_shouldReturnEveryUtf8Entry() throws IOException {
        byte[] classFile = ClassFiles.withConstants("javax/servlet/http/HttpServlet", "java/lang/Object");

        try (InputStream input = new ByteArrayInputStream(classFile)) {
            assertThat(ClassFileReader.readUtf8Constants(input))
                    .containsExactly("javax/servlet/http/HttpServlet", "java/lang/Object");
        }
    }

    @Test
    void readUtf8Constants_shouldReturnEmptyForNonClassFile() throws IOException {
        try (InputStream input = new ByteArrayInputStream("not a class file".getBytes())) {
            assertThat(ClassFileReader.readUtf8Constants(input)).isEmpty();
        }
    }

    @Test
    void readUtf8Constants_shouldSkipWideAndNarrowEntries() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(bytes)) {
            data.writeInt(0xCAFEBABE);
            data.writeShort(0);
            data.writeShort(61);
            data.writeShort(7);
            data.writeByte(5);
            data.writeLong(1L);
            data.writeByte(7);
            data.writeShort(1);
            data.writeByte(15);
            data.writeByte(1);
            data.writeShort(1);
            data.writeByte(10);
            data.writeInt(1);
            data.writeByte(1);
            data.writeUTF("javax/ws/rs/GET");
        }

        try (InputStream input = new ByteArrayInputStream(bytes.toByteArray())) {
            assertThat(ClassFileReader.readUtf8Constants(input)).containsExactly("javax/ws/rs/GET");
        }
    }

    @Test
    void readUtf8Constants_shouldRejectUnknownTag() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(bytes)) {
            data.writeInt(0xCAFEBABE);
            data.writeShort(0);
            data.writeShort(61);
            data.writeShort(2);
            data.writeByte(99);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        assertThatThrownBy(() -> ClassFileReader.readUtf8Constants(new ByteArrayInputStream(bytes.toByteArray())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("99");
    }
}
