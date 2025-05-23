/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.adminApi.AdminApiInterceptor;
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.proxies.*;
import org.springframework.beans.*;
import org.springframework.context.*;

import java.util.Set;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.ABORT;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description Adds the current state of HTTP requests and responses to an "exchange store".
 * @explanation Note that depending on the implementation of the exchange store, both request *and* response logging
 *              might both be required for the exchange to be saved.
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name="exchangeStore")
public class ExchangeStoreInterceptor extends AbstractInterceptor implements ApplicationContextAware {

	private static final String BEAN_ID_ATTRIBUTE_CANNOT_BE_USED = "bean id attribute cannot be used";
	private ApplicationContext applicationContext;

	private ExchangeStore store;
	private String exchangeStoreBeanId;

	private final Set<AbstractServiceProxy> serviceProxiesContainingAdminConsole = new HashSet<>();

	public ExchangeStoreInterceptor() {
		name = "  store interceptor";
	}

	public ExchangeStoreInterceptor(ExchangeStore exchangeStore) {
		this();
		setExchangeStore(exchangeStore);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		return handle(exc, REQUEST);
	}

	@Override
	public Outcome handleResponse(final Exchange exc) {
		return handle(exc, RESPONSE);
	}

	@Override
	public void handleAbort(Exchange exc) {
		handle(exc, ABORT);
	}

	protected Outcome handle(Exchange exc, Flow flow) {
		if (serviceProxiesContainingAdminConsole.contains(exc.getProxy())) {
			return CONTINUE;
		}
		store.snap(exc, flow);
		return CONTINUE;
	}

	public ExchangeStore getExchangeStore() {
		return store;
	}

	/**
	 * @description Bean name of the exchange store defined as a spring bean.
	 * @example forgetfulExchangeStore
	 */
	@MCAttribute(attributeName="name")
	public void setExchangeStore(ExchangeStore exchangeStore) {
		store = exchangeStore;
		exchangeStoreBeanId = BEAN_ID_ATTRIBUTE_CANNOT_BE_USED;
	}

	public String getExchangeStoreBeanId() {
		return exchangeStoreBeanId;
	}

	/**
	 * @deprecated use {@link #setExchangeStore(ExchangeStore)} instead: Using
	 *             {@link #setExchangeStoreBeanId(String)} from Spring works,
	 *             but does not create a Spring bean dependency.
	 */
	@Deprecated
	public void setExchangeStoreBeanId(String exchangeStoreBeanId) {
		this.exchangeStoreBeanId = exchangeStoreBeanId;
	}

	@Override
	public void init() {
		super.init();

		//noinspection StatementWithEmptyBody
		if (Objects.equals(exchangeStoreBeanId, BEAN_ID_ATTRIBUTE_CANNOT_BE_USED))
			; // do nothing as "store" was already set via #setExchangeStore(ExchangeStore)
		else if (exchangeStoreBeanId != null)
			store = applicationContext.getBean(exchangeStoreBeanId, ExchangeStore.class);
		else
			store = router.getExchangeStore();

		store.init(router);

		searchAdminConsole();
	}

	private void searchAdminConsole() {
		for (Proxy r : router.getRuleManager().getRules()) {
			if (!(r instanceof AbstractServiceProxy)) continue;

			for (Interceptor i : r.getInterceptors()) {
				if (i instanceof AdminConsoleInterceptor) {
					serviceProxiesContainingAdminConsole.add((AbstractServiceProxy)r);
				}
				if (i instanceof AdminApiInterceptor) {
					serviceProxiesContainingAdminConsole.add((AbstractServiceProxy)r);
				}
			}
		}
	}

	@Override
	public String getShortDescription() {
		return "Logs all exchanges (requests and responses) into an exchange store "+
				"that can be inspected using <a href=\"http://www.membrane-soa.org/soap-monitor/\">Membrane Monitor</a>.";
	}
}
