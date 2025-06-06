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

package com.predic8.membrane.core.util.jdbc;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.util.ConfigurationException;

import javax.sql.DataSource;
import java.util.Map;

public abstract class AbstractJdbcSupport {

    private DataSource datasource;
    private Router router;
    private static final String DATASOURCE_SAMPLE = """
             Sample:
            
             <spring:bean id="dataSource" class="org.apache.commons.dbcp2.BasicDataSource">
                 <spring:property name="driverClassName" value="org.postgresql.Driver" />
                 <spring:property name="url" value="jdbc:postgresql://localhost:5432/postgres" />
                 <spring:property name="username" value="user" />
                 <spring:property name="password" value="password" />
             </spring:bean>
            
             <router>
                 <api port="2000">
                     <apiKey>
                         <databaseApiKeyStore datasource="dataSource">
                             <keyTable>key</keyTable>
                             <scopeTable>scope</scopeTable>
                         </databaseApiKeyStore>
                         <headerExtractor />
                     </apiKey>
                 </api>
             </router>
            """;

    public void init(Router router) {
        this.router = router;
        getDatasourceIfNull();
    }

    private void getDatasourceIfNull() {
        if (datasource != null)
            return;

        Map<String, DataSource> beans = router.getBeanFactory().getBeansOfType(DataSource.class);

        DataSource[] datasources = beans.values().toArray(new DataSource[0]);
        if (datasources.length == 0) {
            datasource = datasources[0];
            return;
        }
        if (datasources.length > 1) {
            throw new ConfigurationException("""
                        More than one DataSource found in configuration. Specify the dataSource name explicitly.
                        %s
                    """.formatted(DATASOURCE_SAMPLE));
        }
        throw new RuntimeException("""
                No datasource found - specifiy a DataSource bean in your configuration
                %s
                """.formatted(DATASOURCE_SAMPLE));
    }

    @MCAttribute
    public void setDatasource(DataSource datasource) {
        this.datasource = datasource;
    }

    public DataSource getDatasource() {
        return datasource;
    }
}
