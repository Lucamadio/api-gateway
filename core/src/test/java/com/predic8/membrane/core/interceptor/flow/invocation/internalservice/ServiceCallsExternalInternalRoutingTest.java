/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.flow.invocation.internalservice;

import com.predic8.membrane.core.proxies.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.*;
import static org.junit.jupiter.api.Assertions.*;

public class ServiceCallsExternalInternalRoutingTest extends AbstractInternalServiceRoutingInterceptorTest {

    protected void configure() throws Exception {

        api(a -> {
            a.setKey(new ServiceProxyKey("*","*",null,2000));
            a.add(A);
            a.getTarget().setUrl("internal://b");
        });

        internal(b -> {
            b.setName("b");
            b.add(B);
            b.getTarget().setHost("localhost");  // Test also!
            b.getTarget().setPort(2001);
        });

        api(c -> {
            c.setKey(new ServiceProxyKey("*", "*", null, 2001));
            c.add(C, RETURN);
        });
    }

    @Test
    void test() {
        assertEquals(">a>b>c<c<b<a", call());
    }
}
