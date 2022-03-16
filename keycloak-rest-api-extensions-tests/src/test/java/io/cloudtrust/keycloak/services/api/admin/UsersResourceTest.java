package io.cloudtrust.keycloak.services.api.admin;

import io.cloudtrust.keycloak.AbstractRestApiExtensionTest;
import io.cloudtrust.keycloak.representations.idm.UsersPageRepresentation;
import io.cloudtrust.keycloak.test.container.KeycloakDeploy;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.UserRepresentation;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@ExtendWith(KeycloakDeploy.class)
class UsersResourceTest extends AbstractRestApiExtensionTest {
    private static final String getMethod = "GET";

    @BeforeEach
    public void initToken() throws IOException {
    	this.initializeToken();
    }

    @Test
    void testStandardGetUsers() throws IOException, URISyntaxException {
            UsersPageRepresentation page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users");
            UserRepresentation[] users = grabUsers(page);
            assertThat(users, notNullValue());
            assertThat(users, arrayWithSize(8));
            assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(),
                    arrayContainingInAnyOrder("non-duplicate-email-user", "rolerichuser", "level2groupuser", "topgroupuser",
                            "keycloak-user@localhost", "john-doh@localhost", "test-user@localhost", "topgroupuser2"));
            assertThat(page.getCount(), is(8));
    }

    @Test
    void testPaginatedGetUsers() throws IOException, URISyntaxException {
        // The users are sorted alphabetically by username (sorted in GetUsersQuery)

        // Page 1
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("first", "0"));
        nvps.add(new BasicNameValuePair("max", "2"));
        UsersPageRepresentation page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        UserRepresentation[] users = grabUsers(page);
        assertThat(users, notNullValue());
        assertThat(users, arrayWithSize(2));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(), arrayContaining("john-doh@localhost", "keycloak-user@localhost"));
        assertThat(page.getCount(), is(8));

        // Page 3
        nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("first", "4"));
        nvps.add(new BasicNameValuePair("max", "2"));
        page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        users = grabUsers(page);
        assertThat(users, notNullValue());
        assertThat(users, arrayWithSize(2));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(), arrayContaining("rolerichuser", "test-user@localhost"));
        assertThat(page.getCount(), is(8));
    }

    @Test
    void testStandardGetUser() throws IOException, URISyntaxException {
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("username", "rolerichuser"));
        UsersPageRepresentation page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        UserRepresentation[] users = grabUsers(page);
        assertThat(users, notNullValue());
        assertThat(users, arrayWithSize(1));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(),
                arrayContainingInAnyOrder("rolerichuser"));
        assertThat(page.getCount(), is(1));
    }

    @ParameterizedTest
    @MethodSource("getUsersWithWildcardSamples")
    void testGetUsersWithWildcard(String field, String value, int expectedCount) throws IOException, URISyntaxException {
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair(field, value));
        UsersPageRepresentation page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        UserRepresentation[] users = grabUsers(page);
        assertThat(users, notNullValue());
        assertThat(users.length, is(expectedCount));
    }

    public static Stream<Arguments> getUsersWithWildcardSamples() throws IOException, URISyntaxException {
    	return Stream.of(
    			Arguments.of("lastName", "doh", 2),
    			Arguments.of("lastName", "%do%", 2),
    			Arguments.of("lastName", "doh%", 1),
    			Arguments.of("lastName", "%doh", 1),
    			Arguments.of("lastName", "=do", 0),
    			Arguments.of("lastName", "=doh", 1)
		);
    }

    @Test
    void testGetUsersWithGroup() throws IOException, URISyntaxException {
    	RealmResource testRealm = this.getRealm();

    	List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("groupId", testRealm.groups().groups("topGroup", null, null).get(0).getId()));
        UsersPageRepresentation page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        UserRepresentation[] users = grabUsers(page);
        assertThat(users, arrayWithSize(2));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(),
                arrayContainingInAnyOrder("topgroupuser","topgroupuser2"));
        assertThat(page.getCount(), is(2));

        nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("groupId", testRealm.groups().groups("roleRichGroup", null, null).stream()
                .flatMap(group -> group.getSubGroups().stream()).filter(group -> group.getName().equals("level2group")).findFirst().get().getId()));
        page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        users = grabUsers(page);
        assertThat(users, arrayWithSize(1));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(),
                arrayContainingInAnyOrder("rolerichuser"));
        assertThat(page.getCount(), is(1));

        nvps.add(new BasicNameValuePair("groupId", testRealm.groups().groups("topGroup", null, null).get(0).getId()));
        page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        users = grabUsers(page);
        assertThat(users, arrayWithSize(3));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(),
                arrayContainingInAnyOrder("topgroupuser","topgroupuser2","rolerichuser"));
        assertThat(page.getCount(), is(3));
    }

    @Test
    void testGetUsersWithRole() throws IOException, URISyntaxException {
    	RealmResource testRealm = this.getRealm();

    	List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("roleId", testRealm.roles().get("user").toRepresentation().getId()));
        UsersPageRepresentation page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        UserRepresentation[] users = grabUsers(page);
        assertThat(users, arrayWithSize(5));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(),
                arrayContainingInAnyOrder("non-duplicate-email-user","topgroupuser2"
                        ,"keycloak-user@localhost", "john-doh@localhost", "test-user@localhost"));
        assertThat(page.getCount(), is(5));

        nvps.add(new BasicNameValuePair("roleId", testRealm.roles().get("offline_access").toRepresentation().getId()));
        page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        users = grabUsers(page);
        assertThat(users, arrayWithSize(6));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(),
                arrayContainingInAnyOrder("non-duplicate-email-user","topgroupuser2"
                        ,"keycloak-user@localhost", "john-doh@localhost", "test-user@localhost", "topgroupuser"));
        assertThat(page.getCount(), is(6));
    }

    @Test
    void testGetUsersWithGroupAndRole() throws IOException, URISyntaxException {
    	RealmResource testRealm = this.getRealm();

    	List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("roleId", testRealm.roles().get("user").toRepresentation().getId()));
        nvps.add(new BasicNameValuePair("groupId", testRealm.groups().groups("topGroup", null, null).get(0).getId()));
        UsersPageRepresentation page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        UserRepresentation[] users = grabUsers(page);
        assertThat(users, arrayWithSize(1));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(),
                arrayContainingInAnyOrder("topgroupuser2"));
        assertThat(page.getCount(), is(1));

        nvps.add(new BasicNameValuePair("roleId", testRealm.roles().get("offline_access").toRepresentation().getId()));
        page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        users = grabUsers(page);
        assertThat(users, arrayWithSize(2));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(),
                arrayContainingInAnyOrder("topgroupuser2", "topgroupuser"));
        assertThat(page.getCount(), is(2));
    }

    @Test
    void testGetUsersWithNonExistingGroup() throws IOException, URISyntaxException {
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("groupId", "123"));
        UsersPageRepresentation page  = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        UserRepresentation[] users = grabUsers(page);
        assertThat(users, arrayWithSize(0));
        assertThat(page.getCount(), is(0));
    }

    @Test
    void testGetUsersWithNonExistingRole() throws IOException, URISyntaxException {
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("roleId", "123879834564"));
        UsersPageRepresentation page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        UserRepresentation[] users = grabUsers(page);
        assertThat(users, arrayWithSize(0));
        assertThat(page.getCount(), is(0));
    }

    @Test
    void testGetUsersWithGroupAndSearch() throws IOException, URISyntaxException {
    	RealmResource testRealm = this.getRealm();

    	List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("groupId",  testRealm.groups().groups("topGroup", null, null).get(0).getId()));
        nvps.add(new BasicNameValuePair("search", "topgroupuser2"));
        UsersPageRepresentation page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        UserRepresentation[] users = grabUsers(page);
        assertThat(users, arrayWithSize(1));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(),
                arrayContainingInAnyOrder("topgroupuser2"));
        assertThat(page.getCount(), is(1));
    }

    @Test
    void testGetUsersWithSearch() throws IOException, URISyntaxException {
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("search", "topgroupuser"));
        UsersPageRepresentation page = queryApi(UsersPageRepresentation.class, getMethod, "/realms/master/api/admin/realms/test/users", nvps);
        UserRepresentation[] users = grabUsers(page);
        assertThat(users, arrayWithSize(2));
        assertThat(Arrays.stream(users).map(UserRepresentation::getUsername).toArray(),
                arrayContainingInAnyOrder("topgroupuser", "topgroupuser2"));
        assertThat(page.getCount(), is(2));
    }

    private UserRepresentation[] grabUsers(UsersPageRepresentation page) {
        return page.getUsers().toArray(new UserRepresentation[]{});
    }
}
