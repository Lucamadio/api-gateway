/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2.client.b2c;

import com.predic8.membrane.core.interceptor.session.FakeSyncSessionStoreManager;
import com.predic8.membrane.core.interceptor.session.SessionManager;

public class SyncB2CResourceTest extends OAuth2ResourceB2CTest {
    @Override
    protected SessionManager createSessionManager() {
        return new FakeSyncSessionStoreManager();
    }
}
