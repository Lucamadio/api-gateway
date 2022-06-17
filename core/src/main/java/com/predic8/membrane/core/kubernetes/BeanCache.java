package com.predic8.membrane.core.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.spring.K8sHelperGeneratorAutoGenerated;
import com.predic8.membrane.core.config.spring.k8s.Envelope;
import com.predic8.membrane.core.config.spring.k8s.YamlLoader;
import com.predic8.membrane.core.kubernetes.client.WatchAction;
import com.predic8.membrane.core.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BeanCache implements BeanRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesWatcher.class);
    private final Router router;
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private ConcurrentHashMap<String, Object> uuidMap = new ConcurrentHashMap<>();
    private ArrayBlockingQueue<BeanDefinition> changeEvents = new ArrayBlockingQueue<>(1000);
    private Thread thread;

    public BeanCache(Router router) {
        this.router = router;
    }

    public void start() {
        thread = new Thread() {
            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    try {
                        BeanDefinition beanDefinition = changeEvents.take();
                        handle(beanDefinition);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

            }
        };
        thread.start();
    }

    public void stop() {
        if (thread != null)
            thread.interrupt();
    }

    public Envelope define(Map map) throws IOException {
        String s = mapper.writeValueAsString(map).substring(4);
        if (LOG.isDebugEnabled())
            LOG.debug("defining bean: " + s);
        YamlLoader y = new YamlLoader();
        Envelope envelope = y.load(new StringReader(s), this);
        System.err.println("SUCCESS.");
        return envelope;
    }

    /**
     * May be called from multiple threads.
     */
    public void handle(WatchAction action, Map m) throws IOException {
        changeEvents.add(new BeanDefinition(action, m));
    }

    // uid -> bean definition
    Map<String, BeanDefinition> bds = new HashMap<>();
    Set<String> uidsToActivate = new HashSet<>();

    void handle(BeanDefinition bd) {
        if (bd.getAction() == WatchAction.DELETED)
            bds.remove(bd.getUid());
        else
            bds.put(bd.getUid(), bd);

        if (bd.isRule())
            uidsToActivate.add(bd.getUid());

        if (changeEvents.size() == 0)
            activationRun();
    }

    public void activationRun() {
        System.err.println("---");
        Set<String> uidsToRemove = new HashSet<>();
        for (String uid : uidsToActivate) {
            BeanDefinition bd = bds.get(uid);
            try {
                Envelope envelope = define(bd.getMap());
                bd.setEnvelope(envelope);
                Rule newRule = (Rule) envelope.getSpec();
                try {
                    newRule.setName(bd.getName());
                    newRule.init(router);
                } catch (Exception e) {
                    throw new RuntimeException("Could not init rule.", e);
                }

                Rule oldRule = null;
                if (bd.getAction() == WatchAction.MODIFIED || bd.getAction() == WatchAction.DELETED)
                    oldRule = (Rule) uuidMap.get(bd.getUid());

                if (bd.getAction() == WatchAction.ADDED)
                    router.add(newRule);
                else if (bd.getAction() == WatchAction.DELETED)
                    router.getRuleManager().removeRule(oldRule);
                else if (bd.getAction() == WatchAction.MODIFIED)
                    router.getRuleManager().replaceRule(oldRule, newRule);

                if (bd.getAction() == WatchAction.ADDED || bd.getAction() == WatchAction.MODIFIED)
                    uuidMap.put(bd.getUid(), newRule);
                if (bd.getAction() == WatchAction.DELETED)
                    uuidMap.remove(bd.getUid());
                uidsToRemove.add(bd.getUid());
            } catch (Throwable e) {
                LOG.error("Could not handle " + bd.getAction() + " " + bd.getNamespace() + "/" + bd.getName(), e);
            }
        }
        for (String uid : uidsToRemove)
            uidsToActivate.remove(uid);
    }

    @Override
    public Object resolveReference(String url) {
        Optional<BeanDefinition> obd = bds.values().stream().filter(bd -> bd.getName().equals(url)).findFirst();
        if (obd.isPresent()) {
            BeanDefinition bd = obd.get();
            Envelope envelope = null;
            if (bd.getEnvelope() != null)
                envelope = bd.getEnvelope();
            if (envelope == null) {
                try {
                    envelope = define(bd.getMap());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (!"prototype".equals(bd.getScope()))
                    bd.setEnvelope(envelope);
            }
            Object spec = envelope.getSpec();
            if (spec instanceof Bean)
                return ((Bean) spec).getBean();
            return spec;
        }
        throw new RuntimeException("Reference " + url + " not found");
    }
}
