package io.cloudtrust.keycloak;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;

import io.cloudtrust.keycloak.test.AbstractInKeycloakTest;
import io.cloudtrust.keycloak.test.init.InjectionException;

public abstract class AbstractRestApiExtensionTest extends AbstractInKeycloakTest {
	@BeforeEach
    public void setupTest() throws IOException, InjectionException {
		this.injectComponents();
    	this.createRealm("/testrealm.json");
    	// Clean events
    	this.activateEvents();
    	this.clearEvents();
    }
}
