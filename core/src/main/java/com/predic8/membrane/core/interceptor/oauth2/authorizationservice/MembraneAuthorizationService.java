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

package com.predic8.membrane.core.interceptor.oauth2.authorizationservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.oauth2.ClaimRenamer;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Util;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import com.predic8.membrane.core.resolver.ResolverMap;
import org.apache.commons.io.IOUtils;
import com.predic8.membrane.annot.Required;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@MCElement(name="membrane")
public class MembraneAuthorizationService extends AuthorizationService {
    private String src; // url to OpenID-Provider data
    private String internalSrc;

    private String issuer;
    private String tokenEndpoint;
    private String userInfoEndpoint;
    private String subject = ClaimRenamer.convert("sub");
    private String authorizationEndpoint;
    private String publicAuthorizationEndpoint;
    private String revocationEndpoint;
    private String registrationEndpoint;
    private String jwksEndpoint;
    private String endSessionEndpoint;
    private String claims;
    private String claimsIdt;
    private String claimsParameter;
    private List<String> responseModesSupported = List.of("query", "fragment");

    private DynamicRegistration dynamicRegistration;

    protected boolean encodedScope;

    public static boolean isValidURI(String uri)
    {
        try
        {
            new URI(uri);
            return true;
        } catch (Exception exception)
        {
            return false;
        }
    }

    @Override
    public void init() throws Exception {
        if(src == null)
            throw new Exception("No wellknown file source configured. - Cannot work without one");
        if(dynamicRegistration != null){
            dynamicRegistration.init(router);
            supportsDynamicRegistration = true;
        }
        parseSrc(resolve(
                router.getResolverMap(),
                router.getBaseLocation(),
                getWellKnownUrl(internalSrc == null ? src : internalSrc)));
        if(internalSrc != null) {
            publicAuthorizationEndpoint = src + new URI(authorizationEndpoint).getPath();
        }
        adjustScope();
        prepareClaimsForLoginUrl();
    }

    private @NotNull String getWellKnownUrl(String baseUrl) {
        return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + ".well-known/openid-configuration";
    }

    public InputStream resolve(ResolverMap rm, String baseLocation, String url) throws Exception {
        return dynamicRegistration != null ?
                dynamicRegistration.retrieveOpenIDConfiguration(url) :
                super.resolve(rm, baseLocation, url);
    }

    @Override
    public String getIssuer() {
        return issuer;
    }

    @Override
    public String getJwksEndpoint() throws Exception {
        return jwksEndpoint;
    }

    @Override
    public String getEndSessionEndpoint() throws Exception {
        return endSessionEndpoint;
    }

    @Override
    protected void doDynamicRegistration(List<String> callbackURLs) throws Exception {
        if(dynamicRegistration == null || registrationEndpoint == null || registrationEndpoint.isEmpty())
            throw new RuntimeException("A registration bean is required and src needs to specify a registration endpoint");

        dynamicRegistrationIfNeeded(callbackURLs);
    }

    private void dynamicRegistrationIfNeeded(List<String> callbackUris) throws Exception {
        Client client = dynamicRegistration.registerWithCallbackAt(callbackUris, registrationEndpoint);
        setClientIdAndSecret(client.getClientId(), client.getClientSecret());
    }

    private void prepareClaimsForLoginUrl() throws IOException {
        claimsParameter = ClaimsParameter.writeCompleteJson(claims,claimsIdt);
        if(claimsParameter.isEmpty())
            claimsParameter = null;
    }

    @Override
    public void setScope(String scope) {
        super.setScope(scope);
        encodedScope = false;
    }

    private void adjustScope() throws UnsupportedEncodingException {
        if(scope == null)
            scope = "profile";
        if (!encodedScope) {
            scope = OAuth2Util.urlencode(scope);
            encodedScope = true;
        }
    }

    private void parseSrc(InputStream resolve) throws IOException {
        String file = IOUtils.toString(resolve);
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Object> json = mapper.readValue(file, new TypeReference<>() {});

        // without checks
        tokenEndpoint = (String) json.get("token_endpoint");
        if (tokenEndpoint == null)
            throw new RuntimeException("No token_endpoint could be detected.");
        userInfoEndpoint = (String) json.get("userinfo_endpoint");
        authorizationEndpoint = (String) json.get("authorization_endpoint");
        revocationEndpoint = (String) json.get("revocation_endpoint");
        registrationEndpoint = (String) json.get("registration_endpoint");
        jwksEndpoint = (String) json.get("jwks_uri");
        endSessionEndpoint = (String) json.get("end_session_endpoint");
        issuer = (String) json.get("issuer");
        if (json.containsKey("response_modes_supported")) {
            List<?> v = (List<?>) json.get("response_modes_supported");
            responseModesSupported = v.stream()
                    .filter(i -> i instanceof String)
                    .map(i -> (String)i)
                    .collect(Collectors.<String>toList());
        }
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    @Override
    public String getRevocationEndpoint() {
        return revocationEndpoint;
    }

    @Override
    public String getLoginURL(String callbackURL) {
        String endpoint = publicAuthorizationEndpoint;
        if(endpoint == null)
            endpoint = authorizationEndpoint;
        boolean formPostSupported = responseModesSupported.contains("form_post");
        return endpoint +"?"+
                "client_id=" + getClientId() + "&"+
                "response_type=code&"+
                "scope="+scope+"&"+
                "redirect_uri=" + callbackURL +
                (formPostSupported ? "&response_mode=form_post" : "") +
                getClaimsParameter();
    }

    private String getClaimsParameter() {
        if(claimsParameter == null)
            return "";
        return "&claims=" + OAuth2Util.urlencode(claimsParameter);
    }

    @Override
    public String getUserInfoEndpoint() {
        return userInfoEndpoint;
    }

    @Override
    public String getSubject() {
        return subject;
    }

    @MCAttribute
    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSrc() {
        return src;
    }

    /**
     * Base URL of the OIDC Discovery compliant Authorization Server.
     */
    @Required
    @MCAttribute
    public void setSrc(String src) {
        this.src = src;
    }

    public String getInternalSrc() {
        return internalSrc;
    }

    /**
     * Internal Base URL of the OIDC Discovery compliant Authorization Server. Needs only to be set,
     * if access to the Authorization Server is also routed via Membrane API Gateway. Usually points
     * to an internal proxy, e.g. "internal://oauth2-gw/", which routes to the Authorization Server.
     */
    @MCAttribute
    public void setInternalSrc(String internalSrc) {
        this.internalSrc = internalSrc;
    }

    public String getClaims() {
        return claims;
    }

    /**
     *
     * @description claims that are requested for the userinfo endpoint
     */
    @MCAttribute
    public void setClaims(String claims) {
        this.claims = claims;
    }

    public String getClaimsIdt() {
        return claimsIdt;
    }

    /**
     *
     * @description claims that are requested for the id_token
     */
    @MCAttribute
    public void setClaimsIdt(String claimsIdt) {
        this.claimsIdt = claimsIdt;
    }

    public DynamicRegistration getDynamicRegistration() {
        return dynamicRegistration;
    }

    /**
     * @description defines a chain of interceptors that are run for the dynamic registration process of openid-connect
     */
    @MCChildElement(order=10)
    public void setDynamicRegistration(DynamicRegistration dynamicRegistration) {
        this.dynamicRegistration = dynamicRegistration;
    }

    public List<String> getResponseModesSupported() {
        return responseModesSupported;
    }
}
