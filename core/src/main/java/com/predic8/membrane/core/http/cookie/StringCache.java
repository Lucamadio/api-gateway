/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.predic8.membrane.core.http.cookie;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * This class implements a String cache for ByteChunk and CharChunk.
 * <p>
 * Source: <a href="http://tomcat.apache.org/">...</a>
 * License:  <a href="http://www.apache.org/licenses/LICENSE-2.0">...</a>
 *
 * @author Remy Maucherat
 */
public class StringCache {


    // ------------------------------------------------------- Static Variables


    /**
     * Enabled ?
     */
    protected static boolean byteEnabled = ("true".equals(System.getProperty(
            "tomcat.util.buf.StringCache.byte.enabled", "false")));


    protected static boolean charEnabled = ("true".equals(System.getProperty(
            "tomcat.util.buf.StringCache.char.enabled", "false")));


    protected static int trainThreshold = Integer.parseInt(System.getProperty(
            "tomcat.util.buf.StringCache.trainThreshold", "20000"));


    protected static int cacheSize = Integer.parseInt(System.getProperty(
            "tomcat.util.buf.StringCache.cacheSize", "200"));


    protected static final int maxStringSize = Integer.parseInt(System.getProperty(
            "tomcat.util.buf.StringCache.maxStringSize", "128"));


    /**
     * Statistics hash map for byte chunk.
     */
    protected static final HashMap<ByteEntry, int[]> bcStats =
            new HashMap<>(cacheSize);


    /**
     * toString count for byte chunk.
     */
    protected static int bcCount = 0;


    /**
     * Cache for byte chunk.
     */
    protected static ByteEntry[] bcCache = null;


    /**
     * Statistics hash map for char chunk.
     */
    protected static final HashMap<CharEntry, int[]> ccStats = new HashMap<>(cacheSize);


    /**
     * toString count for char chunk.
     */
    protected static int ccCount = 0;


    /**
     * Cache for char chunk.
     */
    protected static CharEntry[] ccCache = null;


    /**
     * Access count.
     */
    protected static int accessCount = 0;


    /**
     * Hit count.
     */
    protected static int hitCount = 0;


    // ------------------------------------------------------------ Properties


    /**
     * @return Returns the cacheSize.
     */
    public int getCacheSize() {
        return cacheSize;
    }


    /**
     * @param cacheSize The cacheSize to set.
     */
    public void setCacheSize(int cacheSize) {
        StringCache.cacheSize = cacheSize;
    }


    /**
     * @return Returns the enabled.
     */
    public boolean getByteEnabled() {
        return byteEnabled;
    }


    /**
     * @param byteEnabled The enabled to set.
     */
    public void setByteEnabled(boolean byteEnabled) {
        StringCache.byteEnabled = byteEnabled;
    }


    /**
     * @return Returns the enabled.
     */
    public boolean getCharEnabled() {
        return charEnabled;
    }


    /**
     * @param charEnabled The enabled to set.
     */
    public void setCharEnabled(boolean charEnabled) {
        StringCache.charEnabled = charEnabled;
    }


    /**
     * @return Returns the trainThreshold.
     */
    public int getTrainThreshold() {
        return trainThreshold;
    }


    /**
     * @param trainThreshold The trainThreshold to set.
     */
    public void setTrainThreshold(int trainThreshold) {
        StringCache.trainThreshold = trainThreshold;
    }


    /**
     * @return Returns the accessCount.
     */
    public int getAccessCount() {
        return accessCount;
    }


    /**
     * @return Returns the hitCount.
     */
    public int getHitCount() {
        return hitCount;
    }


    // -------------------------------------------------- Public Static Methods


    public void reset() {
        hitCount = 0;
        accessCount = 0;
        synchronized (bcStats) {
            bcCache = null;
            bcCount = 0;
        }
        synchronized (ccStats) {
            ccCache = null;
            ccCount = 0;
        }
    }


    public static String toString(ByteChunk bc) {

        // If the cache is null, then either caching is disabled, or we're
        // still training
        if (bcCache == null) {
            String value = bc.toStringInternal();
            if (byteEnabled && (value.length() < maxStringSize)) {
                // If training, everything is synced
                synchronized (bcStats) {
                    // If the cache has been generated on a previous invocation
                    // while waiting for the lock, just return the toString
                    // value we just calculated
                    if (bcCache != null) {
                        return value;
                    }
                    // Two cases: either we just exceeded the train count, in
                    // which case the cache must be created, or we just update
                    // the count for the string
                    if (bcCount > trainThreshold) {

                        // Sort the entries according to occurrence
                        TreeMap<Integer, ArrayList<ByteEntry>> tempMap =
                                new TreeMap<>();
                        for (Entry<ByteEntry, int[]> item : bcStats.entrySet()) {
                            ByteEntry entry = item.getKey();
                            int[] countA = item.getValue();
                            Integer count = countA[0];
                            // Add to the list for that count
                            ArrayList<ByteEntry> list = tempMap.computeIfAbsent(count, k -> new ArrayList<>());
                            // Create list
                            list.add(entry);
                        }
                        // Allocate array of the right size
                        int size = bcStats.size();
                        if (size > cacheSize) {
                            size = cacheSize;
                        }
                        ByteEntry[] tempbcCache = new ByteEntry[size];
                        // Fill it up using an alphabetical order
                        // and a dumb insert sort
                        ByteChunk tempChunk = new ByteChunk();
                        int n = 0;
                        while (n < size) {
                            Object key = tempMap.lastKey();
                            ArrayList<ByteEntry> list = tempMap.get(key);
                            for (int i = 0; i < list.size() && n < size; i++) {
                                ByteEntry entry = list.get(i);
                                tempChunk.setBytes(entry.name, 0,
                                        entry.name.length);
                                int insertPos = findClosest(tempChunk,
                                        tempbcCache, n);
                                if (insertPos == n) {
                                    tempbcCache[n + 1] = entry;
                                } else {
                                    System.arraycopy(tempbcCache, insertPos + 1,
                                            tempbcCache, insertPos + 2,
                                            n - insertPos - 1);
                                    tempbcCache[insertPos + 1] = entry;
                                }
                                n++;
                            }
                            tempMap.remove(key);
                        }
                        bcCount = 0;
                        bcStats.clear();
                        bcCache = tempbcCache;
                    } else {
                        bcCount++;
                        // Allocate new ByteEntry for the lookup
                        ByteEntry entry = new ByteEntry();
                        entry.value = value;
                        int[] count = bcStats.get(entry);
                        if (count == null) {
                            int end = bc.getEnd();
                            int start = bc.getStart();
                            // Create byte array and copy bytes
                            entry.name = new byte[bc.getLength()];
                            System.arraycopy(bc.getBuffer(), start, entry.name,
                                    0, end - start);
                            // Set encoding
                            entry.charset = bc.getCharset();
                            // Initialize occurrence count to one
                            count = new int[1];
                            count[0] = 1;
                            // Set in the stats hash map
                            bcStats.put(entry, count);
                        } else {
                            count[0] = count[0] + 1;
                        }
                    }
                }
            }
            return value;
        } else {
            accessCount++;
            // Find the corresponding String
            String result = find(bc);
            if (result == null) {
                return bc.toStringInternal();
            }
            // Note: We don't care about safety for the stats
            hitCount++;
            return result;
        }

    }


    // ----------------------------------------------------- Protected Methods


    /**
     * Compare given byte chunk with byte array.
     * Return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    protected static int compare(ByteChunk name, byte[] compareTo) {
        int result = 0;

        byte[] b = name.getBuffer();
        int start = name.getStart();
        int end = name.getEnd();
        int len = compareTo.length;

        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (b[i + start] > compareTo[i]) {
                result = 1;
            } else if (b[i + start] < compareTo[i]) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length > (end - start)) {
                result = -1;
            } else if (compareTo.length < (end - start)) {
                result = 1;
            }
        }
        return result;
    }


    /**
     * Find an entry given its name in the cache and return the associated
     * String.
     */
    protected static String find(ByteChunk name) {
        int pos = findClosest(name, bcCache, bcCache.length);
        if ((pos < 0) || (compare(name, bcCache[pos].name) != 0)
                || !(name.getCharset().equals(bcCache[pos].charset))) {
            return null;
        } else {
            return bcCache[pos].value;
        }
    }


    /**
     * Find an entry given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    protected static int findClosest(ByteChunk name, ByteEntry[] array,
                                     int len) {

        int a = 0;
        int b = len - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }

        if (compare(name, array[0].name) < 0) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) >>> 1;
            int result = compare(name, array[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = compare(name, array[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }


    // -------------------------------------------------- ByteEntry Inner Class


    public static class ByteEntry {

        public byte[] name = null;
        public Charset charset = null;
        public String value = null;

        @Override
        public String toString() {
            return value;
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ByteEntry) {
                return value.equals(((ByteEntry) obj).value);
            }
            return false;
        }

    }


    // -------------------------------------------------- CharEntry Inner Class


    public static class CharEntry {

        public char[] name = null;
        public final String value = null;

        @Override
        public String toString() {
            return value;
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CharEntry) {
                return value.equals(((CharEntry) obj).value);
            }
            return false;
        }

    }


}
