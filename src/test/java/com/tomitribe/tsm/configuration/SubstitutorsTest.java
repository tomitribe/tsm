/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.configuration;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class SubstitutorsTest {
    @Test
    public void filter() {
        assertEquals(
            "some text without placedholer",
            Substitutors.resolveWithVariables("some text ${with} placedholer", new HashMap<String, String>() {{ put("with", "without"); }}));
    }

    @Test
    public void keepUnknownPlaceholder() {
        assertEquals(
            "some text ${with} placedholer",
            Substitutors.resolveWithVariables("some text ${with} placedholer"));
    }

    @Test
    public void withDefaults() {
        assertEquals(
            "some text without placedholer",
            Substitutors.resolveWithVariables("some text ${with:-without} placedholer"));
    }
}
