package io.cloudtrust.keycloak.services.api.admin;

import org.junit.BeforeClass;

import io.cloudtrust.keycloak.test.AbstractInKeycloakTest;

public abstract class AbstractRestApiModuleTest extends AbstractInKeycloakTest {
    @BeforeClass
    public static void startKeycloak() {
    	AbstractInKeycloakTest.startKeycloakContainer(keycloak -> {
    		keycloak
    		    .withRealmImportFile("/testrealm.json")
                .withProviderClassesFrom("target/classes");
    	});
    }
}
