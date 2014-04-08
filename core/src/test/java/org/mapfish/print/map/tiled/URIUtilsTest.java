/*
 * Copyright (C) 2014  Camptocamp
 *
 * This file is part of MapFish Print
 *
 * MapFish Print is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MapFish Print is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MapFish Print.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mapfish.print.map.tiled;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Jesse on 4/4/14.
 */
public class URIUtilsTest {

    public static final String TEST_QUERY = "a=1&a=2&b=b1&b=b2&c&d=1";

    @Test
    public void testGetParametersURI() throws Exception {
        URI uri = new URI("http://server:port/path1/path2?" + TEST_QUERY);
        final Multimap<String, String> parameters = URIUtils.getParameters(uri);

        assertEquals(6, parameters.size());
        assertTestParams(parameters);
    }


    @Test
    public void testGetParametersString() throws Exception {
        final Multimap<String, String> parameters = URIUtils.getParameters(TEST_QUERY);

        assertEquals(6, parameters.size());
        assertTestParams(parameters);
    }

    private void assertTestParams(Multimap<String, String> parameters) {
        assertTrue(parameters.containsEntry("a", "1"));
        assertTrue(parameters.containsEntry("a", "2"));
        assertTrue(parameters.containsEntry("b", "b1"));
        assertTrue(parameters.containsEntry("b", "b2"));
        assertTrue(parameters.containsEntry("c", ""));
        assertTrue(parameters.containsEntry("d", "1"));
    }

    @Test
    public void testAddParamsNoOverrides() throws Exception {
        URI uri = new URI("http://server:port/path1/path2?" + TEST_QUERY);
        Multimap<String, String> newParams = HashMultimap.create();
        newParams.put("a", "n1");
        newParams.put("e", "e1");
        Set<String> overrides = Sets.newHashSet();
        final URI updatedUrl1 = URIUtils.addParams(uri, newParams, overrides);

        final Multimap<String, String> updatedParams1 = URIUtils.getParameters(updatedUrl1);

        assertEquals(8, updatedParams1.size());

        assertTestParams(updatedParams1);
        assertTrue(updatedParams1.containsEntry("a", "n1"));
        assertTrue(updatedParams1.containsEntry("e", "e1"));
    }

    @Test
    public void testAddParamsWithOverrides() throws Exception {
        URI uri = new URI("http://server:port/path1/path2?" + TEST_QUERY);
        Multimap<String, String> newParams = HashMultimap.create();
        newParams.put("a", "n1");
        newParams.put("b", "nb1");
        newParams.put("e", "e1");
        Set<String> overrides = Sets.newHashSet();
        overrides.add("a");
        final URI updatedUrl1 = URIUtils.addParams(uri, newParams, overrides);

        final Multimap<String, String> updatedParams1 = URIUtils.getParameters(updatedUrl1);

        assertEquals(7, updatedParams1.size());

        assertTrue(updatedParams1.containsEntry("a", "n1"));
        assertTrue(updatedParams1.containsEntry("b", "b1"));
        assertTrue(updatedParams1.containsEntry("b", "nb1"));
        assertTrue(updatedParams1.containsEntry("b", "b2"));
        assertTrue(updatedParams1.containsEntry("c", ""));
        assertTrue(updatedParams1.containsEntry("d", "1"));
        assertTrue(updatedParams1.containsEntry("e", "e1"));
    }

    @Test
    public void testAddParamOverride() throws Exception {
        URI uri = new URI("http://server:port/path1/path2?" + TEST_QUERY);

        final Multimap<String, String> parameters = URIUtils.getParameters(uri);
        URIUtils.addParamOverride(parameters, "a", "n1");

        assertEquals(5, parameters.size());

        assertTrue(parameters.containsEntry("a", "n1"));
        assertTrue(parameters.containsEntry("b", "b1"));
        assertTrue(parameters.containsEntry("b", "b2"));
        assertTrue(parameters.containsEntry("c", ""));
        assertTrue(parameters.containsEntry("d", "1"));
    }

    @Test
    public void testSetParamDefault() throws Exception {
        URI uri = new URI("http://server:port/path1/path2?" + TEST_QUERY);

        final Multimap<String, String> parameters = URIUtils.getParameters(uri);
        URIUtils.setParamDefault(parameters, "a", "n1");

        assertEquals(6, parameters.size());
        assertTestParams(parameters);

        URIUtils.setParamDefault(parameters, "e", "n1");

        assertEquals(7, parameters.size());
        assertTestParams(parameters);
        assertTrue(parameters.containsEntry("e", "n1"));
    }

    @Test
    public void testSetQueryParams() throws Exception {
        URI initialUri = new URI("http://un:ps@server.com:9876/p1/p2?z=3,y=4#fragment");
        Multimap<String, String> params = LinkedListMultimap.create();
        params.put("a", "1");
        params.put("b", "2");
        params.put("b", "3");
        assertEquals("http://un:ps@server.com:9876/p1/p2?a=1&b=2&b=3#fragment", URIUtils.setQueryParams(initialUri, params).toString());

        initialUri = new URI("http", "un:ps", "server.com", 9876, "/p1/p2", "z=3&y=4", "fragment");
        assertEquals("http://un:ps@server.com:9876/p1/p2?a=1&b=2&b=3#fragment", URIUtils.setQueryParams(initialUri, params).toString());

        initialUri = new URI("http://center_wmts_fixedscale.com:1234/wmts");
        assertEquals("http://center_wmts_fixedscale.com:1234/wmts?a=1&b=2&b=3", URIUtils.setQueryParams(initialUri, params).toString());

        initialUri = new URI("http","center_wmts_fixedscale.com:1234", "/wmts", "a=3", "fragment");
        assertEquals("http://center_wmts_fixedscale.com:1234/wmts?a=1&b=2&b=3#fragment", URIUtils.setQueryParams(initialUri, params).toString());

        initialUri = new URI("http","center_wmts_fixedscale.com:1234", "/wmts", null, null);
        assertEquals("http://center_wmts_fixedscale.com:1234/wmts?a=1&b=2&b=3", URIUtils.setQueryParams(initialUri, params).toString());

        params = LinkedListMultimap.create();
        final String trickyKey = "a # param";
        final String trickyValue = "a value &time=1#trickFrag";
        params.put(trickyKey, trickyValue);
        initialUri = new URI("http://un:ps@server.com:9876/p1/p2?z=3,y=4#fragment");
        final URI updatedUri = URIUtils.setQueryParams(initialUri, params);
        assertFalse(updatedUri.toString().contains("a #"));
        assertFalse(updatedUri.toString().contains("1#t"));
        assertFalse(updatedUri.toString().contains(" &time"));
        assertFalse(updatedUri.toString().contains("#trick"));
        assertEquals("http", updatedUri.getScheme());
        assertEquals("fragment", updatedUri.getFragment());
        assertEquals("server.com", updatedUri.getHost());
        assertEquals("/p1/p2", updatedUri.getPath());
        assertEquals(9876, updatedUri.getPort());
    }
}
