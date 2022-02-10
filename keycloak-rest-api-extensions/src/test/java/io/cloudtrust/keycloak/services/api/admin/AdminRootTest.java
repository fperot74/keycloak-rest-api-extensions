package io.cloudtrust.keycloak.services.api.admin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import org.apache.http.NameValuePair;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import io.cloudtrust.keycloak.representations.idm.DeletableUserRepresentation;

public class AdminRootTest extends AbstractRestApiModuleTest {
    private static final TypeReference<List<DeletableUserRepresentation>> deletableUserListType = new TypeReference<List<DeletableUserRepresentation>>() {
    };

    @Test
    public void testNoUserDeclinedTOU() throws IOException, URISyntaxException {
        // To write a test where there are some users having declined TOU for more than the configured delay,
        // we should create sample users with a given creation timestamp far in the past
        List<NameValuePair> params = Collections.emptyList();
        List<DeletableUserRepresentation> users = queryApi(deletableUserListType, "GET", "/realms/master/api/admin/expired-tou-acceptance", params);
        assertThat(users.size(), is(0));
    }
}
