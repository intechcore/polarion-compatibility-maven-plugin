package com.example;

public final class Legacy {

    private Legacy() {
    }

    /**
     * Returns the name through a method rather than a constant field, so javac emits an ldc
     * instruction. A static final String would live in a ConstantValue attribute, which neither
     * this plugin nor Polarion inspects.
     */
    public static String filterClassName() {
        return "javax.servlet.Filter";
    }
}
