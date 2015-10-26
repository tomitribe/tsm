/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.command.application;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodeSelectorTest {
    @Test
    public void noSelection() {
        final NodeSelector selector = new NodeSelector(-1, -1);
        assertTrue(selector.isSelected(0));
        assertTrue(selector.isSelected(1));
        assertTrue(selector.isSelected(2));
        assertTrue(selector.isSelected(4));
    }

    @Test
    public void index() {
        {
            final NodeSelector selector = new NodeSelector(0, -1);
            assertTrue(selector.isSelected(0));
            assertFalse(selector.isSelected(1));
            assertFalse(selector.isSelected(2));
        }
        {
            final NodeSelector selector = new NodeSelector(1, -1);
            assertFalse(selector.isSelected(0));
            assertTrue(selector.isSelected(1));
            assertFalse(selector.isSelected(2));
        }
        {
            final NodeSelector selector = new NodeSelector(2, -1);
            assertFalse(selector.isSelected(0));
            assertFalse(selector.isSelected(1));
            assertTrue(selector.isSelected(2));
        }
    }

    @Test
    public void factor1() {
        {
            final NodeSelector selector = new NodeSelector(0, 1);
            assertTrue(selector.isSelected(0));
            assertFalse(selector.isSelected(1));
            assertFalse(selector.isSelected(2));
        }
        {
            final NodeSelector selector = new NodeSelector(1, 1);
            assertFalse(selector.isSelected(0));
            assertTrue(selector.isSelected(1));
            assertFalse(selector.isSelected(2));
        }
        {
            final NodeSelector selector = new NodeSelector(2, 1);
            assertFalse(selector.isSelected(0));
            assertFalse(selector.isSelected(1));
            assertTrue(selector.isSelected(2));
        }
    }

    @Test
    public void factor2() {
        {
            final NodeSelector selector = new NodeSelector(0, 2);
            assertTrue(selector.isSelected(0));
            assertTrue(selector.isSelected(1));
            assertFalse(selector.isSelected(2));
        }
        {
            final NodeSelector selector = new NodeSelector(1, 2);
            assertFalse(selector.isSelected(0));
            assertFalse(selector.isSelected(1));
            assertTrue(selector.isSelected(2));
        }
        {
            final NodeSelector selector = new NodeSelector(2, 2);
            assertFalse(selector.isSelected(0));
            assertFalse(selector.isSelected(1));
            assertFalse(selector.isSelected(2));
        }
    }
}
