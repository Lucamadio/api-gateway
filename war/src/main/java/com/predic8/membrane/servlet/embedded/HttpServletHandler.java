/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.servlet.embedded;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.transport.http.AbortException;
import com.predic8.membrane.core.transport.http.AbstractHttpHandler;
import com.predic8.membrane.core.transport.http.EOFWhileReadingFirstLineException;
import com.predic8.membrane.core.util.DNSCache;
import com.predic8.membrane.core.util.EndOfStreamException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Enumeration;

import static com.predic8.membrane.core.http.Header.TRANSFER_ENCODING;

class HttpServletHandler extends AbstractHttpHandler {
	private static final Logger log = LoggerFactory.getLogger(HttpServletHandler.class);

	private final HttpServletRequest request;
	private final HttpServletResponse response;
	private final InetAddress remoteAddr, localAddr;

	public HttpServletHandler(HttpServletRequest request, HttpServletResponse response,
			Transport transport) throws IOException {
		super(transport);
		this.request = request;
		this.response = response;
		remoteAddr = InetAddress.getByName(request.getRemoteAddr());
		localAddr = InetAddress.getByName(request.getLocalAddr());
		exchange = new Exchange(this);

		exchange.setProperty(Exchange.HTTP_SERVLET_REQUEST, request);
	}

	public void run() {
		try {
			srcReq = createRequest();

			exchange.received();

			try {
				DNSCache dnsCache = getTransport().getRouter().getDnsCache();
				String ip = dnsCache.getHostAddress(remoteAddr);
				exchange.setRemoteAddrIp(ip);
				exchange.setRemoteAddr(getTransport().isReverseDNS() ? dnsCache.getHostName(remoteAddr) : ip);

				exchange.setRequest(srcReq);
				exchange.setOriginalRequestUri(srcReq.getUri());

				invokeHandlers();
			} catch (AbortException e) {
				exchange.finishExchange(true, exchange.getErrorMessage());
				writeResponse(exchange.getResponse());
				return;
			}

			exchange.getRequest().readBody(); // read if not already read
			writeResponse(exchange.getResponse());
			exchange.setCompleted();
		} catch (EndOfStreamException e) {
			log.debug("stream closed");
		} catch (EOFWhileReadingFirstLineException e) {
			log.debug("Client connection terminated before line was read. Line so far: ("
					+ e.getLineSoFar() + ")");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			exchange.detach();
		}

	}

	protected void writeResponse(Response res) throws Exception {
		response.setStatus(res.getStatusCode());
		for (HeaderField header : res.getHeader().getAllHeaderFields()) {
			if (header.getHeaderName().hasName(TRANSFER_ENCODING))
				continue;
			response.addHeader(header.getHeaderName().toString(), header.getValue());
		}

		ServletOutputStream out = response.getOutputStream();
		res.getBody().write(new PlainBodyTransferrer(out), false);
		out.flush();

		response.flushBuffer();

		exchange.setTimeResSent(System.currentTimeMillis());
		exchange.collectStatistics();
	}

	private Request createRequest() throws IOException {
		Request srcReq = new Request();

		String pathQuery = request.getRequestURI();
		if (request.getQueryString() != null)
			pathQuery += "?" + request.getQueryString();

		if (getTransport().isRemoveContextRoot()) {
			String contextPath = request.getContextPath();
			if (!contextPath.isEmpty() && pathQuery.startsWith(contextPath))
				pathQuery = pathQuery.substring(contextPath.length());
		}

		srcReq.create(
				request.getMethod(),
				pathQuery,
				request.getProtocol(),
				createHeader(),
				request.getInputStream());
		return srcReq;
	}

	private Header createHeader() {
		Header header = new Header();
		Enumeration<?> e = request.getHeaderNames();
		while (e.hasMoreElements()) {
			String key = (String)e.nextElement();
			Enumeration<?> e2 = request.getHeaders(key);
			while (e2.hasMoreElements()) {
				String value = (String)e2.nextElement();
				header.add(key, value);
			}
		}
		return header;
	}

	@Override
	public void shutdownInput() throws IOException {
		request.getInputStream().close();
		// nothing more we can do, since the servlet API does not give
		// us access to the TCP API
	}

	@Override
	public InetAddress getLocalAddress() {
		return localAddr;
	}

	@Override
	public int getLocalPort() {
		return request.getLocalPort();
	}

	@Override
	public ServletTransport getTransport() {
		return (ServletTransport)super.getTransport();
	}

	@Override
	public boolean isMatchLocalPort() {
		return false;
	}

	@Override
	public String getContextPath(Exchange exc) {
		if (!getTransport().isRemoveContextRoot())
			return "";
		return exc.getProperty(Exchange.HTTP_SERVLET_REQUEST, HttpServletRequest.class).getContextPath();
	}

}
