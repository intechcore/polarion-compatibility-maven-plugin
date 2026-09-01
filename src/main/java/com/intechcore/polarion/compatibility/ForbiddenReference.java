package com.intechcore.polarion.compatibility;

/**
 * One rejected reference found in a scanned class file.
 *
 * @param source    the jar file name, or the class directory name, that holds the class
 * @param className the binary name of the class that holds the reference
 * @param reference the forbidden package reference, in dotted form
 */
public record ForbiddenReference(String source, String className, String reference) {

    @Override
    public String toString() {
        return source + " -> " + className + " references " + reference;
    }
}
