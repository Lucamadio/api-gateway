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

package com.predic8.membrane.examples.util;

import com.predic8.membrane.test.HttpAssertions;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.test.StringAssertions.assertContains;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoadBalancerUtil {
	private static final Pattern nodePattern = Pattern.compile("Node (\\d+)");

	public static int getRespondingNode(String url) throws Exception {
		try (HttpAssertions ha = new HttpAssertions()) {
			Matcher m = nodePattern.matcher(ha.getAndAssert200(url));
			assertTrue(m.find());
			return Integer.parseInt(m.group(1));
		}
	}

	public static void addLBNodeViaHTML(String adminBaseURL, String nodeHost, int nodePort) throws Exception {
		try (HttpAssertions ha = new HttpAssertions()) {
			HttpPost post = new HttpPost(adminBaseURL + "node/save") {{
				setEntity(new UrlEncodedFormEntity(new ArrayList<>() {{
					add(new BasicNameValuePair("balancer", "Default"));
					add(new BasicNameValuePair("cluster", "Default"));
					add(new BasicNameValuePair("host", nodeHost));
					add(new BasicNameValuePair("port", "" + nodePort));
				}}));
			}};
			ha.assertStatusCode(302, post);
		}
	}

	public static void assertNodeStatus(String adminPageHTML, String nodeHost, int nodePort,
			String expectedNodeStatus) {
		for (String row : adminPageHTML.split("<tr>")) {
			if (row.contains(nodeHost + ":" + nodePort)) {
				assertContains(expectedNodeStatus, row);
				return;
			}
		}
		throw new AssertionError("Node " + nodeHost + ":" + nodePort + " not found in " + adminPageHTML);
	}

	public static void checkWhatNodesAreResponding(int[] nodes) throws Exception {
		List<Integer> nodeNumbers  = new ArrayList<>();
		nodeNumbers.add(getRespondingNode("http://localhost:3023/service"));
		nodeNumbers.add(getRespondingNode("http://localhost:3023/service"));
		nodeNumbers.add(getRespondingNode("http://localhost:3023/service"));
		nodeNumbers.add(getRespondingNode("http://localhost:3023/service"));
		nodeNumbers.add(getRespondingNode("http://localhost:3023/service"));

		for (int node: nodes) {
			assertTrue(nodeNumbers.contains(node));
		}
	}
}
