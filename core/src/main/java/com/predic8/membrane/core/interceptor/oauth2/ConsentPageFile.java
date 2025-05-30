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

import com.fasterxml.jackson.core.type.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.resolver.*;
import org.apache.commons.io.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class ConsentPageFile {

    public static final String SCOPE_DESCRIPTIONS = "scope_descriptions";
    public static final String CLAIM_DESCRIPTIONS = "claim_descriptions";

    public static final String PRODUCT_NAME = "product_name";
    public static final String LOGO_URL = "logo_url";
    public static final String SCOPES = "scopes";
    public static final String CLAIMS = "claims";
    private ResolverMap resolver;

    private String productName;
    private String logoUrl;
    final ConcurrentHashMap<String,String> scopesToDescriptions = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String,String> claimsToDescriptions = new ConcurrentHashMap<>();
    private Map<String, Object> json;


    public void init(Router router, String url) throws IOException {
        resolver = router.getResolverMap();
        if(url == null) {
            createDefaults();
            return;
        }
        parseFile(getFromUrl(ResolverMap.combine(router.getBaseLocation(),url)));
    }

    private void parseFile(String consentPageFile) throws IOException {
        parseJson(consentPageFile);
        parseProductAndLogo();
        parseScopes();
        parseClaims();
    }

    private void parseJson(String consentPageFile) throws IOException {
        json = new ObjectMapper().readValue(consentPageFile, new TypeReference<>() {});
    }

    private void parseProductAndLogo() {
        setProductName((String) json.get(PRODUCT_NAME));
        setLogoUrl((String) json.get(LOGO_URL));
    }

    private void parseClaims() {
        claimsToDescriptions.putAll((Map<String, String>) json.get(CLAIMS));
    }

    private void parseScopes() {
        scopesToDescriptions.putAll((Map<String, String>) json.get(SCOPES));
    }

    private String getFromUrl(String url) throws IOException {
        return IOUtils.toString(resolver.resolve(url));
    }

    private void createDefaults() {
    }

    public String convertScope(String scope){
        if(!scopesToDescriptions.containsKey(scope))
            return scope;
        return scopesToDescriptions.get(scope);
    }

    public String convertClaim(String claim){
        if(!claimsToDescriptions.containsKey(claim))
            return claim;
        return claimsToDescriptions.get(claim);
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }
}
