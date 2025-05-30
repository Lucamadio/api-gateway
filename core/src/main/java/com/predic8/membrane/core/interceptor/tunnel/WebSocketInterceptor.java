/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.tunnel;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.transport.ws.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.ALLOW_WEBSOCKET;
import static java.lang.Boolean.TRUE;

/**
 * @description Allow HTTP protocol upgrades to the <a
 *              href="http://tools.ietf.org/html/rfc6455">WebSocket protocol</a>.
 *              After the upgrade, the connection's data packets are simply forwarded
 *              and not inspected.
 * @default false
 */
@MCElement(name = "webSocket")
public class WebSocketInterceptor extends AbstractInterceptor {

	protected static final Logger log = LoggerFactory.getLogger(WebSocketInterceptor.class);

	private String url;
	private String pathQuery;
	private List<WebSocketInterceptorInterface> interceptors = new ArrayList<>();

	@Override
	public void init() {
		super.init();
        try {
            pathQuery = url == null ? null : URLUtil.getPathQuery(getRouter().getUriFactory(), url);
        } catch (URISyntaxException e) {
            throw new ConfigurationException("Could not parse " + url,e);
        }
    }

	@Override
	public String getDisplayName() {
		return "websocket";
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		if ("websocket".equalsIgnoreCase(exc.getRequest().getHeader().getFirstValue("Upgrade"))) {
			exc.setProperty(ALLOW_WEBSOCKET, TRUE);
			if (url != null) {
				exc.getRequest().setUri(pathQuery);
				exc.getDestinations().set(0, url);
			}
		}
		return Outcome.CONTINUE;
	}

	@Override
	public String getShortDescription() {
		return "Allow HTTP protocol upgrades to the <a href=\"http://tools.ietf.org/html/rfc6455\">WebSocket protocol</a>. After the upgrade, the connection's data packets are simply forwarded and not inspected.";
	}

	public String getUrl() {
		return url;
	}

	/**
     * @description The URL the WebSocket connection will be forwarded to. The (host,port) pair specifies the target server.
     * The (path,query) part are sent to the target server on the initial request. (For example, ActiveMQ listens on port
     * 61614 and expects the incoming WebSocket connection to have a path '/' and empty query.)
     * @example <a href="http://localhost:61614/">http://localhost:61614/</a>
     */
	@MCAttribute
	public void setUrl(String url) {
		this.url = url;
	}

	public List<WebSocketInterceptorInterface> getInterceptors() {
		return interceptors;
	}

	@MCChildElement
	public void setInterceptors(List<WebSocketInterceptorInterface> interceptors) {
		this.interceptors = interceptors;
	}
}
