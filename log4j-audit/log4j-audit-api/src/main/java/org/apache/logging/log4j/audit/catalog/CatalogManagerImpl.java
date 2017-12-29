/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.audit.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.audit.exception.AuditException;
import org.apache.logging.log4j.audit.util.NamingUtils;
import org.apache.logging.log4j.catalog.api.Attribute;
import org.apache.logging.log4j.catalog.api.CatalogData;
import org.apache.logging.log4j.catalog.api.Event;
import org.apache.logging.log4j.catalog.api.CatalogReader;
import org.apache.logging.log4j.catalog.api.EventAttribute;

import static org.apache.logging.log4j.catalog.api.constant.Constants.DEFAULT_CATALOG;

/**
 *
 */
public class CatalogManagerImpl implements CatalogManager {

    private static Logger logger = LogManager.getLogger(CatalogManagerImpl.class);

    private volatile Map<String, Map<String, CatalogInfo>> infoMap;

    private Map<String, Attribute> requestContextAttributes = new HashMap<>();

    private final Map<String, Map<String, Attribute>> attributeMap = new HashMap<>();

    private static final String REQCTX = "ReqCtx_";



    protected CatalogData catalogData;

    public CatalogManagerImpl(CatalogReader catalogReader) {
        try {
            infoMap = initializeData(catalogReader);
        } catch (Exception ex) {
            throw new AuditException("Unable to initialize catalog data", ex);
        }
    }

    @Override
    public Event getEvent(String eventName, String catalogId) {
        CatalogInfo info = getCatalogInfo(eventName, catalogId);
        return info != null ? info.event : null;
    }

    @Override
    public List<String> getRequiredContextAttributes(String eventName, String catalogId) {
        Map<String, CatalogInfo> catalogMap = infoMap.get(catalogId == null ? DEFAULT_CATALOG : catalogId);
        return catalogMap != null ? catalogMap.get(eventName).requiredContextAttributes : null;
    }

    @Override
    public Map<String, Attribute> getAttributes(String eventName, String catalogId) {
        Event event = getEvent(eventName, catalogId);
        Map<String, Attribute> attributes = new HashMap<>(event.getAttributes().size());
        for (EventAttribute eventAttribute : event.getAttributes()) {
            Attribute attr = getAttribute(eventAttribute.getName(), event.getCatalogId());
            if (attr != null) {
                attributes.put(attr.getName(), attr);
            }
        }
        return attributes;
    }

    @Override
    public List<String> getAttributeNames(String eventName, String catalogId) {
        return infoMap.get(catalogId == null ? DEFAULT_CATALOG : catalogId).get(eventName).attributeNames;
    }

    @Override
    public Attribute getAttribute(String name) {
        Map<String, Attribute> attrMap = attributeMap.get(DEFAULT_CATALOG);
        return attrMap != null ? attrMap.get(name) : null;
    }

    public Attribute getAttribute(String name, String catalogId) {
        Map<String, Attribute> attrMap = attributeMap.get(catalogId);
        if (attrMap == null || !attrMap.containsKey(name)) {
            attrMap = attributeMap.get(DEFAULT_CATALOG);
        }
        return attrMap != null ? attrMap.get(name) : null;
    }

    @Override
    public Map<String, Attribute> getRequestContextAttributes() {
        return requestContextAttributes;
    }

    private CatalogInfo getCatalogInfo(String eventName, String catalogId) {
        Map<String, CatalogInfo> defaultCatalog = infoMap.get(DEFAULT_CATALOG);
        Map<String, CatalogInfo> catalog = catalogId != null ? infoMap.get(catalogId) : null;
        return catalog != null && catalog.containsKey(eventName) ? catalog.get(eventName) :
                defaultCatalog.get(eventName);
    }

    private Map<String, Map<String, CatalogInfo>> initializeData(CatalogReader catalogReader) throws Exception {
        String catalog = catalogReader.readCatalog();
        JsonFactory factory = new JsonFactory();
        factory.enable(JsonParser.Feature.ALLOW_COMMENTS);
        ObjectMapper mapper = new ObjectMapper(factory);
        catalogData = mapper.readValue(catalog, CatalogData.class);
        for (Attribute attr : catalogData.getAttributes()) {
            if (attr.isRequestContext()) {
                requestContextAttributes.put(attr.getName(), attr);
            }
            Map<String, Attribute> attrMap = attributeMap.get(attr.getCatalogId());
            if (attrMap == null) {
                attrMap = new HashMap<>();
                attributeMap.put(attr.getCatalogId(), attrMap);
            }
            attrMap.put(attr.getName(), attr);
        }
        Map<String, Map<String, CatalogInfo>> map = new HashMap<>();
        map.put(DEFAULT_CATALOG, new HashMap<>());
        for (Event event : catalogData.getEvents()) {
            CatalogInfo info = new CatalogInfo();
            info.event = event;
            String catalogId = event.getCatalogId();
            if (catalogId != null && catalogId.length() > 0 && !map.containsKey(catalogId)) {
                map.put(catalogId, new HashMap<>());
            }
            List<String> required = new ArrayList<>();
            List<String> names = new ArrayList<>();
            info.attributes = new HashMap<>(names.size());
            if (event.getAttributes() != null) {
                for (EventAttribute eventAttribute : event.getAttributes()) {
                    String name = eventAttribute.getName();
                    Attribute attribute = getAttribute(name, event.getCatalogId());
                    info.attributes.put(name, attribute);
                    if (name.indexOf('.') != -1) {
                        name = name.replaceAll("\\.", "");
                    }
                    if (name.indexOf('/') != -1) {
                        name = name.replaceAll("/", "");
                    }
                    if (attribute.isRequestContext()) {
                        if (attribute.isRequired()) {
                            if (name.startsWith(REQCTX)) {
                                name = name.substring(REQCTX.length());
                            }
                            required.add(name);
                        }
                    } else {
                        names.add(name);
                    }
                }
            }
            info.requiredContextAttributes = required;
            info.attributeNames = names;
            Map<String, CatalogInfo> catalogMap = catalogId == null ?
                    map.get(DEFAULT_CATALOG) : map.get(catalogId);
            catalogMap.put(NamingUtils.getFieldName(event.getName()), info);
        }
        return map;
    }

    private class CatalogInfo {
        private Event event;

        private List<String> requiredContextAttributes;

        private List<String> attributeNames;

        private Map<String, Attribute> attributes;
    }
}
