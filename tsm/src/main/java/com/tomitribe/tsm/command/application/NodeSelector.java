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

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NodeSelector {
    private final int index;
    private final int byGroup;

    public boolean isSelected(final int index) {
        if (byGroup < 0) {
            return this.index < 0 || this.index == index;
        }

        if (this.index < 0) {
            throw new IllegalArgumentException("grouping needs index parameter");
        }

        return index >= this.index * byGroup && index < (this.index + 1) * byGroup;
    }
}
