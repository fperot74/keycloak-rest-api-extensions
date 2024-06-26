package io.cloudtrust.keycloak.services.resource.api.admin;

import io.cloudtrust.keycloak.ExecuteActionsEmailHelper;
import io.cloudtrust.keycloak.delegate.CtUserModelDelegate;
import io.cloudtrust.keycloak.email.EmailSender;
import io.cloudtrust.keycloak.email.model.EmailModel;
import io.cloudtrust.keycloak.email.model.RealmWithOverridenEmailTheme;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.email.EmailException;
import org.keycloak.email.freemarker.beans.ProfileBean;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.ClientModel;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.utils.RedirectUtils;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.services.ErrorResponse;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resources.LoginActionsService;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.UserResource;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CtUserResource extends UserResource {
    private static final Logger logger = Logger.getLogger(CtUserResource.class);
    private static final String USER_EMAIL_MISSING = "User email missing";

    protected static final String ATTRIB_NAME_ID = "saml.persistent.name.id.for.*";

    private final AdminPermissionEvaluator auth;
    private final AdminEventBuilder adminEvent;
    private final KeycloakSession kcSession;
    private final UserModel user;

    public CtUserResource(KeycloakSession kcSession, UserModel user, AdminPermissionEvaluator auth, AdminEventBuilder adminEvent) {
        super(kcSession.getContext().getRealm(), user, auth, adminEvent);
        this.auth = auth;
        this.adminEvent = adminEvent;
        this.kcSession = kcSession;
        this.user = user;
    }

    /**
     * Get representation of the user
     *
     * @return Requested user
     */
    @GET
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public UserRepresentation getUser() {
        UserRepresentation res = super.getUser();
        String nameId = this.user.getFirstAttribute(ATTRIB_NAME_ID);
        if (StringUtils.isNotBlank(nameId)) {
            res.getAttributes().put(ATTRIB_NAME_ID, Collections.singletonList(nameId));
        }
        return res;
    }

    @Path("send-email")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response sendMail(EmailModel emailModel) {
        auth.users().requireManage();

        if (emailModel.getRecipient() == null) {
            emailModel.setRecipient(user.getEmail());
        }
        if (StringUtils.isBlank(emailModel.getRecipient())) {
            return ErrorResponse.error(USER_EMAIL_MISSING, Response.Status.BAD_REQUEST);
        }

        if (!user.isEnabled()) {
            throw new WebApplicationException(ErrorResponse.error("User is disabled", Response.Status.BAD_REQUEST));
        }

        if (emailModel.getTheming()!=null) {
            Response error = setEmailTheme(emailModel.getTheming().getThemeRealmName());
            if (error != null) {
                return error;
            }
        }

        Locale locale = session.getContext().resolveLocale(user);

        UriBuilder builder = LoginActionsService.loginActionsBaseUrl(session.getContext().getUri());
        String link = builder.build(session.getContext().getRealm().getName()).toString() + "/";
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("user", new ProfileBean(user));
        attributes.put("realmName", realm.getDisplayName());
        attributes.put("link", link);
        if (emailModel.getTheming()!=null && emailModel.getTheming().getTemplateParameters() != null) {
            attributes.putAll(emailModel.getTheming().getTemplateParameters());
        }

        return EmailSender.sendMail(kcSession, realm, emailModel, locale, attributes);
    }

    /**
     * Send an update account email to the user
     * <p>
     * An email contains a link the user can click to perform a set of required actions.
     * The redirectUri and clientId parameters are optional. If no redirect is given, then there will
     * be no link back to click after actions have completed.  Redirect uri must be a valid uri for the
     * particular clientId.
     *
     * @param redirectUri    Redirect uri
     * @param clientId       Client id
     * @param lifespan       Number of seconds after which the generated token expires
     * @param custom1        Custom parameter
     * @param custom2        Custom parameter
     * @param custom3        Custom parameter
     * @param custom4        Custom parameter
     * @param custom5        Custom parameter
     * @param themeRealmName Name of the realm used for theming
     * @param actions        required actions the user needs to complete
     * @return status no-content if successful, returns error otherwise
     */
    @Path("execute-actions-email")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response executeActionsEmail(@QueryParam(OIDCLoginProtocol.REDIRECT_URI_PARAM) String redirectUri,
                                        @QueryParam(OIDCLoginProtocol.CLIENT_ID_PARAM) String clientId,
                                        @QueryParam("lifespan") Integer lifespan,
                                        @QueryParam("custom1") String custom1,
                                        @QueryParam("custom2") String custom2,
                                        @QueryParam("custom3") String custom3,
                                        @QueryParam("custom4") String custom4,
                                        @QueryParam("custom5") String custom5,
                                        @QueryParam("themeRealm") String themeRealmName,
                                        List<String> actions) {
        auth.users().requireManage(user);

        if (user.getEmail() == null) {
            return ErrorResponse.error(USER_EMAIL_MISSING, Response.Status.BAD_REQUEST);
        }

        validateUser(user);
        validateRedirectUriAndClient(redirectUri, clientId);

        if (clientId == null) {
            clientId = Constants.ACCOUNT_MANAGEMENT_CLIENT_ID;
        }

        Map<String, String> attributes = new HashMap<>();
        attributes.put("custom1", custom1);
        attributes.put("custom2", custom2);
        attributes.put("custom3", custom3);
        attributes.put("custom4", custom4);
        attributes.put("custom5", custom5);

        UserModel targetUser = this.user;
        String emailToValidate = StringUtils.trim(user.getFirstAttribute(ExecuteActionsEmailHelper.ATTRB_EMAIL_TO_VALIDATE));
        if (StringUtils.isNotBlank(emailToValidate) && actions.contains(ExecuteActionsEmailHelper.VERIFY_EMAIL_ACTION)) {
            targetUser = new CtUserModelDelegate(user);
            targetUser.setEmail(emailToValidate);
        } else if (StringUtils.isBlank(user.getEmail())) {
            return ErrorResponse.error(USER_EMAIL_MISSING, Response.Status.BAD_REQUEST);
        }

        ClientModel client = getEnabledClientOrFail(clientId);

        String redirect;
        if (redirectUri != null) {
            redirect = RedirectUtils.verifyRedirectUri(session, redirectUri, client);
            if (redirect == null) {
                throw new WebApplicationException(ErrorResponse.error("Invalid redirect uri.", Response.Status.BAD_REQUEST));
            }
        }

        Response error = setEmailTheme(themeRealmName);
        if (error != null) {
            return error;
        }

        try {
            ExecuteActionsEmailHelper.sendExecuteActionsEmail(session, realm, targetUser, actions, lifespan, redirectUri, clientId, attributes);
            adminEvent.operation(OperationType.ACTION).resourcePath(session.getContext().getUri()).success();

            return Response.noContent().build();
        } catch (EmailException e) {
            ServicesLogger.LOGGER.failedToSendActionsEmail(e);
            return ErrorResponse.error("Failed to send execute actions email", Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            session.getContext().setRealm(realm);
        }
    }

    private void validateUser(UserModel user) {
        if (!user.isEnabled()) {
            throw new WebApplicationException(ErrorResponse.error("User is disabled", Response.Status.BAD_REQUEST));
        }
    }

    private void validateRedirectUriAndClient(String redirectUri, String clientId) {
        if (redirectUri != null && clientId == null) {
            throw new WebApplicationException(ErrorResponse.error("Client id missing", Response.Status.BAD_REQUEST));
        }
    }

    private ClientModel getEnabledClientOrFail(String clientId) {
        ClientModel client = realm.getClientByClientId(clientId);
        if (client == null) {
            logger.debugf("Client %s doesn't exist", clientId);
            throw new WebApplicationException(ErrorResponse.error("Client doesn't exist", Response.Status.BAD_REQUEST));
        }
        if (!client.isEnabled()) {
            logger.debugf("Client %s is not enabled", clientId);
            throw new WebApplicationException(ErrorResponse.error("Client is not enabled", Response.Status.BAD_REQUEST));
        }
        return client;
    }

    private Response setEmailTheme(String themeRealmName) {
        if (!StringUtils.isBlank(themeRealmName)) {
            RealmManager realmManager = new RealmManager(session);
            RealmModel themeRealm = realmManager.getRealmByName(themeRealmName);
            if (themeRealm == null) {
                return ErrorResponse.error("Invalid realm name", Response.Status.BAD_REQUEST);
            }
            if (themeRealm.getEmailTheme() != null) {
                RealmWithOverridenEmailTheme overridenRealm = new RealmWithOverridenEmailTheme(realm);
                overridenRealm.setEmailTheme(themeRealm.getEmailTheme());
                session.getContext().setRealm(overridenRealm);
            }
        }
        return null;
    }
}
