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

package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.util.BufferLogger;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.predic8.membrane.test.AssertUtils.postAndAssert;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddSoapHeaderTest extends DistributionExtractingTestcase {
    @Test
    public void test() throws IOException, InterruptedException, ParserConfigurationException, SAXException {
        File baseDir = getExampleDir("/soap/add-soap-header");

        BufferLogger b = new BufferLogger();
        Process2 mvn = new Process2.Builder().in(baseDir).executable("mvn package").withWatcher(b).start();
        try {
            int exitCode = mvn.waitFor(60000);
            if (exitCode != 0)
                throw new RuntimeException("Maven exited with code " + exitCode + ": " + b.toString());
        } finally {
            mvn.killScript();
        }

        BufferLogger proxyWatcher = new BufferLogger();
        Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().withWatcher(proxyWatcher).start();
        try {
            String body = new String(Files.readAllBytes(Paths.get(baseDir + FileSystems.getDefault().getSeparator()
                    + "soap-message-without-header.xml")));
            String[] headers = {"Content-Type", "application/xml"};
            String response = postAndAssert(200,"http://localhost:2000/", headers, body);
            Thread.sleep(1000);


            assertTrue(proxyWatcher.toString().contains("wss:Security"));
        } finally {
            sl.killScript();
        }
    }

}
