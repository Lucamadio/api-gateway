/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.proxies;

import com.predic8.membrane.annot.*;

/**
 * @description <p>
 *              A service proxy can be deployed on front of a Web server, Web Service or a REST resource. It conceals
 *              the server and offers the same interface as the target server to its clients.
 *              </p>
 * @topic 1. Proxies and Flow
 */
@MCElement(name="serviceProxy")
public class ServiceProxy extends AbstractServiceProxy {

	public ServiceProxy() {
		this.key = new ServiceProxyKey(80);
	}

	public ServiceProxy(ServiceProxyKey ruleKey, String targetHost, int targetPort) {
		this.key = ruleKey;
		this.target.setHost(targetHost);
		this.target.setPort(targetPort);
	}

	public String getMethod() {
		return key.getMethod();
	}

	/**
	 * @description If set, Membrane will only consider this rule, if the method (GET, PUT, POST, DELETE, etc.)
	 *              header of incoming HTTP requests matches. The asterisk '*' matches any method.
	 * @default *
	 * @example GET
	 */
	@MCAttribute
	public void setMethod(String method) {
		((ServiceProxyKey)key).setMethod(method);
	}

}
