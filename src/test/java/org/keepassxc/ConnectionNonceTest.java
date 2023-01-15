package org.keepassxc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ConnectionNonceTest {
    @Test
    void test() {
        byte[] before = new byte[] {
            -1, 89, 10, 82, -100, 105, -57, 47, 124, -69, -39, -20, 80, -2, 17, 81, -26, 38, -14, -68, -44, -58, 98, -91
        };
        byte[] expected = new byte[] {
            0, 90, 10, 82, -100, 105, -57, 47, 124, -69, -39, -20, 80, -2, 17, 81, -26, 38, -14, -68, -44, -58, 98, -91
        };
        byte[] actual = Connection.incrementNonce(before);
        assertArrayEquals(expected, actual);
    }
}
