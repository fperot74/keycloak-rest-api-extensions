package io.cloudtrust.keycloak.restapi.tools;

import io.cloudtrust.exception.CloudtrustException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resources.LoginActionsService;

import javax.ws.rs.core.UriBuilder;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailHelper {
    private static final Pattern regexValidateProtoAndDomain = Pattern.compile("^(http[s]?:\\/\\/[^\\/]+)[\\/]?$");
    private static final Pattern regexExtractQueryPath = Pattern.compile("^(http[s]?:\\/\\/[^\\/]+)(.*)$");

    public static String validateBaseURL(String whiteLabelledBaseURL) throws CloudtrustException {
        Matcher m = regexValidateProtoAndDomain.matcher(whiteLabelledBaseURL);
        if (m.find()) {
            return m.group(1);
        }
        throw new CloudtrustException("Invalid white labelled value " + whiteLabelledBaseURL);
    }

    public static String evaluateLink(KeycloakSession session, String whiteLabelledBaseURL) {
        return evaluateLink(session, b -> b, session.getContext().getRealm().getName(), whiteLabelledBaseURL);
    }

    public static String evaluateLink(KeycloakSession session, Function<UriBuilder, UriBuilder> updater, String realmName, String whiteLabelledBaseURL) {
        UriBuilder builder = LoginActionsService.loginActionsBaseUrl(session.getContext().getUri());
        builder = updater.apply(builder);
        //String link = builder.build(realmName).toString() + "/";
        String link = builder.build(realmName).toString();
        if (whiteLabelledBaseURL != null) {
            Matcher m = regexExtractQueryPath.matcher(link);
            if (m.find()) {
                return whiteLabelledBaseURL + m.group(2);
            }
        }
        return link;
    }
}
