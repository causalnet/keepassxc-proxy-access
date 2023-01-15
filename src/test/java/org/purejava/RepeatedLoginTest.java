package org.purejava;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatedLoginTest {
    private static final KeepassProxyAccess kpa = new KeepassProxyAccess();

    @BeforeAll
    static void setUp() throws Exception {
        assertTrue(kpa.connect());
        assertFalse(kpa.associate());
        Thread.sleep(10000L); // give me 10 seconds to enter a associate id

        assertTrue(null != kpa.getDatabasehash() && !kpa.getDatabasehash().isEmpty());
        assertTrue(kpa.testAssociate(kpa.getAssociateId(), kpa.getIdKeyPairPublicKey()));
    }

    @DisplayName("Testing KeePassXC repeatedly")
    @RepeatedTest(1000) //Running test 1000 times eventually will hit 2nd byte increment
    void test() {
        String url = "https://aserver.test.test.test";
        var logins = kpa.getLogins(url, url, false, List.of(kpa.exportConnection()));
        System.out.println(logins);

        assertTrue(logins.containsKey("entries"));
    }
}
