/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.ssl.acme;

import javax.net.ssl.SSLContext;

public class AcmeKeyCert {
    final String key;
    final String certs;
    final long validFrom;
    final long validUntil;
    final javax.net.ssl.SSLContext sslc;

    public AcmeKeyCert(String keyS, String certsS, long validFrom, long validUntil, javax.net.ssl.SSLContext sslc) {
        this.key = keyS;
        this.certs = certsS;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.sslc = sslc;
    }

    public long getValidFrom() {
        return validFrom;
    }

    public long getValidUntil() {
        return validUntil;
    }

    public SSLContext getSslContext() {
        return sslc;
    }

    public String getKey() {
        return key;
    }

    public String getCerts() {
        return certs;
    }
}