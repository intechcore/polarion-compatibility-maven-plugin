package com.intechcore.polarion.compatibility;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads the UTF-8 constant pool entries of a Java class file.
 *
 * <p>The constant pool holds every type name, method descriptor and string literal the class uses.
 * Scanning it finds both compile-time references and the string constants that Polarion's own
 * checker rejects, without loading the class.</p>
 */
public final class ClassFileReader {

    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;

    private static final int TAG_UTF8 = 1;
    private static final int TAG_INTEGER = 3;
    private static final int TAG_FLOAT = 4;
    private static final int TAG_LONG = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_CLASS = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_FIELD_REF = 9;
    private static final int TAG_METHOD_REF = 10;
    private static final int TAG_INTERFACE_METHOD_REF = 11;
    private static final int TAG_NAME_AND_TYPE = 12;
    private static final int TAG_METHOD_HANDLE = 15;
    private static final int TAG_METHOD_TYPE = 16;
    private static final int TAG_DYNAMIC = 17;
    private static final int TAG_INVOKE_DYNAMIC = 18;
    private static final int TAG_MODULE = 19;
    private static final int TAG_PACKAGE = 20;

    private ClassFileReader() {
    }

    /**
     * Reads every UTF-8 constant of a class file.
     *
     * @param input the class file content; the caller closes it
     * @return the constants, in pool order; empty when the stream is not a class file
     * @throws IOException when the stream is a class file but cannot be parsed
     */
    public static Set<String> readUtf8Constants(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(input);
        if (data.readInt() != CLASS_FILE_MAGIC) {
            return Collections.emptySet();
        }
        data.readUnsignedShort();
        data.readUnsignedShort();

        int poolCount = data.readUnsignedShort();
        Set<String> constants = new LinkedHashSet<>();
        int index = 1;
        while (index < poolCount) {
            index += readEntry(data, constants);
        }
        return constants;
    }

    private static int readEntry(DataInputStream data, Set<String> constants) throws IOException {
        int tag = data.readUnsignedByte();
        switch (tag) {
            case TAG_UTF8:
                constants.add(data.readUTF());
                return 1;
            case TAG_LONG, TAG_DOUBLE:
                skip(data, 8);
                return 2;
            case TAG_INTEGER, TAG_FLOAT, TAG_FIELD_REF, TAG_METHOD_REF, TAG_INTERFACE_METHOD_REF,
                 TAG_NAME_AND_TYPE, TAG_DYNAMIC, TAG_INVOKE_DYNAMIC:
                skip(data, 4);
                return 1;
            case TAG_CLASS, TAG_STRING, TAG_METHOD_TYPE, TAG_MODULE, TAG_PACKAGE:
                skip(data, 2);
                return 1;
            case TAG_METHOD_HANDLE:
                skip(data, 3);
                return 1;
            default:
                throw new IOException("Unknown constant pool tag: " + tag);
        }
    }

    private static void skip(DataInputStream data, int bytes) throws IOException {
        data.readFully(new byte[bytes]);
    }
}
