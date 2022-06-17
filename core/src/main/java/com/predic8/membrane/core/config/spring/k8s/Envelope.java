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
package com.predic8.membrane.core.config.spring.k8s;

import com.predic8.membrane.core.config.spring.K8sHelperGeneratorAutoGenerated;
import com.predic8.membrane.core.kubernetes.BeanRegistry;
import com.predic8.membrane.core.kubernetes.GenericYamlParser;
import org.yaml.snakeyaml.events.*;

import java.util.*;

import static com.predic8.membrane.core.config.spring.k8s.YamlLoader.readObj;
import static com.predic8.membrane.core.config.spring.k8s.YamlLoader.readString;

public class Envelope {
    String kind;
    String apiVersion;
    Metadata metadata;
    Object spec;
    Map<String, Object> additionalProperties = new HashMap<>();

    public void parse(Iterator<Event> events, BeanRegistry registry) {
        int state = 0;
        while (events.hasNext()) {
            Event event = events.next();
            switch (state) {
                case 0:
                    if (event instanceof MappingStartEvent)
                        state = 1;
                    break;
                case 1:
                    if (event instanceof ScalarEvent) {
                        String value = ((ScalarEvent)event).getValue();
                        switch (value) {
                            case "kind":
                                kind = readString(events);
                                break;
                            case "apiVersion":
                                apiVersion = readString(events);
                                break;
                            case "spec":
                                spec = readSpec(kind, events, registry);
                                break;
                            case "metadata":
                                metadata = readMetadata(events);
                                break;
                            default:
                                additionalProperties.put(value, readObj(events));
                                break;
                        }
                    } else if (event instanceof MappingEndEvent) {
                            return;
                    } else {
                        throw new IllegalStateException("Expected scalar or end-of-map in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
                    }
            }
        }
    }

    private Object readSpec(String kind, Iterator<Event> events, BeanRegistry registry) {
        Class clazz = K8sHelperGeneratorAutoGenerated.elementMapping.get(kind);
        if (clazz == null)
            throw new RuntimeException("Did not find java class for kind '" + kind + "'.");
        Object o = GenericYamlParser.parse(kind, clazz, events, registry);

        return o;
    }

    public static class Metadata {
        String name;
        String namespace;
        String uid;
        Map additionalProperties = new HashMap();

        public String getUid() {
            return uid;
        }
    }

    private Metadata readMetadata(Iterator<Event> events) {
        Event event = events.next();
        if (!(event instanceof MappingStartEvent))
            throw new IllegalStateException("Expected map in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
        Metadata metadata = new Metadata();
        while (events.hasNext()) {
            event = events.next();
            if (event instanceof ScalarEvent) {
                String value = ((ScalarEvent) event).getValue();
                switch (value) {
                    case "name":
                        metadata.name = readString(events);
                        break;
                    case "namespace":
                        metadata.namespace = readString(events);
                        break;
                    case "uid":
                        metadata.uid = readString(events);
                        break;
                    default:
                        metadata.additionalProperties.put(value, readObj(events));
                        break;
                }
            } else if (event instanceof MappingEndEvent) {
                break;
            } else {
                throw new IllegalStateException("Expected scalar or end-of-map in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
            }
        }
        return metadata;
    }

    public Object getSpec() {
        return spec;
    }

    public Metadata getMetadata() {
        return metadata;
    }
}
