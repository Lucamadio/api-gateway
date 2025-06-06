/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.interceptor.oauth2.processors.*;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.NotNull;
import org.jose4j.lang.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

@SuppressWarnings("LoggingSimilarMessage")
@MCElement(name = "oauth2authserver")
public class OAuth2AuthorizationServerInterceptor extends AbstractInterceptor {
    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthorizationServerInterceptor.class.getName());
    public static final Set<@NotNull String> SUPPORTED_AUTHORIZATION_GRANTS = Set.of("code", "token", "id_token token");

    private String issuer;
    private String location;
    private String basePath;
    private String path = "/login/";
    private String message;
    private String consentFile;
    private boolean exposeUserCredentialsToSession;
    private boolean loginViewDisabled = false;
    private boolean issueNonSpecIdTokens = false;
    private boolean issueNonSpecRefreshTokens = false;

    private UserDataProvider userDataProvider;
    private SessionManager sessionManager = new SessionManager();
    private AccountBlocker accountBlocker;
    private ClientList clientList;
    private TokenGenerator tokenGenerator = new BearerTokenGenerator();


    private TokenGenerator refreshTokenGenerator = new BearerTokenGenerator();
    private ClaimList claimList;
    private OAuth2Statistics statistics;

    private JwtGenerator jwtGenerator;
    private OAuth2Processors processors = new OAuth2Processors();
    private HashSet<String> supportedAuthorizationGrants = new HashSet<>();
    private SessionFinder sessionFinder = new SessionFinder();
    private WellknownFile wellknownFile = new WellknownFile();
    private ConsentPageFile consentPageFile = new ConsentPageFile();

    @Override
    public void init() {
        super.init();
        name = "oauth2 authorization server";
        setFlow(Flow.Set.REQUEST_RESPONSE_ABORT_FLOW);

        basePath = computeBasePath();
        if (basePath.endsWith("/"))
            throw new RuntimeException("When <oauth2AuthorizationServer> is nested in a <serviceProxy> with a <path>, the path should not end in a '/'.");

        if (refreshTokenConfig != null)
            refreshTokenGenerator = refreshTokenConfig.tokenGenerator;

        try {
            tokenGenerator.init(router);
            refreshTokenGenerator.init(router);
        } catch (Exception e) {
            throw new ConfigurationException("Could not create token generators.",e);
        }

        addSupportedAuthorizationGrants();

        try {
            getWellknownFile().init(this);
        } catch (IOException e) {
            throw new ConfigurationException("Could not create Well-known file.",e);
        }

        try {
            getConsentPageFile().init(router,getConsentFile());
        } catch (IOException e) {
            throw new ConfigurationException("Could not create Consent Page file.",e);
        }
        if (userDataProvider == null)
            throw new ConfigurationException("No userDataProvider configured. - Cannot work without one.");
        if (getClientList() == null)
            throw new ConfigurationException("No clientList configured. - Cannot work without one.");
        if (getClaimList() == null)
            throw new ConfigurationException("No scopeList configured. - Cannot work without one");
        if(getLocation() == null) {
            log.warn("===========================================================================================");
            log.warn("IMPORTANT: No location configured - Authorization code and implicit flows are not available");
            log.warn("===========================================================================================");
            loginViewDisabled = true;
        }
        if(getConsentFile() == null && !isLoginViewDisabled()){
            log.warn("==============================================================================================");
            log.warn("IMPORTANT: No consentFile configured - Authorization code and implicit flows are not available");
            log.warn("==============================================================================================");
            loginViewDisabled = true;
        }
        if(getPath() == null)
            throw new ConfigurationException("No path configured. - Cannot work without one");
        userDataProvider.init(router);
        getClientList().init(router);
        getClaimList().init(router);
        try {
            jwtGenerator = new JwtGenerator();
        } catch (JoseException e) {
            log.error("",e);
            throw new ConfigurationException("Could not generate JwtGenerator");
        }
        sessionManager.init(router);
        statistics = new OAuth2Statistics();
        addDefaultProcessors();
        new CleanupThread(sessionManager, accountBlocker).start();
    }

    private void addDefaultProcessors() {
        List.of(
                new InvalidMethodProcessor(this),
                new FaviconEndpointProcessor(this),
                new AuthEndpointProcessor(this),
                new TokenEndpointProcessor(this),
                new UserinfoEndpointProcessor(this),
                new RevocationEndpointProcessor(this),
                new LoginDialogEndpointProcessor(this),
                new WellknownEndpointProcessor(this),
                new CertsEndpointProcessor(this),
                new EmptyEndpointProcessor(this),
                new DefaultEndpointProcessor(this)
        ).forEach(processors::add);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        Outcome outcome = processors.runProcessors(exc);
        if (outcome != Outcome.CONTINUE)
            sessionManager.postProcess(exc);
        return outcome;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        sessionManager.postProcess(exc);
        return super.handleResponse(exc);
    }

    public UserDataProvider getUserDataProvider() {
        return userDataProvider;
    }

    @Required
    @MCChildElement(order = 1)
    public void setUserDataProvider(UserDataProvider userDataProvider) {
        this.userDataProvider = userDataProvider;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @MCChildElement(order = 2)
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public String getLocation() {
        return location;
    }

    /**
     * @description Base path under which the login dialog will be served.
     * @example logindialog
     */
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public String getPath() {
        return path;
    }

    @MCAttribute
    public void setPath(String path) {
        this.path = path;
    }

    public String getMessage() {
        return message;
    }

    @MCAttribute
    public void setMessage(String message) {
        this.message = message;
    }

    public AccountBlocker getAccountBlocker() {
        return accountBlocker;
    }

    @MCChildElement(order = 3)
    public void setAccountBlocker(AccountBlocker accountBlocker) {
        this.accountBlocker = accountBlocker;
    }

    public boolean isExposeUserCredentialsToSession() {
        return exposeUserCredentialsToSession;
    }

    @MCAttribute
    public void setExposeUserCredentialsToSession(boolean exposeUserCredentialsToSession) {
        this.exposeUserCredentialsToSession = exposeUserCredentialsToSession;
    }

    public ClientList getClientList() {
        return clientList;
    }

    @Required
    @MCChildElement(order = 4)
    public void setClientList(ClientList clientList) {
        this.clientList = clientList;
    }

    public TokenGenerator getTokenGenerator() {
        return tokenGenerator;
    }

    @MCChildElement(order = 5)
    public void setTokenGenerator(TokenGenerator tokenGenerator) {
        this.tokenGenerator = tokenGenerator;
    }

    public HashSet<String> getSupportedAuthorizationGrants() {
        return supportedAuthorizationGrants;
    }

    public void setSupportedAuthorizationGrants(HashSet<String> supportedAuthorizationGrants) {
        this.supportedAuthorizationGrants = supportedAuthorizationGrants;
    }

    public OAuth2Processors getProcessors() {
        return processors;
    }

    public void setProcessors(OAuth2Processors processors) {
        this.processors = processors;
    }

    public SessionFinder getSessionFinder() {
        return sessionFinder;
    }

    public void setSessionFinder(SessionFinder sessionFinder) {
        this.sessionFinder = sessionFinder;
    }

    public JwtGenerator getJwtGenerator() {
        return jwtGenerator;
    }

    public String getIssuer() {
        return issuer;
    }

    @Required
    @MCAttribute
    public void setIssuer(String issuer) {
        this.issuer = issuer;
        if (issuer.endsWith("/"))
            log.warn("In <oauth2authserver>, the 'issuer' attribute ends with a '/'. This should be avoided.");
    }

    public ClaimList getClaimList() {
        return claimList;
    }

    @Required
    @MCChildElement(order = 7)
    public void setClaimList(ClaimList claimList) {
        this.claimList = claimList;
    }


    public WellknownFile getWellknownFile() {
        return wellknownFile;
    }

    public void setWellknownFile(WellknownFile wellknownFile) {
        this.wellknownFile = wellknownFile;
    }

    public String getConsentFile() {
        return consentFile;
    }

    @MCAttribute
    public void setConsentFile(String consentFile) {
        this.consentFile = consentFile;
    }

    public ConsentPageFile getConsentPageFile() {
        return consentPageFile;
    }

    public void setConsentPageFile(ConsentPageFile consentPageFile) {
        this.consentPageFile = consentPageFile;
    }

    @Override
    public String getShortDescription() {
        return "Authorization server of the oauth2 authentication process.\n" + statistics.toString();
    }

    public void addSupportedAuthorizationGrants() {
        getSupportedAuthorizationGrants().addAll(SUPPORTED_AUTHORIZATION_GRANTS);
    }

    public OAuth2Statistics getStatistics() {
        return statistics;
    }

    public void setStatistics(OAuth2Statistics statistics) {
        this.statistics = statistics;
    }


    public TokenGenerator getRefreshTokenGenerator() {
        return refreshTokenGenerator;
    }

    public void setRefreshTokenGenerator(TokenGenerator refreshTokenGenerator) {
        this.refreshTokenGenerator = refreshTokenGenerator;
    }

    public boolean isLoginViewDisabled() {
        return loginViewDisabled;
    }

    public void setLoginViewDisabled(boolean loginViewDisabled) {
        this.loginViewDisabled = loginViewDisabled;
    }

    public boolean isIssueNonSpecIdTokens() {
        return issueNonSpecIdTokens;
    }

    /**
     * @description Issue id-tokens also in credentials-flow and password-flow . The OIDC specification, which brings in id-tokens, does not handle those flows, which is why the default value is false.
     * @default false
     */
    @MCAttribute
    public void setIssueNonSpecIdTokens(boolean issueNonSpecIdTokens) {
        this.issueNonSpecIdTokens = issueNonSpecIdTokens;
    }

    public boolean isIssueNonSpecRefreshTokens() {
        return issueNonSpecRefreshTokens;
    }

    /**
     * @description Issue refresh-tokens also in credentials-flow. The OAuth2 specification does not issue refresh tokens in the credentials-flow, which is why the default value is false.
     * @default false
     */
    @MCAttribute
    public void setIssueNonSpecRefreshTokens(boolean issueNonSpecRefreshTokens) {
        this.issueNonSpecRefreshTokens = issueNonSpecRefreshTokens;
    }


    @MCElement(name="refresh")
    public static class RefreshTokenConfig {

        TokenGenerator tokenGenerator = new BearerTokenGenerator();

        public TokenGenerator getTokenGenerator() {
            return tokenGenerator;
        }

        @MCChildElement(order = 5)
        public void setTokenGenerator(TokenGenerator tokenGenerator) {
            this.tokenGenerator = tokenGenerator;
        }

    }

    private RefreshTokenConfig refreshTokenConfig = null;

    public RefreshTokenConfig getRefreshTokenConfig() {
        return refreshTokenConfig;
    }

    @MCChildElement(order=6)
    public void setRefreshTokenConfig(RefreshTokenConfig refreshTokenConfig) {
        this.refreshTokenConfig = refreshTokenConfig;
    }

    public String computeBasePath() {
        Proxy proxy = getProxy();
        if (proxy == null)
            return "";
        if (proxy.getKey().getPath() == null || proxy.getKey().isPathRegExp())
            return "";
        return proxy.getKey().getPath();
    }

    public String getBasePath() {
        return basePath;
    }
}
